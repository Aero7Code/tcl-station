"""Verify an opted-in listener resumes on visible relaunch and Stop clears that choice."""
import os
import re
import subprocess
import time
import xml.etree.ElementTree as ET

ADB = os.environ.get('ADB', 'adb')
def run(*args):
    return subprocess.run([ADB, *args], check=True, capture_output=True, text=True).stdout

def nodes():
    run('shell', 'uiautomator', 'dump', '/sdcard/station-persistence.xml')
    data = subprocess.run([ADB, 'exec-out', 'cat', '/sdcard/station-persistence.xml'], check=True, capture_output=True).stdout
    return list(ET.fromstring(data).iter('node'))

def labels(): return {n.get('text') for n in nodes()}
def wait_for(text, seconds=30):
    end = time.monotonic() + seconds
    while time.monotonic() < end:
        if text in labels(): return
        time.sleep(.4)
    raise AssertionError(f'Never reached {text}')

def tap(text):
    entry = next(n for n in nodes() if n.get('text') == text)
    x1, y1, x2, y2 = map(int, re.findall(r'\d+', entry.get('bounds')))
    run('shell', 'input', 'tap', str((x1+x2)//2), str((y1+y2)//2))

run('shell', 'am', 'start', '-S', '-n', 'com.aero.tclstation/.MainActivity')
if 'Hands-free: Off' not in labels():
    wait_for('Hands-free: On')
    tap('Hands-free: On')
    wait_for('Hands-free: Off')
tap('Hands-free: Off')
wait_for('Hands-free: On', 90)
run('shell', 'am', 'force-stop', 'com.aero.tclstation')
run('shell', 'am', 'start', '-n', 'com.aero.tclstation/.MainActivity')
wait_for('Hands-free: On', 90)
assert 'ListeningService' in run('shell','dumpsys','activity','services','com.aero.tclstation')
print('PASS opted-in listener returns when Station becomes visible again')
tap('Hands-free: On')
wait_for('Hands-free: Off')
run('shell', 'am', 'force-stop', 'com.aero.tclstation')
run('shell', 'am', 'start', '-n', 'com.aero.tclstation/.MainActivity')
wait_for('Hands-free: Off')
assert 'ListeningService' not in run('shell','dumpsys','activity','services','com.aero.tclstation')
print('PASS explicit Stop remains off across app relaunch')
# User requested hands-free remain on. Finish with their explicitly requested opt-in enabled.
tap('Hands-free: Off')
wait_for('Hands-free: On', 90)
print('PASS opted-in listener left on at end of test')
