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
system_label = "Voice: system (tap for offline model)"
model_label = "Voice: offline model (tap for system)"
had_system = any(n.get("text") == system_label for n in nodes())
if had_system:
    tap(system_label)
assert any(n.get("text") == model_label for n in nodes()), "Could not select model voice"
adb("shell", "pm", "grant", "com.aero.tclstation", "android.permission.RECORD_AUDIO")
if any(n.get("text") == "Hands-free: On" for n in nodes()):
    tap("Hands-free: On")
    time.sleep(.8)
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
    assert "text: Station voice test." in log, "Sherpa model engine did not synthesize the test reply"
    assert "engineSpeed: 1.08" in log, "Model speed request was not applied by Sherpa"
    print("PASS Sherpa model synthesized reply at 1.08x; microphone paused and resumed")
finally:
    if any(n.get("text") == "Hands-free: On" for n in nodes()):
        tap("Hands-free: On")
    if had_system:
        tap(model_label)
