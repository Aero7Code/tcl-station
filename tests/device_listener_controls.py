"""Device integration: hands-free control is visible, permission-gated, and stoppable."""
import os
import re
import subprocess
import time
import xml.etree.ElementTree as ET

ADB = os.environ.get('ADB', os.path.join(os.environ['TMPDIR'], 'tcl-adb/usr/bin/adb'))

def run(*args):
    return subprocess.run([ADB, *args], check=True, capture_output=True, text=True).stdout

def nodes():
    run('shell','uiautomator','dump','/sdcard/listener-ui.xml')
    xml = subprocess.run([ADB,'exec-out','cat','/sdcard/listener-ui.xml'],check=True,capture_output=True).stdout
    return list(ET.fromstring(xml).iter('node'))

def tap(text):
    n=next((n for n in nodes() if n.get('text') == text),None)
    assert n is not None, f'Missing {text}'
    x1,y1,x2,y2=map(int,re.findall(r'\d+',n.get('bounds')))
    run('shell','input','tap',str((x1+x2)//2),str((y1+y2)//2))

def station_mic_active():
    current = run('shell','dumpsys','audio').split('Audio event log: recording activity received')[0]
    return bool(re.search(r'active\? true\s*\n[^\n]*pack:com\.aero\.tclstation', current))

run('shell','am','start','-S','-n','com.aero.tclstation/.MainActivity')
assert any(n.get('text') == 'Hands-free: Off' for n in nodes()), 'Hands-free toggle is not visible'
print('PASS hands-free control is visible and initially off')
run('shell','pm','grant','com.aero.tclstation','android.permission.RECORD_AUDIO')
tap('Hands-free: Off')
deadline = time.monotonic() + 90
while time.monotonic() < deadline:
    labels = {n.get('text') for n in nodes()}
    if 'Hands-free: On' in labels:
        break
    time.sleep(2)
else:
    raise AssertionError('Offline microphone never reached On; check device logcat')
services = run('shell','dumpsys','activity','services','com.aero.tclstation')
assert 'ListeningService' in services, 'Listener is not an Android foreground service'
assert station_mic_active(), 'Station does not have a live tablet microphone capture'
tap('Speak')
time.sleep(1)
assert not station_mic_active(), 'Push-to-talk must release Station microphone before external recognizer'
run('shell','input','keyevent','4')
time.sleep(1.5)
assert station_mic_active(), 'Station microphone did not resume after push-to-talk cancellation'
run('shell','input','keyevent','26')  # Screen off, not a service stop.
time.sleep(2)
assert 'ListeningService' in run('shell','dumpsys','activity','services','com.aero.tclstation')
assert station_mic_active(), 'Listener lost microphone capture with screen off'
run('shell','input','keyevent','26')
time.sleep(1)
run('shell','wm','dismiss-keyguard')
run('shell','cmd','statusbar','collapse')
run('shell','input','swipe','600','650','600','100','400')  # Dismiss noncredential lock shade.
run('shell','am','start','-n','com.aero.tclstation/.MainActivity')
tap('Hands-free: On')
deadline = time.monotonic() + 8
while time.monotonic() < deadline:
    if any(n.get('text') == 'Hands-free: Off' for n in nodes()):
        break
else:
    raise AssertionError('Explicit stop did not turn microphone UI off')
assert 'ListeningService' not in run('shell','dumpsys','activity','services','com.aero.tclstation')
assert not station_mic_active(), 'Microphone capture persisted after explicit stop'
tap('Hands-free: Off')
deadline = time.monotonic() + 20
while time.monotonic() < deadline and not station_mic_active():
    time.sleep(1)
assert station_mic_active(), 'Microphone did not restart after a normal explicit stop'
tap('Hands-free: On')
deadline = time.monotonic() + 8
while time.monotonic() < deadline and station_mic_active():
    time.sleep(0.25)
assert not station_mic_active(), 'Microphone remained active after second stop'
print('PASS opt-in local microphone survives screen off, pauses for Speak, stops and restarts explicitly')
