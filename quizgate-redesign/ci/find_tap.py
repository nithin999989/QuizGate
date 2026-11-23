#!/usr/bin/env python3
# Usage: find_tap.py UI_XML TEXT [MIN_HEIGHT] [REQUIRE_CLICKABLE] -> "x y" centre of best node containing TEXT.
# Preference: clickable+exact > clickable+substring > exact > substring. Toolbar titles are not
# clickable, rows/buttons are; this keeps label needles from tapping titles. Nodes shorter than
# MIN_HEIGHT px are skipped (clipped slivers). REQUIRE_CLICKABLE=1 keeps only clickable nodes.
import sys,xml.etree.ElementTree as ET,re
ui,needle=sys.argv[1],sys.argv[2].lower()
minh=int(sys.argv[3]) if len(sys.argv)>3 else 0
reqclick=len(sys.argv)>4 and sys.argv[4]=='1'
cands=[]
for el in ET.parse(ui).iter('node'):
    t=(el.get('text') or '').strip()
    if needle in t.lower() and el.get('bounds'):
        m=re.match(r'\[(\d+),(\d+)\]\[(\d+),(\d+)\]',el.get('bounds'))
        if m:
            x1,y1,x2,y2=map(int,m.groups())
            if (y2-y1)>=minh:
                click=el.get('clickable')=='true'
                if reqclick and not click: continue
                cands.append(((x1+x2)//2,(y1+y2)//2,t.lower()==needle,click))
cands.sort(key=lambda c:(not c[3],not c[2]))
if cands:
    print(f'{cands[0][0]} {cands[0][1]}');sys.exit(0)
sys.exit(1)
