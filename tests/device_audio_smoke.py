"""On-device smoke test for the Studio panel's audio test control."""
import os
import re
import subprocess
import time
import xml.etree.ElementTree as ET

ADB = os.environ.get("ADB", os.path.join(os.environ["TMPDIR"], "tcl-adb/usr/bin/adb"))


def adb(*args):
    return subprocess.run([ADB, *args], check=True, capture_output=True, text=True).stdout


def nodes():
    adb("shell", "uiautomator", "dump", "/sdcard/window.xml")
    xml = subprocess.run([ADB, "exec-out", "cat", "/sdcard/window.xml"], check=True, capture_output=True).stdout
    return list(ET.fromstring(xml).iter("node"))


def tap_node(text):
    match = next((n for n in nodes() if n.get("text") == text), None)
    assert match is not None, f"Missing visible control: {text}"
    x1, y1, x2, y2 = map(int, re.findall(r"\d+", match.get("bounds")))
    adb("shell", "input", "tap", str((x1 + x2) // 2), str((y1 + y2) // 2))
    time.sleep(0.6)


adb("shell", "am", "force-stop", "com.aero.tclstation")
adb("shell", "am", "start", "-n", "com.aero.tclstation/.MainActivity")
time.sleep(1.5)
tap_node("›")
# Panel tap is by visible title; its parent receives the click.
tap_node("Studio / Audio")
assert any(n.get("text") == "Test audio (short beep)" for n in nodes()), "Missing Test audio button"
assert any(n.get("text") == "Test spoken reply" for n in nodes()), "Missing TTS test button"
labels = {n.get("text") for n in nodes()}
assert "Voice: alternate (tap for original)" in labels or "Voice: original (tap for alternate)" in labels
original = "Voice: alternate (tap for original)" if "Voice: alternate (tap for original)" in labels else "Voice: original (tap for alternate)"
other = "Voice: original (tap for alternate)" if original.startswith("Voice: alternate") else "Voice: alternate (tap for original)"
tap_node(original)
assert any(n.get("text") == other for n in nodes()), "TTS voice switch did not update"
adb("shell", "am", "force-stop", "com.aero.tclstation")
adb("shell", "am", "start", "-n", "com.aero.tclstation/.MainActivity")
time.sleep(1)
tap_node("›")
tap_node("Studio / Audio")
assert any(n.get("text") == other for n in nodes()), "Voice choice was not persisted"
tap_node(other)
assert any(n.get("text") == original for n in nodes()), "Could not restore starting voice"
tap_node("Test spoken reply")
assert "ListeningService" not in adb("shell", "dumpsys", "activity", "services", "com.aero.tclstation"), "Audio test unexpectedly started hands-free"
print("PASS Studio speech-test and voice toggle visible, persistent, reversible; listener remains off")
