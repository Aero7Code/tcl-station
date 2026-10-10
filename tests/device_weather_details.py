"""Real tablet UI smoke for Weather expansion, live forecast and voice-entry action."""
import os
import re
import subprocess
import time
import xml.etree.ElementTree as ET

ADB = os.environ.get('ADB', 'adb')
def run(*args):
    return subprocess.run([ADB, *args], check=True, capture_output=True, text=True).stdout

def nodes():
    run('shell', 'uiautomator', 'dump', '/sdcard/station-weather-test.xml')
    xml = subprocess.run([ADB, 'exec-out', 'cat', '/sdcard/station-weather-test.xml'], check=True, capture_output=True).stdout
    return list(ET.fromstring(xml).iter('node'))

def tap(label):
    entry = next((n for n in nodes() if n.get('text') == label), None)
    assert entry is not None, f'Missing {label}'
    x1, y1, x2, y2 = map(int, re.findall(r'\d+', entry.get('bounds')))
    run('shell', 'input', 'tap', str((x1+x2)//2), str((y1+y2)//2))

run('shell', 'am', 'start', '-S', '-n', 'com.aero.tclstation/.MainActivity')
tap('Weather')
tap('Detailed weather')
time.sleep(2)
labels = [n.get('text', '') for n in nodes()]
assert 'REST OF THE WEEK' in labels and 'Close' in labels, labels
assert any('Observed at' in text for text in labels) or 'Current conditions unavailable' in labels
unavailable = 'Current conditions unavailable' in labels
print('PASS Weather detail opens with condition data or an honest unavailable state')
seen = set(labels)
for _ in range(4):
    run('shell', 'input', 'swipe', '400', '600', '400', '130', '550')
    seen.update(n.get('text', '') for n in nodes())
assert any('Forecast: Open-Meteo' in text for text in seen)
if not unavailable:
    daily = [text for text in seen if 'Rain ' in text and '° / ' in text]
    assert len(daily) >= 5, f'Expected rest of the week, got {len(daily)} rows'
    print('PASS actual daily forecast scrolls through rest of week')
else:
    assert 'Forecast unavailable — try Refresh when connected.' in seen
    print('PASS unavailable weather does not invent a forecast')
run('shell', 'input', 'keyevent', '4')
run('shell', 'am', 'start', '-a', 'com.aero.tclstation.OPEN_WEATHER', '-f', '0x34000000', '-n', 'com.aero.tclstation/.MainActivity')
labels = [n.get('text', '') for n in nodes()]
assert 'Detailed weather' in labels and 'Refresh weather' in labels, 'weather navigation action did not expand panel'
print('PASS weather navigation action selects and expands Weather panel')
run('shell', 'am', 'start', '-S', '-n', 'com.aero.tclstation/.MainActivity')
entry = next(n for n in nodes() if n.get('text') == 'Weather')
x1, y1, x2, y2 = map(int, re.findall(r'\d+', entry.get('bounds')))
x, y = (x1+x2)//2, (y1+y2)//2
run('shell', 'input', 'tap', str(x), str(y))
time.sleep(.7)
run('shell', 'input', 'tap', str(x), str(y))
assert 'REST OF THE WEEK' in [n.get('text', '') for n in nodes()], 'double tap did not open details'
print('PASS two quick Weather card presses open detail')
