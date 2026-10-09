"""Gate: packaged prototype cannot record or expose a recording service."""
from pathlib import Path
from zipfile import ZipFile

root = Path(__file__).resolve().parent.parent
manifest = (root / "AndroidManifest.xml").read_text()
assert "RecorderService" not in manifest
assert "FOREGROUND_SERVICE_CAMERA" not in manifest
assert "RECORD_AUDIO" not in manifest
apk = root / "build/tcl-station-debug.apk"
with ZipFile(apk) as archive:
    dex = archive.read("classes.dex")
for symbol in (b"RecorderService", b"MediaRecorder", b"ACTION_START", b"ACTION_STOP"):
    assert symbol not in dex, f"Packaged APK contains retired recorder symbol: {symbol!r}"
print("PASS APK excludes recording implementation and service")
