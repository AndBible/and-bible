#!/usr/bin/env python3
"""Cooperative Mac admission queue; the legacy .build-lock is the execution gate.

Metadata is outside the synced tree. All clients share an SSH account, so group
checks prevent accidental interference, not hostile impersonation. Reservations
hold a FIFO place until release or expiry (at most one hour from creation).
"""
import argparse
import fcntl
import errno
import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import time
import uuid


MAX_RESERVATION_SECONDS = 3600


def process_start(pid):
    """Use the same locale/timezone-stable ps identity as the legacy lock."""
    result = subprocess.run(
        ['ps', '-p', str(pid), '-o', 'lstart='], capture_output=True, text=True,
        env={**os.environ, 'LC_ALL': 'C', 'TZ': 'UTC0'}, timeout=5)
    return ' '.join(result.stdout.split())


def alive(row):
    # A zombie retains its PID/start and passes kill(0), but cannot make progress.
    status = subprocess.run(
        ['ps', '-p', str(row['pid']), '-o', 'stat='], capture_output=True,
        text=True, env={**os.environ, 'LC_ALL': 'C'}, timeout=5).stdout.strip()
    if status.startswith('Z'):
        return False
    current = process_start(row['pid'])
    if current:
        return current == row['pstart']
    try:
        os.kill(row['pid'], 0)
    except ProcessLookupError:
        return False
    except PermissionError:
        pass
    # A blind ps must not evict a possibly live waiter.
    return True


def load_state(path):
    if not path.exists():
        return {'next_sequence': 1, 'tickets': []}
    state = json.loads(path.read_text())
    if (not isinstance(state, dict) or
            type(state.get('next_sequence')) is not int or
            state['next_sequence'] < 1 or not isinstance(state.get('tickets'), list)):
        raise ValueError('invalid queue state')
    seen = set()
    sequences = set()
    for row in state['tickets']:
        if (not isinstance(row, dict) or
                any(not isinstance(row.get(k), str) or not row[k]
                    for k in ('ticket', 'identity', 'group', 'kind')) or
                row['kind'] not in ('waiter', 'reservation') or
                type(row.get('sequence')) is not int or
                not 0 < row['sequence'] < state['next_sequence'] or
                type(row.get('priority')) is not int or
                not isinstance(row.get('arrival'), (int, float)) or
                row['ticket'] in seen or row['sequence'] in sequences):
            raise ValueError('invalid queue ticket')
        if row['kind'] == 'waiter':
            if (type(row.get('pid')) is not int or row['pid'] <= 0 or
                    not isinstance(row.get('pstart'), str) or not row['pstart']):
                raise ValueError('invalid waiter process identity')
        elif not isinstance(row.get('expires'), (int, float)):
            raise ValueError('invalid reservation expiry')
        seen.add(row['ticket'])
        sequences.add(row['sequence'])
    return state


def save_state(path, state):
    """Replace only after a complete write while holding the stable guard inode."""
    fd, temporary = tempfile.mkstemp(prefix='.state-', dir=path.parent)
    try:
        with os.fdopen(fd, 'w') as output:
            json.dump(state, output, allow_nan=False)
            output.write('\n')
            output.flush()
            os.fsync(output.fileno())
        os.replace(temporary, path)
    finally:
        if os.path.exists(temporary):
            os.unlink(temporary)


def operate(args, state):
    now = time.time()
    rows = state['tickets']
    rows[:] = [r for r in rows if
               (r['expires'] > now if r['kind'] == 'reservation' else alive(r))]
    command = args.command
    if command in ('priority', 'reserve', 'release'):
        if args.role != 'orchestrator' or args.identity != args.group:
            raise ValueError('queue control requires the group orchestrator')

    if command in ('register', 'reserve'):
        if command == 'reserve' and not 1 <= args.seconds <= MAX_RESERVATION_SECONDS:
            raise ValueError('reservation must be between 1 and 3600 seconds')
        ticket = args.ticket if command == 'register' and args.ticket else uuid.uuid4().hex
        if any(r['ticket'] == ticket for r in rows):
            raise ValueError('ticket already registered')
        row = dict(ticket=ticket, identity=args.identity, group=args.group,
                   arrival=now, sequence=state['next_sequence'], priority=0,
                   kind='waiter' if command == 'register' else 'reservation', expires=None)
        if command == 'register':
            if args.pid <= 0:
                raise ValueError('pid must be positive')
            start = args.pstart or process_start(args.pid)
            if not start:
                raise ValueError('cannot identify waiter process start')
            row.update(pid=args.pid, pstart=start)
        else:
            row['expires'] = now + args.seconds
        state['next_sequence'] += 1
        rows.append(row)
        return ticket

    row = next((r for r in rows if r['ticket'] == getattr(args, 'ticket', None)), None)
    if command in ('remove', 'priority', 'release'):
        if row is None:
            if command == 'remove':
                return ''  # cancel and EXIT removal are deliberately idempotent
            raise ValueError('ticket not found')
        if command == 'remove':
            if (row['identity'] != args.identity or row['group'] != args.group or
                    row['kind'] != 'waiter'):
                raise ValueError('can only remove own waiter')
        elif row['group'] != args.group:
            raise ValueError('cannot control another group')
        if command == 'release' and row['kind'] != 'reservation':
            raise ValueError('release requires a reservation')
        if command == 'priority':
            row['priority'] = args.priority
        else:
            rows.remove(row)
        return ''

    ordered = sorted(rows, key=lambda r: (-r['priority'], r['sequence']))
    if command == 'list':
        return json.dumps(ordered)
    if args.ticket and (row is None or row['identity'] != args.identity or
                        row['group'] != args.group or row['kind'] != 'waiter'):
        raise ValueError('waiter ticket missing or not owned by caller')
    if command == 'admit':
        if not ordered or ordered[0]['ticket'] != args.ticket:
            return 'busy'
        # Final eligibility, the exact legacy rename(2) gate, and ticket
        # consumption share the metadata guard with remove/priority/release.
        # Old clients still compete using the same nonempty directory lock.
        if not (args.pending / 'owner').is_file():
            raise ValueError('pending legacy lock has no owner')
        try:
            os.rename(args.pending, args.lock)
        except OSError as error:
            if error.errno in (errno.EEXIST, errno.ENOTEMPTY):
                return 'busy'
            raise
        rows.remove(row)
        return 'acquired'
    return ordered[0]['ticket'] if ordered else ''


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--root', required=True, type=Path)
    parser.add_argument('--identity', required=True)
    parser.add_argument('--group', required=True)
    parser.add_argument('--role', required=True, choices=['child', 'orchestrator'])
    commands = parser.add_subparsers(dest='command', required=True)
    register = commands.add_parser('register')
    register.add_argument('--pid', type=int, required=True)
    register.add_argument('--pstart')
    register.add_argument('--ticket')
    head = commands.add_parser('head')
    head.add_argument('--ticket')
    commands.add_parser('list')
    admit = commands.add_parser('admit')
    admit.add_argument('ticket')
    admit.add_argument('pending', type=Path)
    admit.add_argument('lock', type=Path)
    for command in ('remove', 'priority', 'release'):
        sub = commands.add_parser(command)
        sub.add_argument('ticket')
        if command == 'priority':
            sub.add_argument('priority', type=int)
    commands.add_parser('reserve').add_argument('seconds', type=int)
    args = parser.parse_args()
    try:
        if not args.identity or not args.group:
            raise ValueError('identity and group must not be empty')
        args.root.mkdir(parents=True, exist_ok=True)
        with (args.root / 'guard').open('a') as guard:
            fcntl.flock(guard, fcntl.LOCK_EX)
            path = args.root / 'state.json'
            state = load_state(path)
            result = operate(args, state)
            save_state(path, state)
        if result:
            print(result)
    except (OSError, ValueError, TypeError, subprocess.SubprocessError) as error:
        print('mac-queue: ' + str(error), file=sys.stderr)
        return 1
    return 0


if __name__ == '__main__':
    sys.exit(main())
