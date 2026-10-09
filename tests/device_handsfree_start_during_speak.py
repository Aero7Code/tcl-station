"""Device regression: starting hands-free during Speak never opens a second recorder."""
import os
import re
import subprocess
import time
import xml.etree.ElementTree as ET

ADB = os.environ.get("ADB", os.path.join(os.environ["TMPDIR"], "tcl-adb/usr/bin/adb"))
def adb(*args):
    return subprocess.run([ADB, *args], check=True, capture_output=True, text=True).stdout

def nodes():
    adb("shell", "uiautomator", "dump", "/sdcard/station-race.xml")
    xml = subprocess.run([ADB, "exec-out", "cat", "/sdcard/station-race.xml"], check=True, capture_output=True).stdout
    return list(ET.fromstring(xml).iter("node"))

def center(label, entries):
    entry = next((n for n in entries if n.get("text") == label), None)
    assert entry is not None, f"Missing {label}"
    x1, y1, x2, y2 = map(int, re.findall(r"\d+", entry.get("bounds")))
    return str((x1+x2)//2), str((y1+y2)//2)

def tap(xy): adb("shell", "input", "tap", *xy)

def mic_count():
    current = adb("shell", "dumpsys", "audio").split("Audio event log: recording activity received")[0]
    return len(re.findall(r"active\? true\s*\n[^\n]*pack:com\.aero\.tclstation", current))

adb("shell", "am", "start", "-S", "-n", "com.aero.tclstation/.MainActivity")
adb("shell", "pm", "grant", "com.aero.tclstation", "android.permission.RECORD_AUDIO")
entries = nodes()
speak_xy = center("Speak", entries)
hands_xy = center("Hands-free: Off", entries)
tap(speak_xy)
tap(hands_xy)
try:
    deadline = time.monotonic() + 8
    while time.monotonic() < deadline:
        labels = {n.get("text") for n in nodes()}
        if "Hands-free: Loading" in labels and ({"Speak: listening", "Speak: hearing"} & labels):
            break
    else:
        raise AssertionError(f"Could not observe both active Speak and paused hands-free: {labels}")
    assert "ListeningService" in adb("shell", "dumpsys", "activity", "services", "com.aero.tclstation")
    assert mic_count() == 1, "More than one Station microphone was active during Speak"
    print("PASS late-started hands-free stays paused while local Speak owns the microphone")
    tap(speak_xy)
    deadline = time.monotonic() + 18
    while time.monotonic() < deadline:
        if mic_count() == 1 and "Hands-free: On" in {n.get("text") for n in nodes()}:
            break
    else:
        raise AssertionError("Hands-free did not take over after Speak cancellation")
    print("PASS hands-free resumes after Speak cancellation")
finally:
    labels = {n.get("text") for n in nodes()}
    if "Hands-free: On" in labels or "Hands-free: Loading" in labels:
        try: tap(center("Hands-free: On" if "Hands-free: On" in labels else "Hands-free: Loading", nodes()))
        except (AssertionError, subprocess.CalledProcessError): pass
