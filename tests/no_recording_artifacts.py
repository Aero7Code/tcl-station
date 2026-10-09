"""Gate: packaged prototype cannot record or expose a recording service."""
from pathlib import Path
from zipfile import ZipFile

root = Path(__file__).resolve().parent.parent
manifest = (root / "AndroidManifest.xml").read_text()
assert "RecorderService" not in manifest
assert "FOREGROUND_SERVICE_CAMERA" not in manifest
# Hands-free capture needs RECORD_AUDIO, but it must not save audio/video.
assert "MediaRecorder" not in manifest
apk = root / "build/tcl-station-debug.apk"
with ZipFile(apk) as archive:
    dex = b"".join(archive.read(name) for name in archive.namelist()
                   if name.startswith("classes") and name.endswith(".dex"))
for symbol in (b"RecorderService", b"MediaRecorder", b"station-video-"):
    assert symbol not in dex, f"Packaged APK contains retired recorder symbol: {symbol!r}"
print("PASS APK excludes recording implementation and service")
