"""On a narrow portrait footer, page count remains legible beside both voice controls."""
import os
import subprocess
import xml.etree.ElementTree as ET

adb = os.path.join(os.environ['TMPDIR'], 'tcl-adb/usr/bin/adb')
def run(*args):
    return subprocess.run([adb,*args],check=True,capture_output=True,text=True).stdout
run('shell','am','start','-S','-n','com.aero.tclstation/.MainActivity')
run('shell','uiautomator','dump','/sdcard/footer-portrait.xml')
data = subprocess.run([adb,'exec-out','cat','/sdcard/footer-portrait.xml'],check=True,capture_output=True).stdout
root = ET.fromstring(data)
viewport = root.find('node').get('bounds')
width,height = map(int,__import__('re').findall(r'\d+',viewport)[2:])
assert width < height, 'Run this test with the tablet physically upright'
labels={n.get('text') for n in root.iter('node')}
assert '1 / 3' in labels, 'Portrait page count should fit without a clipped swipe hint'
assert {'Hands-free: Off','Speak','›'} <= labels
print('PASS portrait footer shows legible page count and separate voice controls')
