"""Tablet smoke test: Station tap-to-talk launches Android speech UI and returns safely."""
import os
import re
import subprocess
import time
import xml.etree.ElementTree as ET

ADB = os.environ.get("ADB", os.path.join(os.environ["TMPDIR"], "tcl-adb/usr/bin/adb"))

def run(*args):
    return subprocess.run([ADB, *args], check=True, capture_output=True, text=True).stdout

def nodes():
    run("shell", "uiautomator", "dump", "/sdcard/voice-window.xml")
    doc = subprocess.run([ADB, "exec-out", "cat", "/sdcard/voice-window.xml"], check=True, capture_output=True).stdout
    return list(ET.fromstring(doc).iter("node"))

def tap(label):
    node = next((n for n in nodes() if n.get("text") == label), None)
    assert node is not None, f"Missing control {label!r}"
    bounds = list(map(int, re.findall(r"\d+", node.get("bounds"))))
    run("shell", "input", "tap", str((bounds[0] + bounds[2]) // 2), str((bounds[1] + bounds[3]) // 2))

run("shell", "am", "start", "-S", "-n", "com.aero.tclstation/.MainActivity")
time.sleep(1)
tap("Speak")
time.sleep(1)
window = run("shell", "dumpsys", "window")
assert "com.google.android.tts" in window or "com.google.android.googlequicksearchbox" in window, "Android speech UI was not launched"
run("shell", "input", "keyevent", "4")
time.sleep(.6)
assert any(n.get("text") == "Speak" for n in nodes()), "Station did not return from recognizer"
print("PASS tap-to-talk hands off to speech UI and returns to Station")
tap("Camera")
tap("Open live camera")
time.sleep(.5)
tap("Speak")
time.sleep(.6)
assert "com.google.android.tts" in run("shell", "dumpsys", "window"), "Camera Speak did not open speech UI"
camera = run("shell", "dumpsys", "media.camera")
assert "Client com.aero.tclstation" not in camera, "Camera must close when switching to speech UI"
run("shell", "input", "keyevent", "4")
print("PASS camera Speak launches recognizer and releases preview")
