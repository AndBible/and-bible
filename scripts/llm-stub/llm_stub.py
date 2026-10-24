#!/usr/bin/env python3
"""Local OpenAI chat-completions fixture; no upstream requests or credentials."""
import argparse
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
import json
import math
from pathlib import Path
import re
import ssl
import time


ANSWER = "AndBible emulator stub response."
MODEL = "stub-model"


# `--mode studypad`: the BM-2 scenario (createStudyPad, then finishWithStudyPad with the new labelId).
STUDYPAD_CHAIN = [
    {"tool": "createStudyPad", "args": {"name": "Stub StudyPad", "items": [
        {"type": "text", "text": "Created by the LLM stub."}]}},
    {"tool": "finishWithStudyPad", "args": {"labelId": "{labelId}", "message": "Opened by the LLM stub."}},
]


def parse_chain(text):
    """Validate a --chain JSON list of {"tool": NAME, "args": {...}} steps."""
    chain = json.loads(text)
    if not isinstance(chain, list) or not chain:
        raise ValueError("expected a non-empty JSON list")
    for step in chain:
        if not isinstance(step, dict) or not isinstance(step.get("tool"), str) or not step["tool"]:
            raise ValueError('every step needs a "tool" name')
        if not isinstance(step.get("args", {}), dict):
            raise ValueError('"args" must be a JSON object')
    return chain


def find_key(value, key):
    """First string/number value stored under `key` anywhere in parsed JSON, else None."""
    if isinstance(value, dict):
        if isinstance(value.get(key), (str, int, float)) and not isinstance(value.get(key), bool):
            return str(value[key])
        value = list(value.values())
    if isinstance(value, list):
        for item in value:
            found = find_key(item, key)
            if found is not None:
                return found
    return None


def extract_value(text, key):
    """Value of `key` in a tool result: JSON parse, then regexes; None if absent."""
    for start in [0, *([text.index("{")] if "{" in text else [])]:
        try:
            parsed, _ = json.JSONDecoder().raw_decode(text[start:])
        except ValueError:
            continue
        found = find_key(parsed, key)
        if found is not None:
            return found
    name = re.escape(key)
    for pattern in (rf'"{name}"\s*:\s*"([^"]+)"', rf'{name}[=:]\s*(\S+)'):
        match = re.search(pattern, text)
        if match:
            return match.group(1).rstrip(',;)"\'')
    return None


def fill(value, result_text):
    if isinstance(value, str):
        def sub(match):
            found = extract_value(result_text, match.group(1))
            return match.group(0) if found is None else found
        return re.sub(r"\{(\w+)\}", sub, value)
    if isinstance(value, list):
        return [fill(item, result_text) for item in value]
    if isinstance(value, dict):
        return {key: fill(item, result_text) for key, item in value.items()}
    return value


def message_text(message):
    content = message.get("content")
    if isinstance(content, list):
        return "".join(part.get("text", "") for part in content if isinstance(part, dict))
    return content if isinstance(content, str) else ""


class StubHandler(BaseHTTPRequestHandler):
    def log_message(self, format, *args):
        # Never log headers, prompt bodies or API keys.
        pass

    def send_body(self, status, body, mime="application/json"):
        self.send_response(status)
        self.send_header("Content-Type", mime)
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)
        print(f"{self.command} {self.path} {status}", flush=True)

    def send_json(self, status, value):
        self.send_body(status, json.dumps(value).encode())

    def route(self):
        path = self.path.split("?", 1)[0]
        return path[3:] if path.startswith("/v1/") else path

    def do_GET(self):
        if self.route() != "/models":
            self.send_json(404, {"error": {"message": "Unknown stub endpoint"}})
            return
        self.send_json(200, {"object": "list", "data": [
            {"id": MODEL, "object": "model", "owned_by": "local-stub"}]})

    def do_POST(self):
        if self.route() != "/chat/completions":
            self.send_json(404, {"error": {"message": "Unknown stub endpoint"}})
            return
        try:
            length = int(self.headers.get("Content-Length", "0"))
            if not 0 < length <= 4 * 1024 * 1024:
                raise ValueError("Expected a JSON body of at most 4 MiB")
            body = json.loads(self.rfile.read(length))
            if not isinstance(body, dict) or not isinstance(body.get("messages"), list):
                raise ValueError("Expected a messages array")
            if not all(isinstance(message, dict) for message in body["messages"]):
                raise ValueError("Expected message objects")
        except (ValueError, UnicodeDecodeError) as error:
            self.send_json(400, {"error": {"message": str(error)}})
            return
        options = self.server.options
        if options.mode == "delay":
            time.sleep(options.delay_seconds)
        # Respond to tool results with text, rather than triggering an endless agent loop.
        last_role = next((m.get("role") for m in reversed(body["messages"])
                          if m.get("role") in ("user", "tool")), None)
        tool = options.mode == "toolcall" and last_role != "tool"
        call = {"id": "call_stub_1", "type": "function", "function": {
            "name": options.tool, "arguments": options.tool_args}}
        if options.chain is not None:
            # Stateless: tool results after the last user message say which step is next.
            after_user = []
            for m in reversed(body["messages"]):
                if m.get("role") == "user":
                    break
                if m.get("role") == "tool":
                    after_user.append(m)
            step = len(after_user)
            tool = step < len(options.chain)
            if tool:
                previous = message_text(after_user[0]) if after_user else ""
                spec = options.chain[step]
                call = {"id": f"call_stub_{step + 1}", "type": "function", "function": {
                    "name": spec["tool"],
                    "arguments": json.dumps(fill(spec.get("args", {}), previous))}}
        message = {"role": "assistant", "content": None if tool else ANSWER}
        if tool:
            message["tool_calls"] = [call]
        reason = "tool_calls" if tool else "stop"
        envelope = {"id": "chatcmpl-stub", "created": int(time.time()),
                    "model": body.get("model", MODEL)}
        try:
            if body.get("stream") is True:
                self.send_stream(envelope, tool, call, reason)
            else:
                self.send_json(200, {**envelope, "object": "chat.completion", "choices": [
                    {"index": 0, "message": message, "finish_reason": reason}],
                    "usage": {"prompt_tokens": 1, "completion_tokens": 1, "total_tokens": 2}})
        except (BrokenPipeError, ConnectionResetError):
            print(f"{self.command} {self.path} disconnected", flush=True)

    def send_stream(self, envelope, tool, call, reason):
        self.send_response(200)
        self.send_header("Content-Type", "text/event-stream")
        self.send_header("Cache-Control", "no-cache")
        self.send_header("Connection", "close")
        self.end_headers()
        self.close_connection = True

        def chunk(delta, finish=None):
            payload = {**envelope, "object": "chat.completion.chunk", "choices": [
                {"index": 0, "delta": delta, "finish_reason": finish}]}
            self.wfile.write(f"data: {json.dumps(payload)}\n\n".encode())
            self.wfile.flush()

        chunk({"role": "assistant"})
        if tool:
            chunk({"tool_calls": [{"index": 0, **call,
                                   "function": {"name": call["function"]["name"], "arguments": ""}}]})
            text = call["function"]["arguments"]
            # Deliberately split JSON tokens: consumers must accumulate by tool index.
            for offset in range(0, len(text), 8):
                chunk({"tool_calls": [{"index": 0, "function": {"arguments": text[offset:offset + 8]}}]})
        else:
            for offset in range(0, len(ANSWER), 8):
                chunk({"content": ANSWER[offset:offset + 8]})
        chunk({}, reason)
        self.wfile.write(b"data: [DONE]\n\n")
        self.wfile.flush()
        print(f"{self.command} {self.path} 200 stream", flush=True)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    cert_dir = Path(__file__).resolve().parents[2] / ".local/llm-stub/certs"
    parser.add_argument("--host", default="0.0.0.0")
    parser.add_argument("--port", type=int, default=8123)
    parser.add_argument("--mode", choices=("text", "delay", "toolcall", "toolchain", "studypad"), default="text")
    parser.add_argument("--delay-seconds", type=float, default=25)
    parser.add_argument("--tool")
    parser.add_argument("--tool-args", default="{}")
    parser.add_argument("--chain", help="JSON list of {tool, args} steps for --mode toolchain")
    parser.add_argument("--cert", type=Path, default=cert_dir / "server.crt")
    parser.add_argument("--key", type=Path, default=cert_dir / "server.key")
    parser.add_argument("--insecure-http", action="store_true", help="Plain HTTP for local tests only")
    options = parser.parse_args()
    if not math.isfinite(options.delay_seconds) or options.delay_seconds < 0:
        parser.error("--delay-seconds must be finite and non-negative")
    if options.mode == "toolcall" and not options.tool:
        parser.error("--mode toolcall requires --tool NAME")
    chain_text, options.chain = options.chain, None
    if options.mode == "studypad":
        options.chain = STUDYPAD_CHAIN
    elif options.mode == "toolchain":
        try:
            options.chain = parse_chain(chain_text or "")
        except ValueError as error:
            parser.error(f"invalid --chain: {error}")
    try:
        if not isinstance(json.loads(options.tool_args), dict):
            raise ValueError("expected a JSON object")
    except ValueError as error:
        parser.error(f"invalid --tool-args: {error}")
    context = None
    if not options.insecure_http:
        context = ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER)
        try:
            context.load_cert_chain(options.cert, options.key)
        except (OSError, ssl.SSLError) as error:
            parser.error(f"TLS certificate unavailable; run make-ca.sh first: {error}")
    with ThreadingHTTPServer((options.host, options.port), StubHandler) as server:
        server.options = options
        if context:
            server.socket = context.wrap_socket(server.socket, server_side=True)
        print(f"READY {server.server_port} {'https' if context else 'http'} mode={options.mode}", flush=True)
        try:
            server.serve_forever()
        except KeyboardInterrupt:
            pass


if __name__ == "__main__":
    main()
