"""独立管道实测：仅合成帧，不连接浏览器、账号或后端。"""
import json
import os
from pathlib import Path
import queue
import struct
import subprocess
import threading
import uuid

root = Path(__file__).resolve().parents[2]
pipe_name = 'aden-collector-test-' + uuid.uuid4().hex
shell = Path(os.environ['SystemRoot']) / 'System32/WindowsPowerShell/v1.0/powershell.exe'
proc = subprocess.Popen([str(shell), '-NoLogo', '-NoProfile', '-NonInteractive', '-ExecutionPolicy', 'Bypass', '-File', str(root / 'scripts/collector-pipe.ps1'), '-PipeName', pipe_name], stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.PIPE, creationflags=subprocess.CREATE_NO_WINDOW)
lines = queue.Queue()
def read_lines():
    for line in proc.stdout:
        lines.put(json.loads(line))
threading.Thread(target=read_lines, daemon=True).start()
try:
    ready = lines.get(timeout=15)
    assert ready['kind'] == 'ready' and ready['networkDenied'] is True
    with open('\\\\.\\pipe\\' + pipe_name, 'r+b', buffering=0) as pipe:
        assert lines.get(timeout=5)['kind'] == 'connected'
        for index in range(2):
            request = {'protocolVersion': 1, 'messageId': f'probe-{index}', 'type': 'hello', 'payload': {'text': '合成中文'}}
            data = json.dumps(request, ensure_ascii=False).encode('utf8')
            pipe.write(struct.pack('<I', len(data)) + data)
            event = lines.get(timeout=5)
            assert event == {'kind': 'message', 'message': request}, event
            response = {'protocolVersion': 1, 'messageId': request['messageId'], 'ok': True, 'data': {'text': '合成回执'}}
            proc.stdin.write(json.dumps(response, ensure_ascii=False).encode('utf8') + b'\n')
            proc.stdin.flush()
            length = struct.unpack('<I', pipe.read(4))[0]
            assert json.loads(pipe.read(length)) == response
    assert lines.get(timeout=5)['kind'] == 'disconnected'
    print('COLLECTOR_PIPE_PROBE_PASS: two UTF-8 frames over one current-user pipe')
finally:
    proc.kill()
    proc.wait(timeout=5)
