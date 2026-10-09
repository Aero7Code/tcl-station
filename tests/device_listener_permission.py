"""A denied runtime mic permission must never start hands-free capture."""
import os
import re
import subprocess
import time
import xml.etree.ElementTree as ET

ADB = os.path.join(os.environ['TMPDIR'], 'tcl-adb/usr/bin/adb')
def run(*args):
    return subprocess.run([ADB, *args], check=True, capture_output=True, text=True).stdout

def nodes():
    run('shell', 'uiautomator', 'dump', '/sdcard/listener-permission-ui.xml')
    xml = subprocess.run([ADB, 'exec-out', 'cat', '/sdcard/listener-permission-ui.xml'], check=True, capture_output=True).stdout
    return list(ET.fromstring(xml).iter('node'))

def tap(text):
    element = next((node for node in nodes() if node.get('text') == text), None)
    assert element is not None, f'Missing permission control {text}'
    a,b,c,d = map(int, re.findall(r'\d+', element.get('bounds')))
    run('shell','input','tap',str((a+c)//2),str((b+d)//2))

if any(n.get('text') == 'DON’T ALLOW' for n in nodes()):
    tap('DON’T ALLOW')  # Recover a prompt left open by an interrupted test.
run('shell','pm','revoke','com.aero.tclstation','android.permission.RECORD_AUDIO')
run('shell','am','force-stop','com.aero.tclstation')
run('shell','am','start','-n','com.aero.tclstation/.MainActivity')
tap('Hands-free: Off')
labels = {n.get('text') for n in nodes()}
if 'DON’T ALLOW' in labels:
    tap('DON’T ALLOW')
# After repeated denials Android may decline silently rather than show the dialog.
time.sleep(1)
assert any(n.get('text') == 'Hands-free: Off' for n in nodes())
assert 'ListeningService' not in run('shell','dumpsys','activity','services','com.aero.tclstation')
print('PASS denied permission leaves microphone service off')
