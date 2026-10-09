"""A reversible launcher must be an eligible Home activity, not a kiosk."""
import xml.etree.ElementTree as ET

ns = '{http://schemas.android.com/apk/res/android}'
root = ET.parse('AndroidManifest.xml').getroot()
activities = root.findall('./application/activity')
assert any(
    activity.get(ns + 'exported') == 'true'
    and any(
        {a.get(ns + 'name') for a in f.findall('action')} >= {'android.intent.action.MAIN'}
        and {c.get(ns + 'name') for c in f.findall('category')} >= {
            'android.intent.category.HOME', 'android.intent.category.DEFAULT'}
        for f in activity.findall('intent-filter'))
    for activity in activities
), 'Station is not an eligible Android Home activity'
assert root.find("./application/receiver") is None, 'Do not install boot/persistence receiver'
print('PASS Home intent filter present without boot persistence')
