"""Device smoke: local push-to-talk owns tablet microphone; no external recognition UI."""
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

def tap_speak_active(allow_finished=False):
    entries = nodes()
    active = next((n for n in entries if n.get("text") in {"Speak: listening", "Speak: hearing", "Speak: loading"}), None)
    if active is None and allow_finished and any(n.get("text") == "Speak" for n in entries):
        assert not station_mic_active(), "Speak timed out but microphone remained active"
        return
    assert active is not None, f"Speak ended before cancellation: {[n.get('text') for n in entries]}"
    bounds = list(map(int, re.findall(r"\d+", active.get("bounds"))))
    run("shell", "input", "tap", str((bounds[0] + bounds[2]) // 2), str((bounds[1] + bounds[3]) // 2))

def wait_speak_state(states, seconds=25):
    deadline = time.monotonic() + seconds
    seen = set()
    while time.monotonic() < deadline:
        labels = {n.get("text") for n in nodes()}
        seen.update(x for x in labels if x and x.startswith("Speak"))
        found = labels.intersection(states)
        if found: return found.pop()
        time.sleep(.3)
    raise AssertionError(f"Speak did not reach {states}; seen {seen}; last labels: {labels}")

def station_mic_active():
    current = run('shell','dumpsys','audio').split('Audio event log: recording activity received')[0]
    return bool(re.search(r'active\? true\s*\n[^\n]*pack:com\.aero\.tclstation', current))

run("shell", "am", "start", "-S", "-n", "com.aero.tclstation/.MainActivity")
run("shell", "pm", "grant", "com.aero.tclstation", "android.permission.RECORD_AUDIO")
tap("Speak")
state = wait_speak_state({"Speak: listening", "Speak: hearing"})
assert 'com.aero.tclstation' in run("shell", "dumpsys", "window"), "Station lost focus to a speech app"
assert station_mic_active(), "Push-to-talk did not capture the tablet microphone"
tap_speak_active(allow_finished=True)
assert wait_speak_state({"Speak"}, 10) == "Speak"
assert not station_mic_active(), "Push-to-talk microphone persisted after cancellation or timeout"
print("PASS Speak captures locally, stays in Station, and releases its microphone")
time.sleep(1)  # Allow Android AudioRecord teardown before opening a new one.
tap("Speak")
wait_speak_state({"Speak: listening", "Speak: hearing"})
run("shell", "am", "start", "-a", "android.settings.SETTINGS")  # Leave Station even if Station is the selected Home app.
time.sleep(1)
assert 'com.aero.tclstation' not in run("shell", "dumpsys", "window").split('mCurrentFocus=')[-1].splitlines()[0], "Station did not leave the foreground"
assert not station_mic_active(), "Speak kept recording after Station left the foreground"
run("shell", "am", "start", "-n", "com.aero.tclstation/.MainActivity")
assert wait_speak_state({"Speak"}, 10) == "Speak"
print("PASS local Speak stops when Station leaves the foreground")

tap("Speak")
wait_speak_state({"Speak: listening", "Speak: hearing"})
time.sleep(11)
assert wait_speak_state({"Speak"}, 10) == "Speak"
assert not station_mic_active(), "Speak did not release microphone after timeout"
print("PASS local Speak releases microphone after a one-shot timeout")

tap("Camera")
tap("Open live camera")
time.sleep(.6)
tap("Speak")
state = wait_speak_state({"Speak: listening", "Speak: hearing"})
assert station_mic_active(), "Camera Speak did not start local microphone"
camera = run("shell", "dumpsys", "media.camera")
assert "Client com.aero.tclstation" not in camera, "Camera must close when switching to Speak"
tap_speak_active(allow_finished=True)
assert wait_speak_state({"Speak"}, 10) == "Speak"
print("PASS camera Speak uses local recognizer and releases the preview")
