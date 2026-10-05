"""Minimal RCON client + dev-server driver for the CobbleAscend live checks.

usage:
  rcon.py setup                      enable RCON on localhost with a random password (dev server only)
  rcon.py teardown                   disable RCON again
  rcon.py run <log> <cmd> [<cmd>..]  start the dev server, wait for 'Done (', send commands over RCON, then stop
"""
import re, secrets, socket, struct, subprocess, sys, time, pathlib, os

ROOT = pathlib.Path('L:/Codex/CobbleAscend')
PROPS = ROOT / 'ascensionlib/run/server.properties'
PWFILE = pathlib.Path(os.environ.get('TEMP', '.')) / 'ascend-rcon.pw'
JAVA_HOME = r'C:\Program Files\Eclipse Adoptium\jdk-21.0.12.1+1'


def set_props(values):
    text = PROPS.read_text(encoding='utf-8')
    for key, value in values.items():
        if re.search(rf'^{re.escape(key)}=.*$', text, re.M):
            text = re.sub(rf'^{re.escape(key)}=.*$', f'{key}={value}', text, flags=re.M)
        else:
            text += f'\n{key}={value}\n'
    PROPS.write_text(text, encoding='utf-8')


def rcon(port, password, commands):
    sock = socket.create_connection(('127.0.0.1', port), timeout=15)

    def send(req_id, kind, body):
        payload = struct.pack('<ii', req_id, kind) + body.encode() + b'\x00\x00'
        sock.sendall(struct.pack('<i', len(payload)) + payload)

    def recv():
        head = b''
        while len(head) < 4:
            head += sock.recv(4 - len(head))
        (length,) = struct.unpack('<i', head)
        data = b''
        while len(data) < length:
            data += sock.recv(length - len(data))
        req_id, kind = struct.unpack('<ii', data[:8])
        return req_id, kind, data[8:-2].decode(errors='replace')

    send(1, 3, password)
    if recv()[0] == -1:
        raise RuntimeError('RCON authentication failed')
    out = []
    for command in commands:
        send(2, 2, command)
        try:
            out.append((command, recv()[2]))
        except (ConnectionError, socket.timeout):
            out.append((command, '<connection closed>'))
            break
    return out


def main():
    mode = sys.argv[1]
    if mode == 'setup':
        password = secrets.token_hex(12)
        PWFILE.write_text(password)
        set_props({'enable-rcon': 'true', 'rcon.password': password, 'rcon.port': '25575', 'server-ip': '127.0.0.1'})
        print('RCON enabled on 127.0.0.1:25575')
    elif mode == 'teardown':
        set_props({'enable-rcon': 'false', 'rcon.password': '', 'server-ip': ''})
        PWFILE.unlink(missing_ok=True)
        print('RCON disabled')
    elif mode == 'run':
        log, commands = pathlib.Path(sys.argv[2]), sys.argv[3:]
        password = PWFILE.read_text()
        env = dict(os.environ, JAVA_HOME=JAVA_HOME)
        with open(log, 'wb') as out:
            proc = subprocess.Popen([str(ROOT / 'gradlew.bat'), ':ascensionlib:runServer', '--console=plain'], cwd=ROOT, env=env,
                                    stdin=subprocess.DEVNULL, stdout=out, stderr=subprocess.STDOUT)
            deadline = time.time() + 600
            done = False
            while time.time() < deadline and proc.poll() is None:
                if 'Done (' in log.read_text(errors='replace'):
                    done = True
                    break
                time.sleep(3)
            results = []
            if done:
                time.sleep(3)
                try:
                    results = rcon(25575, password, commands + ['stop'])
                except Exception as exc:  # noqa: BLE001
                    results = [('<rcon>', f'FAILED: {exc!r}')]
            try:
                proc.wait(timeout=120)
            except subprocess.TimeoutExpired:
                proc.kill()
                results.append(('<server>', 'did not stop within 120s; killed'))
        with open(log, 'a', encoding='utf-8') as out:
            out.write('\n=== RCON RESULTS ===\n')
            for command, reply in results:
                out.write(f'> {command}\n{reply}\n')
            out.write(f'=== server started: {done}, exit: {proc.returncode} ===\n')
        print('done' if done else 'server never reached Done')


main()
