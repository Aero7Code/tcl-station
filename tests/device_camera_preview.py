"""Device smoke test: visible-only camera preview with no recording."""
import os
import re
import subprocess
import time
import xml.etree.ElementTree as ET

ADB = os.environ.get("ADB", os.path.join(os.environ["TMPDIR"], "tcl-adb/usr/bin/adb"))


def run(*args):
    return subprocess.run([ADB, *args], check=True, capture_output=True, text=True).stdout


def nodes():
    run("shell", "uiautomator", "dump", "/sdcard/window.xml")
    doc = subprocess.run([ADB, "exec-out", "cat", "/sdcard/window.xml"], check=True, capture_output=True).stdout
    return list(ET.fromstring(doc).iter("node"))


def tap(label):
    n = next((node for node in nodes() if node.get("text") == label), None)
    assert n is not None, f"Missing {label}"
    box = list(map(int, re.findall(r"\d+", n.get("bounds"))))
    run("shell", "input", "tap", str((box[0] + box[2]) // 2), str((box[1] + box[3]) // 2))


run("shell", "input", "keyevent", "3")
run("shell", "am", "start", "-S", "-n", "com.aero.tclstation/.MainActivity")
time.sleep(1.5)
tap("Camera")
assert any(n.get("text") == "Open live camera" for n in nodes()), "Live preview control missing"
print("PASS camera panel offers a live-only preview")

run("shell", "pm", "grant", "com.aero.tclstation", "android.permission.CAMERA")
tap("Open live camera")
try:
    for _ in range(12):
        labels = {n.get("text") for n in nodes() if n.get("text")}
        if "LIVE · back camera · nothing saved" in labels:
            break
        time.sleep(.5)
    else:
        raise AssertionError("Preview never became live; UI: " + repr(labels))
    camera_state = run("shell", "dumpsys", "media.camera")
    section = camera_state.split("Active Camera Clients:", 1)[1].split("Allowed user IDs:", 1)[0]
    assert "com.aero.tclstation" in section, "No active camera client during preview"
    print("PASS in-app live camera session active")
    assert "SWITCH CAMERA" in labels, "Cannot choose the camera facing the room"
    tap("SWITCH CAMERA")
    for _ in range(12):
        labels = {n.get("text") for n in nodes() if n.get("text")}
        if "LIVE · front camera · nothing saved" in labels:
            break
        time.sleep(.5)
    else:
        raise AssertionError("Front-camera preview never became live: " + repr(labels))
    print("PASS switched to live front camera without recording")
    for facing in ("back", "front", "back", "front"):
        tap("SWITCH CAMERA")
        for _ in range(12):
            labels = {n.get("text") for n in nodes() if n.get("text")}
            if f"LIVE · {facing} camera · nothing saved" in labels:
                break
            time.sleep(.5)
        else:
            raise AssertionError(f"Repeated switch to {facing} failed: {labels!r}")
    print("PASS repeated camera switches release and reopen cleanly")
finally:
    labels = {n.get("text") for n in nodes() if n.get("text")}
    if "CLOSE CAMERA" in labels:
        tap("CLOSE CAMERA")
    else:
        run("shell", "input", "keyevent", "3")
for _ in range(8):
    section = run("shell", "dumpsys", "media.camera").split("Active Camera Clients:", 1)[1].split("Allowed user IDs:", 1)[0]
    if "com.aero.tclstation" not in section:
        print("PASS camera released after closing preview")
        break
    time.sleep(.5)
else:
    raise AssertionError("Camera remained open after preview closed")

tap("Open live camera")
for _ in range(12):
    labels = {n.get("text") for n in nodes() if n.get("text")}
    if "LIVE · back camera · nothing saved" in labels:
        break
    time.sleep(.5)
else:
    raise AssertionError("Preview did not reopen")
run("shell", "input", "keyevent", "3")
for _ in range(8):
    section = run("shell", "dumpsys", "media.camera").split("Active Camera Clients:", 1)[1].split("Allowed user IDs:", 1)[0]
    if "com.aero.tclstation" not in section:
        print("PASS camera released on Home/background")
        break
    time.sleep(.5)
else:
    raise AssertionError("Camera remained open after app entered background")
run("shell", "am", "start", "-n", "com.aero.tclstation/.MainActivity")
