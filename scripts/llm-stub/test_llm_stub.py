"""Behavioral tests: real local sockets/processes, never a provider or an AVD."""
import contextlib
import http.client
import json
import os
from pathlib import Path
import select
import socket
import ssl
import subprocess
import sys
import tempfile
import time
import unittest


HERE = Path(__file__).resolve().parent
ANSWER = "AndBible emulator stub response."
ARGS = '{"title":"E3 test","nested":{"text":"quote: \\\""}}'


@contextlib.contextmanager
def running_stub(*args):
    process = subprocess.Popen(
        [sys.executable, "-I", str(HERE / "llm_stub.py"), "--port", "0", *args],
        stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True,
    )
    try:
        ready, _, _ = select.select([process.stdout], [], [], 5)
        line = process.stdout.readline() if ready else ""
        if not line.startswith("READY "):
            process.terminate()
            out, err = process.communicate(timeout=5)
            raise AssertionError(f"Stub did not start: {line}{out}{err}")
        yield int(line.split()[1]), process
    finally:
        process.terminate()
        process.communicate(timeout=5)


def request(port, path="/chat/completions", body=None):
    conn = http.client.HTTPConnection("127.0.0.1", port, timeout=5)
    try:
        if body is None:
            conn.request("GET", path)
        else:
            conn.request("POST", path, json.dumps(body), {"Content-Type": "application/json"})
        response = conn.getresponse()
        return response.status, response.getheader("Content-Type"), response.read()
    finally:
        conn.close()


def prompt(stream=False, messages=None):
    return {"model": "stub-model", "stream": stream,
            "messages": messages or [{"role": "user", "content": "Explain this verse"}]}


class StubTests(unittest.TestCase):
    def test_text_returns_one_assistant_and_logs_request_without_secrets(self):
        with running_stub("--insecure-http", "--mode", "text") as (port, proc):
            status, mime, raw = request(port, body=prompt())
            self.assertEqual(status, 200)
            self.assertIn("application/json", mime)
            result = json.loads(raw)
            self.assertEqual(len(result["choices"]), 1)
            self.assertEqual(result["choices"][0]["message"], {"role": "assistant", "content": ANSWER})
            self.assertEqual(result["choices"][0]["finish_reason"], "stop")
            self.assertTrue(select.select([proc.stdout], [], [], 2)[0])
            self.assertIn("POST /chat/completions 200", proc.stdout.readline())

    def test_models_with_and_without_v1_prefix(self):
        with running_stub("--insecure-http") as (port, _):
            for path in ("/models", "/v1/models"):
                status, _, raw = request(port, path)
                self.assertEqual(status, 200)
                self.assertEqual(json.loads(raw)["data"], [
                    {"id": "stub-model", "object": "model", "owned_by": "local-stub"}])

    def test_delay_before_response(self):
        with running_stub("--insecure-http", "--mode", "delay", "--delay-seconds", "0.2") as (port, _):
            start = time.monotonic()
            status, _, raw = request(port, body=prompt())
            self.assertGreaterEqual(time.monotonic() - start, 0.2)
            self.assertEqual(status, 200)
            self.assertEqual(json.loads(raw)["choices"][0]["message"]["content"], ANSWER)

    def test_toolcall_then_text_after_tool_result_avoids_agent_loop(self):
        with running_stub("--insecure-http", "--mode", "toolcall", "--tool", "createStudyPad",
                          "--tool-args", ARGS) as (port, _):
            status, _, raw = request(port, "/v1/chat/completions", prompt())
            self.assertEqual(status, 200)
            choice = json.loads(raw)["choices"][0]
            self.assertEqual(choice["finish_reason"], "tool_calls")
            call = choice["message"]["tool_calls"][0]
            self.assertEqual(call["type"], "function")
            self.assertEqual(call["function"], {"name": "createStudyPad", "arguments": ARGS})
            messages = [*prompt()["messages"], choice["message"],
                        {"role": "tool", "tool_call_id": call["id"], "content": "Created"}]
            _, _, raw = request(port, body=prompt(messages=messages))
            self.assertEqual(json.loads(raw)["choices"][0]["message"]["content"], ANSWER)
            # A later independent user turn should be able to request another tool.
            messages.append({"role": "user", "content": "Again"})
            _, _, raw = request(port, body=prompt(messages=messages))
            self.assertEqual(json.loads(raw)["choices"][0]["finish_reason"], "tool_calls")

    def stream_chunks(self, port, messages=None):
        status, mime, raw = request(port, "/v1/chat/completions", prompt(True, messages))
        self.assertEqual(status, 200)
        self.assertIn("text/event-stream", mime)
        frames = raw.decode().strip().split("\n\n")
        self.assertEqual(frames[-1], "data: [DONE]")
        chunks = [json.loads(frame.removeprefix("data: ")) for frame in frames[:-1]]
        self.assertTrue(all(c["object"] == "chat.completion.chunk" for c in chunks))
        self.assertEqual(len({c["id"] for c in chunks}), 1)
        self.assertEqual(chunks[0]["choices"][0]["delta"]["role"], "assistant")
        return [c["choices"][0] for c in chunks]

    def test_streamed_text_accumulates_and_finishes(self):
        with running_stub("--insecure-http") as (port, _):
            choices = self.stream_chunks(port)
            parts = [c["delta"]["content"] for c in choices if "content" in c["delta"]]
            self.assertGreater(len(parts), 1)
            self.assertEqual("".join(parts), ANSWER)
            self.assertEqual(choices[-1]["finish_reason"], "stop")

    def test_streamed_tool_arguments_accumulate_by_index_then_followup_text(self):
        with running_stub("--insecure-http", "--mode", "toolcall", "--tool", "createStudyPad",
                          "--tool-args", ARGS) as (port, _):
            choices = self.stream_chunks(port)
            calls = [tc for c in choices for tc in c["delta"].get("tool_calls", [])]
            self.assertGreater(len(calls), 1)
            self.assertEqual({c["index"] for c in calls}, {0})
            self.assertEqual(calls[0]["function"]["name"], "createStudyPad")
            self.assertEqual(calls[0]["type"], "function")
            self.assertTrue(calls[0]["id"])
            self.assertEqual("".join(c["function"].get("arguments", "") for c in calls), ARGS)
            self.assertEqual(choices[-1]["finish_reason"], "tool_calls")
            followup = self.stream_chunks(port, [{"role": "tool", "content": "Created"}])
            self.assertEqual("".join(c["delta"].get("content", "") for c in followup), ANSWER)

    def test_delay_also_applies_before_first_stream_event(self):
        with running_stub("--insecure-http", "--mode", "delay", "--delay-seconds", "0.2") as (port, _):
            conn = http.client.HTTPConnection("127.0.0.1", port, timeout=5)
            try:
                start = time.monotonic()
                conn.request("POST", "/chat/completions", json.dumps(prompt(True)))
                response = conn.getresponse()
                self.assertTrue(response.readline().startswith(b"data: "))
                self.assertGreaterEqual(time.monotonic() - start, 0.2)
                response.read()
            finally:
                conn.close()

    def test_unknown_endpoint_and_invalid_body_are_rejected(self):
        with running_stub("--insecure-http") as (port, _):
            self.assertEqual(request(port, "/no-such-endpoint")[0], 404)
            self.assertEqual(request(port, body=[])[0], 400)
            conn = http.client.HTTPConnection("127.0.0.1", port, timeout=5)
            try:
                conn.request("POST", "/chat/completions", "not json")
                self.assertEqual(conn.getresponse().status, 400)
            finally:
                conn.close()

    def test_invalid_cli_arguments_fail_fast(self):
        for args in (("--delay-seconds", "-1"), ("--delay-seconds", "nan"),
                     ("--mode", "toolcall"),
                     ("--mode", "toolcall", "--tool", "createStudyPad", "--tool-args", "[]")):
            with self.subTest(args=args):
                result = subprocess.run([sys.executable, "-I", str(HERE / "llm_stub.py"), *args],
                                        capture_output=True, text=True, timeout=5)
                self.assertEqual(result.returncode, 2)
                self.assertIn("error:", result.stderr)


class TlsTests(unittest.TestCase):
    def test_generated_ca_verifies_server_and_real_https_response(self):
        with tempfile.TemporaryDirectory() as tmp:
            certs = Path(tmp) / "certs"
            result = subprocess.run(["bash", str(HERE / "make-ca.sh"), str(certs)],
                                    capture_output=True, text=True, timeout=15)
            self.assertEqual(result.returncode, 0, result.stderr)
            verify = subprocess.run(["openssl", "verify", "-CAfile", str(certs / "ca.crt"),
                                     "-verify_ip", "10.0.2.2", "-purpose", "sslserver",
                                     str(certs / "server.crt")], capture_output=True, text=True, timeout=5)
            self.assertEqual(verify.returncode, 0, verify.stderr)
            self.assertEqual((certs / "server.key").stat().st_mode & 0o077, 0)
            self.assertEqual((certs / "ca.key").stat().st_mode & 0o077, 0)
            # Regenerating would silently invalidate the CA installed on the AVD.
            again = subprocess.run(["bash", str(HERE / "make-ca.sh"), str(certs)],
                                   capture_output=True, text=True, timeout=5)
            self.assertNotEqual(again.returncode, 0)
            context = ssl.create_default_context(cafile=str(certs / "ca.crt"))
            with running_stub("--cert", str(certs / "server.crt"), "--key", str(certs / "server.key")) as (port, _):
                with socket.create_connection(("127.0.0.1", port), timeout=5) as sock:
                    with context.wrap_socket(sock, server_hostname="10.0.2.2") as tls:
                        tls.sendall(b"GET /models HTTP/1.0\r\nHost: 10.0.2.2\r\n\r\n")
                        response = http.client.HTTPResponse(tls)
                        response.begin()
                        self.assertEqual(response.status, 200)
                        self.assertEqual(json.loads(response.read())["data"][0]["id"], "stub-model")


class InstallerSafetyTests(unittest.TestCase):
    def run_installer(self, serial, hardware="ranchu", avd_name="private-e3", expected_name="private-e3"):
        with tempfile.TemporaryDirectory() as tmp:
            folder = Path(tmp)
            log = folder / "calls.jsonl"
            # Only the adb process boundary is substituted; never touch a real device.
            adb = folder / "adb"
            adb.write_text("#!/usr/bin/python3 -I\n" + '''import json, os, sys
with open(os.environ["CALL_LOG"], "a") as f:
    f.write(json.dumps([os.environ.get("ANDROID_ADB_SERVER_PORT"), sys.argv[1:]]) + "\\n")
a = sys.argv[1:]
if a[-2:] == ["getprop", "ro.kernel.qemu"]: print("1")
elif a[-2:] == ["getprop", "ro.hardware"]: print(os.environ["FAKE_HARDWARE"])
elif a[-2:] == ["getprop", "ro.boot.qemu.avd_name"]: print(os.environ["FAKE_AVD"])
elif a[-2:] == ["getprop", "ro.build.version.sdk"]: print("36")
elif a[-2:] == ["id", "-u"]: print("0")
elif a[-2:] == ["pidof", "zygote64"]: print("111")
elif a[-2:] == ["pidof", "zygote"]: print("112")
''')
            adb.chmod(0o755)
            # openssl reads a real freshly generated CA, not a canned hash.
            certs = folder / "certs"
            subprocess.run(["bash", str(HERE / "make-ca.sh"), str(certs)],
                           capture_output=True, check=True, timeout=15)
            env = {**os.environ, "PATH": str(folder) + ":" + os.environ["PATH"],
                   "CALL_LOG": str(log), "FAKE_HARDWARE": hardware, "FAKE_AVD": avd_name,
                   "ANDROID_ADB_SERVER_PORT": "5037", "ADB_SERVER_SOCKET": "tcp:host:5037"}
            result = subprocess.run(["bash", str(HERE / "install-ca-on-avd.sh"), serial,
                                     expected_name, str(certs / "ca.crt")],
                                    capture_output=True, text=True, env=env, timeout=15)
            calls = [json.loads(line) for line in log.read_text().splitlines()] if log.exists() else []
            return result, calls

    def test_refuses_non_emulator_and_wrong_avd_before_root(self):
        for serial, hardware, actual in (("phone-123", "ranchu", "private-e3"),
                                         ("emulator-5554", "physical", "private-e3"),
                                         ("emulator-5554", "ranchu", "other-avd")):
            with self.subTest(serial=serial, hardware=hardware, actual=actual):
                result, calls = self.run_installer(serial, hardware, actual)
                self.assertNotEqual(result.returncode, 0)
                self.assertFalse(any(c[1][-1:] in (["root"], ["remount"]) for c in calls))

    def test_routes_all_writes_to_private_adb_and_mounts_apex_for_both_zygotes(self):
        result, calls = self.run_installer("emulator-5554")
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertTrue(calls)
        self.assertTrue(all(port == "5038" and args[:2] == ["-s", "emulator-5554"]
                            for port, args in calls))
        commands = "\n".join(" ".join(args) for _, args in calls)
        self.assertIn("/system/etc/security/cacerts/", commands)
        self.assertIn("/apex/com.android.conscrypt/cacerts", commands)
        self.assertIn("nsenter -t 111 -m", commands)
        self.assertIn("nsenter -t 112 -m", commands)
        self.assertNotIn("setenforce", commands)


if __name__ == "__main__":
    unittest.main()
