"""Verify the signed APK contains a complete local Vosk runtime and English model."""
from pathlib import Path
from zipfile import ZipFile
import hashlib

apk = Path(__file__).resolve().parent.parent / "build/tcl-station-debug.apk"
with ZipFile(apk) as archive:
    entries = set(archive.namelist())
    required = {
        "lib/arm64-v8a/libvosk.so",
        "lib/arm64-v8a/libjnidispatch.so",
        "assets/model-en-us/uuid",
        "assets/THIRD_PARTY_NOTICES.md",
        "assets/model-en-us/am/final.mdl",
        "assets/model-en-us/conf/mfcc.conf",
        "assets/model-en-us/graph/HCLr.fst",
    }
    missing = required - entries
    assert not missing, f"APK lacks offline speech components: {sorted(missing)}"
    assert archive.read("assets/model-en-us/uuid").strip(), "Model marker is empty"
    assert len(archive.read("assets/model-en-us/am/final.mdl")) > 1_000_000, "Model weights incomplete"
    notice = archive.read("assets/THIRD_PARTY_NOTICES.md").decode("utf-8")
    for required_notice in ("OpenBLAS BSD 3-Clause", "CLAPACK license", "libffi permission notice", "THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS"):
        assert required_notice in notice, f"APK missing native dependency notice: {required_notice}"
    assert archive.read("assets/THIRD_PARTY_NOTICES.md") == (apk.parent.parent / "THIRD_PARTY_NOTICES.md").read_bytes(), "Packaged notices differ from public source"
    model_zip = apk.parent / "offline-cache/vosk-model-small-en-us-0.15.zip"
    assert hashlib.sha256(model_zip.read_bytes()).hexdigest() == "30f26242c4eb449f948e42cb302dd7a686cb29a3423a8367f99ff41780942498"
    with ZipFile(model_zip) as model:
        prefix = "vosk-model-small-en-us-0.15/"
        for item in model.infolist():
            if item.is_dir():
                continue
            packaged = "assets/model-en-us/" + item.filename.removeprefix(prefix)
            assert item.filename.startswith(prefix) and packaged in entries, f"Missing model asset: {item.filename}"
            assert archive.read(packaged) == model.read(item), f"Changed model asset: {item.filename}"
    dex_files = sorted(name for name in entries if name.startswith("classes") and name.endswith(".dex"))
    assert dex_files, "APK has no dex code"
    dex = b"".join(archive.read(name) for name in dex_files)
    for symbol in (b"Lorg/vosk/Model;", b"Lorg/vosk/Recognizer;", b"Lcom/sun/jna/Native;"):
        assert symbol in dex, f"APK lacks Java dependency: {symbol!r}"
    for obsolete in (b"RecorderService", b"MediaRecorder"):
        assert obsolete not in dex, f"APK contains old recording implementation: {obsolete!r}"
print("PASS APK includes offline Vosk, JNA and English model without recorder")
