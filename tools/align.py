"""Rewrite an APK so stored entries are 4-byte aligned (what zipalign does)."""
import sys, zipfile
src, dst = sys.argv[1], sys.argv[2]
STORE = (".arsc", ".png", ".webp")
with zipfile.ZipFile(src) as zin, open(dst, "wb") as f:
    zout = zipfile.ZipFile(f, "w")
    names = zin.namelist()
    names.sort(key=lambda n: n != "AndroidManifest.xml")
    for name in names:
        data = zin.read(name)
        info = zipfile.ZipInfo(name, date_time=(2008, 1, 1, 0, 0, 0))
        if name.endswith(STORE):
            info.compress_type = zipfile.ZIP_STORED
            header_end = f.tell() + 30 + len(name.encode())
            info.extra = b"\0" * ((4 - header_end % 4) % 4)
        else:
            info.compress_type = zipfile.ZIP_DEFLATED
        zout.writestr(info, data)
    zout.close()
