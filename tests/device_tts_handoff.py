"""Target-tablet check: spoken reply pauses the opt-in microphone and later resumes it.

Uses Android audio/recording diagnostics, not a substitute for listening to the speaker.
"""
import os
import re
import subprocess
import time
import xml.etree.ElementTree as ET

ADB = os.environ.get("ADB", os.path.join(os.environ["TMPDIR"], "tcl-adb/usr/bin/adb"))


def adb(*args):
    return subprocess.run([ADB, *args], check=True, capture_output=True, text=True).stdout


def nodes():
    adb("shell", "uiautomator", "dump", "/sdcard/tts-handoff.xml")
    xml = subprocess.run([ADB, "exec-out", "cat", "/sdcard/tts-handoff.xml"], check=True, capture_output=True).stdout
    return list(ET.fromstring(xml).iter("node"))


def tap(label):
    node = next((n for n in nodes() if n.get("text") == label), None)
    assert node is not None, f"Missing {label}"
    x1, y1, x2, y2 = map(int, re.findall(r"\d+", node.get("bounds")))
    adb("shell", "input", "tap", str((x1 + x2) // 2), str((y1 + y2) // 2))


def mic_active():
    current = adb("shell", "dumpsys", "audio").split("Audio event log: recording activity received")[0]
    return bool(re.search(r"active\? true\s*\n[^\n]*pack:com\.aero\.tclstation", current))


adb("shell", "am", "start", "-S", "-n", "com.aero.tclstation/.MainActivity")
tap("›")
tap("Studio / Audio")
had_original = any(n.get("text") == "Voice: original (tap for alternate)" for n in nodes())
if had_original:
    tap("Voice: original (tap for alternate)")
assert any(n.get("text") == "Voice: alternate (tap for original)" for n in nodes()), "Could not select alternate voice"
adb("shell", "pm", "grant", "com.aero.tclstation", "android.permission.RECORD_AUDIO")
tap("Hands-free: Off")
try:
    deadline = time.monotonic() + 90
    while time.monotonic() < deadline and not mic_active():
        time.sleep(0.5)
    assert mic_active(), "Hands-free microphone did not start"
    adb("logcat", "-c")
    tap("Test spoken reply")
    deadline = time.monotonic() + 12
    while time.monotonic() < deadline and mic_active():
        time.sleep(0.15)
    assert not mic_active(), "TTS did not pause microphone capture"
    deadline = time.monotonic() + 20
    while time.monotonic() < deadline and not mic_active():
        time.sleep(0.3)
    assert mic_active(), "Microphone did not resume after TTS"
    log = adb("logcat", "-d")
    assert "Synthesis request for locale eng-USA and name en-us-x-sfg-local" in log, "Alternate TTS request was not seen on this Google TTS tablet"
    print("PASS alternate local voice requested; TTS pauses hands-free microphone and capture resumes")
finally:
    if any(n.get("text") == "Hands-free: On" for n in nodes()):
        tap("Hands-free: On")
    if had_original:
        tap("Voice: alternate (tap for original)")
