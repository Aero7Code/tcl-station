"""Device fault injection: missing offline engine must never fall back to system TTS."""
import os
import re
import subprocess
import time
import xml.etree.ElementTree as ET

ADB = os.environ.get("ADB", os.path.join(os.environ["TMPDIR"], "tcl-adb/usr/bin/adb"))
ENGINE = "com.k2fsa.sherpa.onnx.tts.engine"
def adb(*args):
    return subprocess.run([ADB, *args], check=True, capture_output=True, text=True).stdout

def nodes():
    adb("shell", "uiautomator", "dump", "/sdcard/tts-guard.xml")
    xml = subprocess.run([ADB, "exec-out", "cat", "/sdcard/tts-guard.xml"], check=True, capture_output=True).stdout
    return list(ET.fromstring(xml).iter("node"))

def tap(label):
    entry = next((n for n in nodes() if n.get("text") == label), None)
    assert entry is not None, f"Missing {label}"
    x1, y1, x2, y2 = map(int, re.findall(r"\d+", entry.get("bounds")))
    adb("shell", "input", "tap", str((x1+x2)//2), str((y1+y2)//2))

def studio():
    adb("shell", "am", "start", "-S", "-n", "com.aero.tclstation/.MainActivity")
    tap("›")
    tap("Studio / Audio")

studio()
if any(n.get("text") == "Voice: system (tap for offline model)" for n in nodes()):
    tap("Voice: system (tap for offline model)")
assert any(n.get("text") == "Voice: offline model (tap for system)" for n in nodes())
try:
    adb("shell", "am", "force-stop", "com.aero.tclstation")
    adb("shell", "pm", "disable-user", "--user", "0", ENGINE)
    studio()
    adb("logcat", "-c")
    tap("Test spoken reply")
    time.sleep(2)
    log = adb("logcat", "-d")
    assert "text: Station voice test." not in log, "Missing model engine still synthesized a reply"
    assert "Rejected non-model TTS engine" in log, "Unverified fallback engine was not explicitly rejected"
    print("PASS missing model engine blocks a default-system TTS fallback")
finally:
    adb("shell", "pm", "enable", ENGINE)
    assert ENGINE not in adb("shell", "pm", "list", "packages", "-d"), "Model engine remained disabled"
