"""Optional on-device smoke test: Security panel exposes explicit controls."""
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


def tap_label(label):
    node = next((n for n in nodes() if n.get("text") == label), None)
    assert node is not None, f"Missing visible control: {label}"
    a = list(map(int, re.findall(r"\d+", node.get("bounds"))))
    run("shell", "input", "tap", str((a[0] + a[2]) // 2), str((a[1] + a[3]) // 2))
    time.sleep(0.7)


run("shell", "input", "keyevent", "3")
run("shell", "am", "start", "-S", "-n", "com.aero.tclstation/.MainActivity")
for _ in range(12):
    if any(n.get("text") == "TCL  /  STATION" for n in nodes()):
        break
    time.sleep(0.5)
else:
    raise AssertionError("Dashboard did not come to foreground")
run("shell", "input", "tap", "1199", "675")
time.sleep(1.2)
tap_label("Security")
labels = {n.get("text") for n in nodes()}
assert "Start local recording" in labels, "Missing explicit Start local recording button"
assert "Stop recording" in labels, "Missing explicit Stop recording button"
print("PASS Security panel shows explicit start/stop controls")
