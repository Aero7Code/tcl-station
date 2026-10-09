"""Continuous microphone use must be explicit, stoppable, and not boot-persistent."""
import xml.etree.ElementTree as ET

ns = '{http://schemas.android.com/apk/res/android}'
root = ET.parse('AndroidManifest.xml').getroot()
permissions = {node.get(ns+'name') for node in root.findall('uses-permission')}
assert 'android.permission.RECORD_AUDIO' in permissions
assert 'android.permission.FOREGROUND_SERVICE' in permissions
services = root.findall('./application/service')
assert len(services) == 1, 'Expected exactly one local microphone service'
service = services[0]
assert service.get(ns+'name') == '.ListeningService'
assert service.get(ns+'exported') == 'false'
assert service.get(ns+'foregroundServiceType') == 'microphone'
assert root.find('./application/receiver') is None, 'No boot or hidden restart receiver'
print('PASS listener manifest declares opt-in microphone foreground service, no boot receiver')
