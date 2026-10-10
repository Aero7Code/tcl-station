#!/usr/bin/env python3
"""Read Station's bounded event files and narrow Android exit evidence over ADB.

No microphone audio, recognized words, location, credentials, or unfiltered logcat.
Snapshots stay on this host under XDG_STATE_HOME/tcl-station/diagnostics (0700).
"""
import os
from pathlib import Path
import re
import subprocess
from datetime import datetime, timezone

ADB = os.environ.get('ADB', 'adb')
ROOT = '/sdcard/Android/data/com.aero.tclstation/files/diagnostics'
DEST = Path(os.environ.get('XDG_STATE_HOME', str(Path.home() / '.local/state'))) / 'tcl-station/diagnostics'
MAX_EVENT_BYTES = 32 * 1024
MAX_SYSTEM_BYTES = 2 * 1024 * 1024


def adb(*args, timeout=20):
    return subprocess.run([ADB, *args], capture_output=True, timeout=timeout)


def save(path, data):
    fd = os.open(path, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
    with os.fdopen(fd, 'wb') as out:
        out.write(data)


def safe_logcat():
    output = adb('logcat', '-d', '-v', 'threadtime', '-t', '10000', timeout=30)
    if output.returncode or len(output.stdout) > MAX_SYSTEM_BYTES: return b''
    clean = []
    for line in output.stdout.decode('utf-8', 'replace').splitlines():
        match = re.match(r'^\S+\s+\S+\s+\d+\s+\d+\s+[VDIWEF]\s+(\S+)\s*:\s*(.*)$', line)
        if not match: continue
        tag, message = match.groups()
        if tag == 'TCLStationDiag' and re.fullmatch(r'event=[A-Z_]+|diagnostic_file_unavailable', message):
            clean.append(line)
        elif tag == 'TextToSpeech' and re.fullmatch(r'stop failed: not bound to TTS engine|Sucessfully bound to com\.k2fsa\.sherpa\.onnx\.tts\.engine', message):
            clean.append(line)
        elif tag == 'TextToSpeechManagerPerUserService' and message in ('TTS engine binding error', 'java.util.concurrent.CompletionException: java.util.concurrent.TimeoutException'):
            clean.append(line)
        elif tag == 'ActivityManager' and re.search(r'Process com\.aero\.tclstation \(pid \d+\) has died:', message):
            clean.append(line)
    return ('\n'.join(clean) + '\n').encode() if clean else b''


def main():
    connected = adb('get-state', timeout=8)
    if connected.returncode or connected.stdout.strip() != b'device':
        raise SystemExit('Station tablet is not connected/authorized over ADB')
    DEST.mkdir(mode=0o700, parents=True, exist_ok=True)
    DEST.chmod(0o700)
    stamp = datetime.now(timezone.utc).strftime('%Y%m%dT%H%M%S%fZ')
    bundle = DEST / stamp
    bundle.mkdir(mode=0o700)
    found = False
    for filename in ('station-diagnostics.log.1', 'station-diagnostics.log'):
        result = adb('exec-out', 'cat', ROOT + '/' + filename)
        if result.returncode == 0 and 0 < len(result.stdout) <= MAX_EVENT_BYTES:
            # Only known diagnostic records; reject arbitrary external-file contents.
            lines = result.stdout.decode('utf-8', 'replace').splitlines()
            if all(re.fullmatch(r'\S+ pid=\d+ elapsed_ms=\d+ event=[A-Z_]+', row) for row in lines):
                save(bundle / filename, result.stdout)
                found = True
    exits = adb('shell', 'dumpsys', 'activity', 'exit-info', 'com.aero.tclstation')
    if exits.returncode == 0 and len(exits.stdout) <= MAX_SYSTEM_BYTES:
        save(bundle / 'android-exit-info.txt', exits.stdout)
    logs = safe_logcat()
    if logs: save(bundle / 'android-tts-and-station-events.txt', logs)
    bundles = sorted(p for p in DEST.iterdir() if p.is_dir())
    for old in bundles[:-10]:
        for entry in old.iterdir(): entry.unlink()
        old.rmdir()
    print(bundle)
    print('app_event_log_present=' + str(found))


if __name__ == '__main__':
    main()
