"""Device smoke test for discoverable, reversible Home selection controls."""
import os
import re
import subprocess
import time
import xml.etree.ElementTree as ET

ADB = os.environ.get('ADB', os.path.join(os.environ['TMPDIR'], 'tcl-adb/usr/bin/adb'))

def run(*args):
    return subprocess.run([ADB, *args], check=True, capture_output=True, text=True).stdout

def nodes():
    run('shell', 'uiautomator', 'dump', '/sdcard/home-window.xml')
    data = subprocess.run([ADB, 'exec-out', 'cat', '/sdcard/home-window.xml'], check=True, capture_output=True).stdout
    return list(ET.fromstring(data).iter('node'))

def tap(label):
    node = next((n for n in nodes() if n.get('text') == label), None)
    assert node is not None, f'Missing {label!r}'
    a, b, c, d = map(int, re.findall(r'\d+', node.get('bounds')))
    run('shell', 'input', 'tap', str((a+c)//2), str((b+d)//2))

if 'com.google.android.permissioncontroller' in run('shell', 'dumpsys', 'window'):
    run('shell', 'input', 'keyevent', '4')
run('shell', 'am', 'start', '-S', '-n', 'com.aero.tclstation/.MainActivity')
time.sleep(.6)
tap('›')
tap('›')
assert any(n.get('text') == 'Make Station Home' for n in nodes()), 'Home role control missing on setup page'
tap('Home app settings')
time.sleep(.6)
window = run('shell', 'dumpsys', 'window')
assert ('com.android.settings' in window or 'com.google.android.permissioncontroller' in window), 'Android Home-app settings did not open'
options = {n.get('text') for n in nodes()}
assert {'Launcher', 'TCL Station'} <= options, 'Home settings must offer both Station and the previous launcher'
run('shell', 'input', 'keyevent', '4')
print('PASS launcher selection and rollback settings are reachable on tablet')
