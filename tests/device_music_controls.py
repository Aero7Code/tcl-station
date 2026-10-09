"""Music panel smoke: controls visible and no-player Pause is safe."""
import os
import re
import subprocess
import xml.etree.ElementTree as ET

ADB = os.environ.get('ADB', os.path.join(os.environ['TMPDIR'], 'tcl-adb/usr/bin/adb'))
def run(*args):
    return subprocess.run([ADB, *args], check=True, capture_output=True, text=True).stdout

def nodes():
    run('shell', 'uiautomator', 'dump', '/sdcard/station-music-test.xml')
    xml = subprocess.run([ADB, 'exec-out', 'cat', '/sdcard/station-music-test.xml'], check=True, capture_output=True).stdout
    return list(ET.fromstring(xml).iter('node'))

def tap(label):
    n = next((n for n in nodes() if n.get('text') == label), None)
    assert n is not None, f'Missing {label}'
    x1, y1, x2, y2 = map(int, re.findall(r'\d+', n.get('bounds')))
    run('shell', 'input', 'tap', str((x1+x2)//2), str((y1+y2)//2))

run('shell', 'am', 'start', '-S', '-n', 'com.aero.tclstation/.MainActivity')
tap('›')
tap('Music')
labels = {n.get('text') for n in nodes()}
assert {'Open YouTube Music', 'Play', 'Pause', 'Next'} <= labels, labels
print('PASS Music card exposes Play/Pause/Next alongside Open YouTube Music')
tap('Pause')
assert 'com.aero.tclstation' in run('shell', 'dumpsys', 'window')
print('PASS Pause with no player leaves Station open')
