#!/usr/bin/env python3
# Usage: answer_tap.py UI_XML BANK_JSON wrong|correct
# Prints "x y" centre coordinates of the chosen answer option, or nothing.
import json,sys,xml.etree.ElementTree as ET,re

def norm(s): return re.sub(r'\s+',' ',(s or '')).strip()

def centre(b):
    m=re.match(r'\[(\d+),(\d+)\]\[(\d+),(\d+)\]',b or '')
    if not m: return None
    x1,y1,x2,y2=map(int,m.groups())
    return (x1+x2)//2,(y1+y2)//2

def main():
    ui,bank,mode=sys.argv[1],sys.argv[2],sys.argv[3]
    qs=json.load(open(bank))['questions']
    nodes=[]
    for el in ET.parse(ui).iter('node'):
        t=norm(el.get('text'))
        if t: nodes.append((t,el.get('bounds')))
    q=None
    for cand in qs:
        qt=norm(cand['q'])
        if any(t==qt or t.startswith(qt[:60]) or qt.startswith(t[:60]) for t,_ in nodes if len(t)>30):
            q=cand;break
    if not q:
        print('no question node found',file=sys.stderr);return 1
    ans=q['answer']
    picks=[]
    for i,opt in enumerate(q['options']):
        on=norm(opt)
        for t,b in nodes:
            if on and (on in t or t.endswith(on) or on[:40] in t):
                c=centre(b)
                if c: picks.append((i,c));break
    if not picks:
        print('no option nodes found',file=sys.stderr);return 1
    want_wrong=(mode=='wrong')
    for i,c in picks:
        if (i!=ans)==want_wrong:
            print(f'{c[0]} {c[1]}')
            print(f'q={q["id"]} picked index {i} (answer is {ans}) mode={mode}',file=sys.stderr)
            return 0
    print('no matching option for mode',file=sys.stderr);return 1

sys.exit(main())
