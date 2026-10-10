#!/usr/bin/env python3
"""Minimal TLS-terminating TCP proxy (stdlib only), e.g. to reach `php -S` Nextcloud over HTTPS.

  tls-proxy.py --listen 0.0.0.0:8444 --target 127.0.0.1:8080 \
      --cert .local/webdav-test/cert.pem --key .local/webdav-test/key.pem
"""
import argparse
import asyncio
import ssl


def hostport(s):
    host, _, port = s.rpartition(":")
    return host or "0.0.0.0", int(port)


async def pipe(reader, writer):
    """Copy one direction; half-close the peer when this side hits EOF."""
    try:
        while data := await reader.read(65536):
            writer.write(data)
            await writer.drain()
        if writer.can_write_eof():
            writer.write_eof()
    except OSError:
        pass


async def close(writer):
    try:
        writer.close()
        await writer.wait_closed()
    except OSError:
        pass


async def main():
    p = argparse.ArgumentParser()
    p.add_argument("--listen", required=True)
    p.add_argument("--target", required=True)
    p.add_argument("--cert", required=True)
    p.add_argument("--key", required=True)
    a = p.parse_args()
    th, tp = hostport(a.target)
    lh, lp = hostport(a.listen)
    ctx = ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER)
    ctx.load_cert_chain(a.cert, a.key)

    async def handle(cr, cw):
        try:
            tr, tw = await asyncio.open_connection(th, tp)
        except OSError:
            await close(cw)
            return
        try:
            await asyncio.gather(pipe(cr, tw), pipe(tr, cw))
        finally:
            await asyncio.gather(close(cw), close(tw))

    server = await asyncio.start_server(handle, lh, lp, ssl=ctx)
    print(f"TLS proxy {lh}:{lp} -> {th}:{tp}", flush=True)
    async with server:
        await server.serve_forever()


if __name__ == "__main__":
    try:
        asyncio.run(main())
    except KeyboardInterrupt:
        pass
