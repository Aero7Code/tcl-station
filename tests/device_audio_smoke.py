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
print("PASS Studio audio test button is visible after expansion")
