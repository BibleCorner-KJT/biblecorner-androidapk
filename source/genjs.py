import json,re
p='src/today/knowingjesus/biblecorner/Js.java'; s=open(p).read()
page=open('js/page.js').read(); off=open('js/offline.html').read()
s=re.sub(r'static final String PAGE = ".*?";\n', lambda m:'static final String PAGE = '+json.dumps(page,ensure_ascii=True)+';\n', s, flags=re.S)
s=re.sub(r'static final String OFFLINE = ".*?";\n', lambda m:'static final String OFFLINE = '+json.dumps(off,ensure_ascii=True)+';\n', s, flags=re.S)
open(p,'w').write(s)
