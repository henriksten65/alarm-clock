#!/usr/bin/env python3
"""Tiny emulator UI driver for manual testing.

  ui.py dump                      list visible texts / content-descs
  ui.py tap TEXT [--near TEXT2]   tap the node whose text or content-desc == TEXT
                                  (closest one vertically to TEXT2 if several)
  ui.py has TEXT                  exit 0 if a node with that text/desc is visible
"""
import os, re, subprocess, sys

ADB = os.path.expanduser('~/Android/Sdk/platform-tools/adb')


def nodes():
    subprocess.run([ADB, 'shell', 'uiautomator', 'dump', '/sdcard/ui.xml'], capture_output=True)
    xml = subprocess.run([ADB, 'shell', 'cat', '/sdcard/ui.xml'], capture_output=True, text=True).stdout
    out = []
    for n in re.findall(r'<node [^>]+>', xml):
        text = re.search(r' text="([^"]*)"', n).group(1)
        desc = re.search(r'content-desc="([^"]*)"', n).group(1)
        b = list(map(int, re.findall(r'\d+', re.search(r'bounds="([^"]+)"', n).group(1))))
        out.append((text, desc, b))
    return out


def find(label, near=None):
    all_nodes = nodes()
    matches = [n for n in all_nodes if label in (n[0], n[1])]
    if not matches:
        return None
    if near:
        ref = [n for n in all_nodes if near in (n[0], n[1])]
        if ref:
            y = (ref[0][2][1] + ref[0][2][3]) / 2
            matches.sort(key=lambda n: abs((n[2][1] + n[2][3]) / 2 - y))
    return matches[0]


cmd = sys.argv[1]
if cmd == 'dump':
    for t, d, b in nodes():
        if t or d:
            print(repr(t) if t else f'[{d}]', b)
elif cmd == 'tap':
    near = sys.argv[sys.argv.index('--near') + 1] if '--near' in sys.argv else None
    n = find(sys.argv[2], near)
    if not n:
        sys.exit(f'not found: {sys.argv[2]}')
    b = n[2]
    subprocess.run([ADB, 'shell', 'input', 'tap', str((b[0] + b[2]) // 2), str((b[1] + b[3]) // 2)])
elif cmd == 'has':
    sys.exit(0 if find(sys.argv[2]) else 1)
