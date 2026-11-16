import os,struct,sys
def cp_end(b):
    n=struct.unpack('>H',b[8:10])[0]; i=10; k=1; utf={}
    while k<n:
        t=b[i]
        if t==1: L=struct.unpack('>H',b[i+1:i+3])[0]; utf[k]=b[i+3:i+3+L].decode('utf8','replace'); i+=3+L
        elif t in (3,4): i+=5
        elif t in (5,6): i+=9; k+=1
        elif t in (7,8,16,19,20): i+=3
        elif t in (9,10,11,12,17,18): i+=5
        elif t==15: i+=4
        k+=1
    return i,utf
def strip(p):
    b=open(p,'rb').read(); i,utf=cp_end(b); out=bytearray(b[:i])
    out+=b[i:i+6]; i+=6
    ni=struct.unpack('>H',b[i:i+2])[0]; out+=b[i:i+2+2*ni]; i+=2+2*ni
    removed=0
    for kind in ('f','m'):
        n=struct.unpack('>H',b[i:i+2])[0]; out+=b[i:i+2]; i+=2
        for _ in range(n):
            out+=b[i:i+6]; i+=6
            ac=struct.unpack('>H',b[i:i+2])[0]; i+=2; keep=[]
            for _ in range(ac):
                nm=struct.unpack('>H',b[i:i+2])[0]; L=struct.unpack('>I',b[i+2:i+6])[0]; chunk=b[i:i+6+L]; i+=6+L
                if utf.get(nm)=='MethodParameters': removed+=1; continue
                keep.append(chunk)
            out+=struct.pack('>H',len(keep)); [out.extend(c) for c in keep]
    out+=b[i:]
    open(p,'wb').write(out); return removed
t=0
for d,_,fs in os.walk(sys.argv[1]):
    for f in fs:
        if f.endswith('.class'): t+=strip(os.path.join(d,f))
print('removed',t)
