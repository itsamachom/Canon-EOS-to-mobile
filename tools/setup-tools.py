#!/usr/bin/env python3
"""Download pinned official Linux x86_64 Android SDK tools. Requires Java 21+."""
import hashlib,pathlib,sys,urllib.request,zipfile
dest=pathlib.Path(sys.argv[1] if len(sys.argv)>1 else 'build/toolchain').resolve()
dest.mkdir(parents=True,exist_ok=True)
artifacts=[
 ('https://dl.google.com/android/repository/platform-35_r02.zip','platform.zip','0988cacad01b38a18a47bac14a0695f246bc76c1b06c0eeb8eb0dc825ab0c8e0'),
 ('https://dl.google.com/android/repository/build-tools_r35_linux.zip','build-tools.zip','bd3a4966912eb8b30ed0d00b0cda6b6543b949d5ffe00bea54c04c81e1561d88'),
 ('https://repo.maven.apache.org/maven2/org/eclipse/jdt/ecj/3.39.0/ecj-3.39.0.jar','ecj.jar','01f5a92ac19bb2b3bf85e295a68f2c73c264369109158b566ce9b490af982948')]
for url,name,digest in artifacts:
    target=dest/name
    if not target.exists():
        with urllib.request.urlopen(url,timeout=120) as src,target.open('wb') as out:
            while chunk:=src.read(1024*1024):out.write(chunk)
    if hashlib.sha256(target.read_bytes()).hexdigest()!=digest:raise SystemExit(f'Checksum mismatch: {name}; remove file and retry')
    if name.endswith('.zip'):
        with zipfile.ZipFile(target) as archive:
            for entry in archive.infolist():
                if not (dest/entry.filename).resolve().is_relative_to(dest):raise SystemExit('Unsafe archive path')
            archive.extractall(dest)
for name in ['aapt2','d8','zipalign','apksigner']:(dest/'android-15'/name).chmod(0o755)
print(f'TOOLCHAIN="{dest}" bash build-android.sh')
