#!/usr/bin/env python3
# Usage: find_tap.py UI_XML TEXT  -> prints "x y" centre of first node containing TEXT
import sys,xml.etree.ElementTree as ET,re
ui,needle=sys.argv[1],sys.argv[2].lower()
for el in ET.parse(ui).iter('node'):
    t=(el.get('text') or '').strip()
    if needle in t.lower() and el.get('bounds'):
        m=re.match(r'\[(\d+),(\d+)\]\[(\d+),(\d+)\]',el.get('bounds'))
        if m:
            x1,y1,x2,y2=map(int,m.groups())
            print(f'{(x1+x2)//2} {(y1+y2)//2}')
            sys.exit(0)
sys.exit(1)
