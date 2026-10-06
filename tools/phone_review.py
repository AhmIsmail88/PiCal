"""ADB review of synthetic calculator inputs; no project records are changed."""
import re
import os
import subprocess
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

ADB = r"F:\SDK\platform-tools\adb.exe"
ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / os.environ.get('PICAL_REVIEW_DIR', 'ui-test/20261006')
OUT.mkdir(parents=True, exist_ok=True)

def adb(*args):
    return subprocess.run([ADB, '-s', os.environ.get('ANDROID_SERIAL', 'R5GL101Q6TW'), *args], check=True, capture_output=True, text=True, encoding='utf-8', errors='replace').stdout

def snapshot(name='state'):
    adb('shell', 'uiautomator', 'dump', '/sdcard/flowtrack-review-ui.xml')
    adb('pull', '/sdcard/flowtrack-review-ui.xml', str(OUT / f'{name}.xml'))
    nodes = list(ET.parse(OUT / f'{name}.xml').iter('node'))
    expected = os.environ.get('PICAL_EXPECTED_PACKAGE')
    if expected and not any(n.get('package') == expected for n in nodes):
        raise RuntimeError(f'Expected foreground package {expected}; stopping UI automation')
    return nodes

def box(node):
    return list(map(int, re.findall(r'-?\d+', node.get('bounds'))))

def tap(node):
    x1,y1,x2,y2 = box(node)
    adb('shell', 'input', 'tap', str((x1+x2)//2), str((y1+y2)//2))

def set_field(label, value):
    for attempt in range(12):
        nodes = snapshot()
        labels = [i for i,n in enumerate(nodes) if n.get('text') == label]
        if labels:
            editor = next((n for n in nodes[labels[0]+1:] if n.get('class') == 'android.widget.EditText'), None)
            if editor is not None and box(nodes[labels[0]])[1] >= 340 and box(editor)[3] <= 2000 and box(editor)[1] > box(nodes[labels[0]])[1]:
                previous = editor.get('text') or ''
                tap(editor)
                adb('shell', 'input', 'keyevent', '123', *(['67'] * (len(previous)+2)))
                adb('shell', 'input', 'text', str(value))
                adb('shell', 'input', 'keyevent', '4')
                updated = snapshot()
                updated_label = next(i for i,n in enumerate(updated) if n.get('text') == label)
                updated_editor = next(n for n in updated[updated_label+1:] if n.get('class') == 'android.widget.EditText')
                if updated_editor.get('text') != str(value):
                    raise RuntimeError(f'Input verification failed for {label}: {updated_editor.get("text")}')
                print(f'FIELD {label} = {value}', flush=True)
                return
            if box(nodes[labels[0]])[1] < 340:
                adb('shell', 'input', 'swipe', '600', '850', '600', '1830', '450')
                continue
        adb('shell', 'input', 'swipe', '600', '1830', '600', '850', '450')
    raise RuntimeError(f'Field not found: {label}')

def capture(name):
    nodes = snapshot(name)
    adb('shell', 'screencap', '-p', '/sdcard/flowtrack-review.png')
    adb('pull', '/sdcard/flowtrack-review.png', str(OUT / f'{name}.png'))
    text = '\n'.join(n.get('text') for n in nodes if n.get('text'))
    (OUT / f'{name}.txt').write_text(text, encoding='utf-8')
    print(text, flush=True)
    return text

if __name__ == '__main__':
    if sys.argv[1] == 'miter':
        for _ in range(5): adb('shell', 'input', 'swipe', '600', '850', '600', '1830', '350')
        for label,value in [
            ('ط§ظ„ظ‚ط·ط± ط§ظ„ط®ط§ط±ط¬ظٹ D (ظ…ظ…)', '114.3'), ('ط³ظ…ظƒ ظ‚ط·ط¹ط© ط§ظ„ط§ظ†ط­ظ†ط§ط، T (ظ…ظ…)', '6.02'),
            ('ط¥ط¬ظ…ط§ظ„ظٹ ط§ظ„ط³ظ…ط§ط­ظٹط§طھ c (ظ…ظ…)', '1.5'), ('ط§ظ„ط¥ط¬ظ‡ط§ط¯ ط§ظ„ظ…ط³ظ…ظˆط­ ط¨ظ‡ S (ظ…ظٹط¬ط§ط¨ط§ط³ظƒط§ظ„)', '138'),
            ('ظ…ط¹ط§ظ…ظ„ ط§ظ„ط¬ظˆط¯ط© E', '1'), ('ظ…ط¹ط§ظ…ظ„ طھظ‚ظ„ظٹظ„ ظ‚ظˆط© ط§ظ„ظ„ط­ط§ظ… W', '1'),
            ('ط¶ط؛ط· ط§ظ„طھطµظ…ظٹظ… ط§ظ„ط¯ط§ط®ظ„ظٹ ط§ظ„ظ…ظ‚ط§ط³ ط¨ط§ظ„ظ†ط³ط¨ط© ظ„ظ„ط¬ظˆ (ط¨ط§ط±)', '20'), ('ط²ط§ظˆظٹط© ط§ظ„ط§ظ†ط­ظ†ط§ط، ط§ظ„ظƒظ„ظٹط© (آ°)', '90'),
            ('ط¹ط¯ط¯ ظˆطµظ„ط§طھ ط§ظ„ط§ظ†ط­ظ†ط§ط، ط§ظ„ظ…ط§ظٹظ„', '2'), ('ظ†طµظپ ظ‚ط·ط± ط§ظ„ط§ظ†ط­ظ†ط§ط، ط§ظ„ظپط¹ظ‘ط§ظ„ R1 / R (ظ…ظ…)', '150')
        ]:
            set_field(label, value)
        for _ in range(2): adb('shell', 'input', 'swipe', '600', '1830', '600', '1050', '450')
        capture('miter-ar-results')
    else:
        capture(sys.argv[1])
