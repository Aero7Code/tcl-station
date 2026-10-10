"""Toggle the tablet's real app notification setting, then restore it.
A hidden Stop action must not accompany continuous microphone capture.
"""
import os
import re
import subprocess
import time
import xml.etree.ElementTree as ET

ADB = os.environ.get('ADB', 'adb')
PKG = 'com.aero.tclstation'
def run(*args):
    return subprocess.run([ADB, *args], check=True, capture_output=True, text=True).stdout

def nodes():
    run('shell', 'uiautomator', 'dump', '/sdcard/station-guard-ui.xml')
    xml = subprocess.run([ADB, 'exec-out', 'cat', '/sdcard/station-guard-ui.xml'], check=True, capture_output=True).stdout
    return list(ET.fromstring(xml).iter('node'))

def wait_for(label, seconds=60):
    end = time.monotonic() + seconds
    while time.monotonic() < end:
        if any(n.get('text') == label for n in nodes()): return
        time.sleep(.5)
    raise AssertionError('Missing ' + label)

def tap_node(node):
    x1,y1,x2,y2 = map(int, re.findall(r'\d+', node.get('bounds')))
    run('shell','input','tap',str((x1+x2)//2),str((y1+y2)//2))

def tap(label): tap_node(next(n for n in nodes() if n.get('text') == label))

def settings_switch():
    current = nodes()
    label = next(n for n in current if n.get('text') == 'All TCL Station notifications')
    y1, y2 = list(map(int, re.findall(r'\d+', label.get('bounds'))))[1::2]
    switches = [n for n in current if n.get('class') == 'android.widget.Switch']
    return min(switches, key=lambda n: abs((list(map(int,re.findall(r'\d+',n.get('bounds'))))[1] + list(map(int,re.findall(r'\d+',n.get('bounds'))))[3])//2 - (y1+y2)//2))

def settings():
    run('shell','am','start','-a','android.settings.APP_NOTIFICATION_SETTINGS',
        '--es','android.provider.extra.APP_PACKAGE',PKG)
    wait_for('All TCL Station notifications')

def service_running(): return 'ListeningService' in run('shell','dumpsys','activity','services',PKG)

run('shell','am','start','-S','-n',PKG+'/.MainActivity')
settle_by = time.monotonic() + 90
while time.monotonic() < settle_by:
    controls = {n.get('text') for n in nodes()}
    if 'Hands-free: On' in controls or 'Hands-free: Off' in controls: break
else: raise AssertionError('Listener did not settle')
if 'Hands-free: On' not in controls:
    tap('Hands-free: Off')
    wait_for('Hands-free: On', 90)
assert service_running(), 'Listener not running before notification toggle'
settings()
assert settings_switch().get('checked') == 'true', 'User notifications were already disabled; do not change them'
try:
    tap_node(settings_switch())
    assert settings_switch().get('checked') == 'false', 'Could not disable app notifications'
    deadline = time.monotonic() + 25
    while time.monotonic() < deadline and service_running(): time.sleep(.5)
    assert not service_running(), 'Microphone service stayed up after Stop notification was hidden'
    run('shell','am','start','-n',PKG+'/.MainActivity')
    wait_for('Hands-free: Off')
    tap('Hands-free: Off')
    time.sleep(2)
    assert not service_running(), 'Blocked notifications allowed hands-free to restart'
    print('PASS blocked notifications stop and prevent hidden microphone capture')
finally:
    settings()
    if settings_switch().get('checked') == 'false': tap_node(settings_switch())
    assert settings_switch().get('checked') == 'true', 'Could not restore app notifications'

run('shell','am','start','-n',PKG+'/.MainActivity')
wait_for('Hands-free: Off')
tap('Hands-free: Off')
wait_for('Hands-free: On', 90)
print('PASS notifications restored and opt-in listening running')
