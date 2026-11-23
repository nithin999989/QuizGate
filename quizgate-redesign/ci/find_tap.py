#!/usr/bin/env python3
# Usage: find_tap.py UI_XML TEXT [MIN_HEIGHT] -> prints "x y" centre of first node containing TEXT
# Nodes shorter than MIN_HEIGHT pixels are skipped (clipped/off-screen slivers).
import sys,xml.etree.ElementTree as ET,re
ui,needle=sys.argv[1],sys.argv[2].lower()
minh=int(sys.argv[3]) if len(sys.argv)>3 else 0
for el in ET.parse(ui).iter('node'):
    t=(el.get('text') or '').strip()
    if needle in t.lower() and el.get('bounds'):
        m=re.match(r'\[(\d+),(\d+)\]\[(\d+),(\d+)\]',el.get('bounds'))
        if m:
            x1,y1,x2,y2=map(int,m.groups())
            if (y2-y1)>=minh:
                print(f'{(x1+x2)//2} {(y1+y2)//2}')
                sys.exit(0)
sys.exit(1)
