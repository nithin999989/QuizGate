#!/usr/bin/env python3
# Usage: find_tap.py UI_XML TEXT [MIN_HEIGHT] -> prints "x y" centre of the best node containing TEXT.
# Exact-text matches win over substring matches; nodes shorter than MIN_HEIGHT px are skipped (clipped slivers).
import sys,xml.etree.ElementTree as ET,re
ui,needle=sys.argv[1],sys.argv[2].lower()
minh=int(sys.argv[3]) if len(sys.argv)>3 else 0
best=None
for el in ET.parse(ui).iter('node'):
    t=(el.get('text') or '').strip()
    if needle in t.lower() and el.get('bounds'):
        m=re.match(r'\[(\d+),(\d+)\]\[(\d+),(\d+)\]',el.get('bounds'))
        if m:
            x1,y1,x2,y2=map(int,m.groups())
            if (y2-y1)>=minh:
                cand=((x1+x2)//2,(y1+y2)//2)
                if t.lower()==needle:
                    print(f'{cand[0]} {cand[1]}');sys.exit(0)
                if best is None:best=cand
if best:print(f'{best[0]} {best[1]}');sys.exit(0)
sys.exit(1)
