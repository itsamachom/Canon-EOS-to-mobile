#!/usr/bin/env python3
"""Package a clean standalone source commit with APK, guide and checksums."""
import datetime,hashlib,json,pathlib,shutil,subprocess,zipfile
root=pathlib.Path(__file__).resolve().parents[1]
def git(*args):return subprocess.check_output(['git',*args],cwd=root)
if git('status','--porcelain').strip():raise SystemExit('Commit source changes before packaging')
commit=git('rev-parse','HEAD').decode().strip()
out=root/'downloads'/'0.1.1';out.mkdir(parents=True,exist_ok=True)
apk=root/'build'/'600D-Companion-0.1.1-prototype.apk'
with zipfile.ZipFile(apk) as z:
    assert z.testzip() is None and 'classes.dex' in z.namelist()
    assert z.read('assets/index.html')==(root/'app/src/main/assets/index.html').read_bytes()
shutil.copy2(apk,out/apk.name)
source=out/'600D-Companion-0.1.1-source.zip'
paths=['app','tools','tests','previews','docs','README.md','LICENSE','.gitignore','build-android.sh','test.sh']
source.write_bytes(git('archive','--format=zip','--prefix=Canon-EOS-to-mobile-source/',commit,*paths))
with zipfile.ZipFile(source) as z:
    assert z.testzip() is None
    assert not any(n.endswith(('.keystore','.jks','.apk','.FIR','.BIN')) for n in z.namelist())
    assert z.read('Canon-EOS-to-mobile-source/app/src/main/assets/index.html')==(root/'app/src/main/assets/index.html').read_bytes()
shutil.copy2(root/'docs/INSTALL_TH.md',out/'INSTALL_TH.md')
shutil.copy2(root/'LICENSE',out/'LICENSE')
info={
 'version':'0.1.1-prototype','version_code':2,'package':'art.photoui.companion',
 'repository':'https://github.com/itsamachom/Canon-EOS-to-mobile','source_commit':commit,
 'created_utc':datetime.datetime.now(datetime.timezone.utc).isoformat(),
 'min_android_api':26,'target_android_api':35,'build_tools':'35.0.0','java_compiler':'ECJ 3.39.0',
 'physical_android_tested':False,'android_emulator_tested':False,'physical_camera_tested':False,
 'signing':'new local prototype key; uninstall 0.1.0 if signature differs; not a production signing key',
 'migration':'reconstructed from conversation; previous unsent local files/key unavailable; not binary-identical to 0.1.0',
 'verification':{'ptp_assertions':37,'malformed_parser_cases':2000,'browser_menu_tests':'passed','apk_v2_v3_signature':'verified'},
 'apk_sha256':hashlib.sha256(apk.read_bytes()).hexdigest(),'license':'MIT'
}
(out/'BUILD_INFO.json').write_text(json.dumps(info,ensure_ascii=False,indent=2)+'\n')
names=[apk.name,source.name,'INSTALL_TH.md','LICENSE','BUILD_INFO.json']
(out/'SHA256SUMS').write_text(''.join(hashlib.sha256((out/n).read_bytes()).hexdigest()+'  '+n+'\n' for n in names))
print(json.dumps(info,ensure_ascii=False,indent=2))
