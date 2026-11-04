#!/usr/bin/env python3
"""Behavior tests for the remote Mac queue helper (no Mac connection)."""
import concurrent.futures
import json
import os
import pathlib
import subprocess
import tempfile
import time
import unittest

HELPER = pathlib.Path(__file__).with_name('mac-queue.py')


class QueueTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = self.temp.name

    def call(self, *args, identity='box', group='group', role='child', ok=True):
        result = subprocess.run(
            ['python3', '-I', str(HELPER), '--root', self.root,
             '--identity', identity, '--group', group, '--role', role, *args],
            text=True, capture_output=True, timeout=10)
        if ok:
            self.assertEqual(result.returncode, 0, result.stderr)
        else:
            self.assertNotEqual(result.returncode, 0, result.stdout)
        return result.stdout.strip()

    def register(self, **kwargs):
        return self.call('register', '--pid', str(os.getpid()), **kwargs)

    def control(self, *args, **kwargs):
        return self.call(*args, identity='group', role='orchestrator', **kwargs)

    def rows(self):
        return json.loads(self.call('list'))

    def test_registered_waiters_are_fifo(self):
        tickets = [self.register() for _ in range(3)]
        self.assertEqual(len(set(tickets)), 3)
        for ticket in tickets:
            self.assertEqual(self.call('head'), ticket)
            self.call('remove', ticket)
        self.assertEqual(self.rows(), [])
        self.assertEqual(self.call('head'), '')

    def test_concurrent_registration_does_not_lose_tickets_or_sequences(self):
        with concurrent.futures.ThreadPoolExecutor(max_workers=8) as pool:
            tickets = list(pool.map(lambda _: self.register(), range(24)))
        rows = self.rows()
        self.assertEqual({r['ticket'] for r in rows}, set(tickets))
        self.assertEqual([r['sequence'] for r in rows], list(range(1, 25)))
        for row in rows:
            self.call('remove', row['ticket'])
        self.register()
        self.assertEqual(self.rows()[0]['sequence'], 25)

    def test_dead_waiter_and_reused_pid_are_cleaned(self):
        process = subprocess.Popen(['sleep', '30'])
        def stop_process():
            if process.poll() is None:
                process.kill()
            process.wait(timeout=5)
        self.addCleanup(stop_process)
        dead = self.call('register', '--pid', str(process.pid))
        reused = self.call('register', '--pid', str(os.getpid()), '--pstart', 'old start')
        live = self.register()
        process.terminate()
        process.wait(timeout=5)
        self.assertEqual(self.call('head'), live)
        self.assertNotIn(dead, [r['ticket'] for r in self.rows()])
        self.assertNotIn(reused, [r['ticket'] for r in self.rows()])

    def test_priority_descending_and_fifo_within_priority(self):
        a, b, c = [self.register() for _ in range(3)]
        self.control('priority', b, '4')
        self.control('priority', c, '4')
        self.assertEqual([r['ticket'] for r in self.rows()], [b, c, a])
        self.assertEqual(self.call('head'), b)

    def test_children_and_other_groups_cannot_control(self):
        ticket = self.register()
        self.call('priority', ticket, '9', ok=False)
        self.call('reserve', '10', ok=False)
        self.call('priority', ticket, '9', identity='other', group='other',
                  role='orchestrator', ok=False)
        self.call('priority', ticket, '9', identity='imposter',
                  role='orchestrator', ok=False)
        self.call('remove', ticket, identity='other', ok=False)
        self.assertEqual(self.rows()[0]['priority'], 0)
        # Listing is deliberately visible across cooperative groups.
        self.assertEqual(len(json.loads(self.call('list', group='other'))), 1)

    def test_reservation_is_fifo_bounded_and_releasable_only_by_group_owner(self):
        first = self.register()
        reservation = self.control('reserve', '60')
        last = self.register()
        self.assertEqual([r['ticket'] for r in self.rows()], [first, reservation, last])
        self.call('remove', first)
        self.assertEqual(self.call('head'), reservation)
        self.call('release', reservation, ok=False)
        self.call('release', reservation, identity='other', group='other',
                  role='orchestrator', ok=False)
        self.call('remove', reservation, identity='group', ok=False)
        self.control('release', last, ok=False)
        for invalid in ['0', '-1', '3601', 'nan']:
            self.control('reserve', invalid, ok=False)
        self.control('release', reservation)
        self.assertEqual(self.call('head'), last)

    def test_reservation_expires_without_blocking_next_waiter(self):
        self.control('reserve', '1')
        ticket = self.register()
        time.sleep(1.1)
        self.assertEqual(self.call('head'), ticket)
        self.assertEqual(len(self.rows()), 1)

    def test_run_tokens_support_cancel_and_cancelled_head_fails_closed(self):
        self.call('register', '--pid', str(os.getpid()), '--ticket', 'run-token')
        self.call('register', '--pid', str(os.getpid()), '--ticket', 'run-token', ok=False)
        self.call('remove', 'run-token')
        self.call('remove', 'run-token')  # exit/cancel can race harmlessly
        self.call('head', '--ticket', 'run-token', ok=False)

    def test_corrupt_metadata_fails_closed(self):
        ticket = self.register()
        state = pathlib.Path(self.root, 'state.json')
        state.write_text('{broken')
        self.call('head', ok=False)
        self.call('register', '--pid', str(os.getpid()), ok=False)
        self.assertEqual(state.read_text(), '{broken')
        self.assertTrue(ticket)


if __name__ == '__main__':
    unittest.main()
