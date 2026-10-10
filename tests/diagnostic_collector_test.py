"""Collector keeps only vetted Station events and bounded private snapshots."""
import contextlib
from io import StringIO
import importlib.util
import os
from pathlib import Path
import stat
import subprocess
import tempfile
from unittest.mock import patch

module_path = Path(__file__).resolve().parents[1] / 'scripts/collect_diagnostics.py'
spec = importlib.util.spec_from_file_location('collect_diagnostics', module_path)
collector = importlib.util.module_from_spec(spec)
spec.loader.exec_module(collector)

logcat = b'''10-09 22:00:00.100  123  123 I TCLStationDiag: event=VOICE_BIND_REQUEST
10-09 22:00:00.101  123  123 I TCLStationDiag: event=VOICE_FINISHED recognized=private phrase
10-09 22:00:00.102  123  123 I OtherApp: password=unrelated private data
10-09 22:00:00.103  123  123 W TextToSpeechManagerPerUserService: TTS engine binding error
'''

def fake_adb(*args, timeout=20):
    if args[0] == 'get-state': return subprocess.CompletedProcess(args, 0, b'device\n', b'')
    if args[0] == 'logcat': return subprocess.CompletedProcess(args, 0, logcat, b'')
    if args[:3] == ('shell', 'dumpsys', 'activity'):
        return subprocess.CompletedProcess(args, 0, b'package: com.aero.tclstation\n', b'')
    if args[0] == 'exec-out':
        if args[-1].endswith('.log'):
            return subprocess.CompletedProcess(args, 0, b'2026-10-10T00:00:00Z pid=123 elapsed_ms=10 event=VOICE_BIND_REQUEST\n', b'')
        return subprocess.CompletedProcess(args, 1, b'', b'no file')
    raise AssertionError(args)

with tempfile.TemporaryDirectory(prefix='station-diagnostics-test-') as temp:
    collector.DEST = Path(temp) / 'private'
    for index in range(11):
        old = collector.DEST / f'20260101T0000{index:02d}000000Z'
        old.mkdir(parents=True)
        (old / 'sample').write_text('old')
    with patch.object(collector, 'adb', fake_adb), contextlib.redirect_stdout(StringIO()):
        collector.main()
    bundles = sorted(p for p in collector.DEST.iterdir() if p.is_dir())
    assert len(bundles) == 10
    assert bundles[0].name == '20260101T000002000000Z'
    newest = bundles[-1]
    assert stat.S_IMODE(collector.DEST.stat().st_mode) == 0o700
    assert stat.S_IMODE(newest.stat().st_mode) == 0o700
    assert stat.S_IMODE((newest / 'station-diagnostics.log').stat().st_mode) == 0o600
    filtered = (newest / 'android-tts-and-station-events.txt').read_text()
    assert 'event=VOICE_BIND_REQUEST' in filtered and 'TTS engine binding error' in filtered
    assert 'private phrase' not in filtered and 'unrelated private data' not in filtered
    assert (newest / 'station-diagnostics.log').read_text().endswith('event=VOICE_BIND_REQUEST\n')
    def oversized_adb(*args, timeout=20):
        if args[0] == 'exec-out' and args[-1].endswith('.log'):
            return subprocess.CompletedProcess(args, 0, b'A' * (collector.MAX_EVENT_BYTES + 1), b'')
        return fake_adb(*args, timeout=timeout)
    with patch.object(collector, 'adb', oversized_adb), contextlib.redirect_stdout(StringIO()):
        collector.main()
    latest = max(p for p in collector.DEST.iterdir() if p.is_dir())
    assert not (latest / 'station-diagnostics.log').exists()
print('PASS collector filtering, permissions, and ten-snapshot retention')
