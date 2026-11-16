import zipfile, sys
base, dex, out = sys.argv[1:4]
src = zipfile.ZipFile(base)
entries = [(i.filename, src.read(i.filename)) for i in src.infolist()]
entries.append(('classes.dex', open(dex,'rb').read()))
order = ['AndroidManifest.xml','classes.dex','resources.arsc']
entries.sort(key=lambda e: (order.index(e[0]) if e[0] in order else 9, e[0]))
with open(out,'wb') as f:
    z = zipfile.ZipFile(f,'w')
    for name, data in entries:
        stored = name == 'resources.arsc' or name.endswith('.png')
        zi = zipfile.ZipInfo(name, date_time=(1981,1,1,1,1,2)); zi.compress_type = zipfile.ZIP_STORED if stored else zipfile.ZIP_DEFLATED
        zi.create_system = 0; zi.external_attr = 0
        if stored:  # 4-byte alignment of the file data (zipalign), required for resources.arsc on Android 11+
            off = f.tell() + 30 + len(name.encode())
            pad = (4 - off % 4) % 4
            zi.extra = b'\x00' * pad
        z.writestr(zi, data)
    z.close()
z = zipfile.ZipFile(out)
for i in z.infolist():
    if i.compress_type == 0:
        dataoff = i.header_offset + 30 + len(i.filename.encode()) + len(i.extra)
        assert dataoff % 4 == 0, (i.filename, dataoff)
print('packed', len(entries), 'entries, aligned')
