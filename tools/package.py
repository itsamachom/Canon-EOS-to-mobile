#!/usr/bin/env python3
"""Package a clean source commit; bind UI/test evidence and signed APK to it."""
import datetime,hashlib,json,os,pathlib,re,shutil,subprocess,sys,xml.etree.ElementTree as ET,zipfile
root=pathlib.Path(__file__).resolve().parents[1]
def git(*args):return subprocess.check_output(['git',*args],cwd=root)
if git('status','--porcelain').strip():raise SystemExit('Commit source changes before packaging')
manifest=ET.parse(root/'app/src/main/AndroidManifest.xml').getroot();ns='{http://schemas.android.com/apk/res/android}'
version_name=manifest.attrib[ns+'versionName'];version=version_name.split('-')[0];version_code=int(manifest.attrib[ns+'versionCode'])
ptp_text=(root/'build/ptp-tests.txt').read_text();eos_text=(root/'build/eos-tests.txt').read_text();ui=json.loads((root/'build/ui-tests.json').read_text())
p=re.search(r'PASS: (\d+) PTP assertions \+ (\d+)',ptp_text);e=re.search(r'PASS: (\d+) EOS',eos_text)
assert p and e and ui['passed'] and ui['version']==version,'Missing matching test evidence'
commit=git('rev-parse','HEAD').decode().strip();out=root/'downloads'/version;out.mkdir(parents=True,exist_ok=True)
apk=root/'build'/f'600D-Companion-{version_name}.apk'
with zipfile.ZipFile(apk) as z:
    assert z.testzip() is None and 'classes.dex' in z.namelist()
    assert z.read('assets/index.html')==(root/'app/src/main/assets/index.html').read_bytes()
chain=pathlib.Path(os.environ.get('TOOLCHAIN',root/'build/toolchain'))
signer=pathlib.Path(os.environ.get('BUILD_TOOLS',chain/'android-15'))/'apksigner'
cert_output=subprocess.check_output([str(signer),'verify','--print-certs',str(apk)]).decode()
cert=re.search(r'certificate SHA-256 digest: (\S+)',cert_output).group(1)
baseline=root/'downloads/0.1.1/600D-Companion-0.1.1-prototype.apk';same_signer=False
if baseline.exists():
    old=subprocess.check_output([str(signer),'verify','--print-certs',str(baseline)]).decode()
    same_signer=re.search(r'certificate SHA-256 digest: (\S+)',old).group(1)==cert
if '--require-signer-match' in sys.argv:assert same_signer,'Signer must match published 0.1.1'
shutil.copy2(apk,out/apk.name)
source=out/f'600D-Companion-{version}-source.zip'
paths=['app','tools','tests','previews','docs','README.md','LICENSE','.gitignore','build-android.sh','test.sh']
source.write_bytes(git('archive','--format=zip','--prefix=Canon-EOS-to-mobile-source/',commit,*paths))
with zipfile.ZipFile(source) as z:
    assert z.testzip() is None
    assert not any(n.endswith(('.keystore','.jks','.apk','.FIR','.BIN')) for n in z.namelist())
    assert z.read('Canon-EOS-to-mobile-source/app/src/main/assets/index.html')==(root/'app/src/main/assets/index.html').read_bytes()
for src,name in [('docs/INSTALL_TH.md','INSTALL_TH.md'),('docs/RELEASE_0.2.0.md','RELEASE_NOTES.md'),('docs/TEST_RESULTS.md','TEST_RESULTS.md'),('LICENSE','LICENSE')]:shutil.copy2(root/src,out/name)
info={
 'version':version_name,'version_code':version_code,'package':manifest.attrib['package'],
 'repository':'https://github.com/itsamachom/Canon-EOS-to-mobile','source_commit':commit,
 'created_utc':datetime.datetime.now(datetime.timezone.utc).isoformat(),
 'min_android_api':26,'target_android_api':35,'build_tools':'35.0.0','java_compiler':'ECJ 3.39.0',
 'physical_android_tested':False,'android_emulator_tested':False,'physical_camera_tested':False,
 'previous_user_feedback':'0.1.1 could expose once in M then no visible response; cause unconfirmed; 0.2.0 requires hardware retest',
 'signing_certificate_sha256':cert,'signer_matches_published_0_1_1':same_signer,'signing':'prototype key; private key not included',
 'verification':{'ptp_assertions':int(p.group(1)),'eos_cache_assertions':int(e.group(1)),'malformed_parser_cases':int(p.group(2)),'browser_menu_tests':ui,'apk_v2_v3_signature':'verified'},
 'live_view':'EOS JPEG segments 1/11 and JPEG-in-9; non-JPEG segment 9 unsupported; streaming during video recording not hardware verified',
 'apk_sha256':hashlib.sha256(apk.read_bytes()).hexdigest(),'license':'MIT'
}
(out/'BUILD_INFO.json').write_text(json.dumps(info,ensure_ascii=False,indent=2)+'\n')
names=[apk.name,source.name,'INSTALL_TH.md','RELEASE_NOTES.md','TEST_RESULTS.md','LICENSE','BUILD_INFO.json']
(out/'SHA256SUMS').write_text(''.join(hashlib.sha256((out/n).read_bytes()).hexdigest()+'  '+n+'\n' for n in names))
print(json.dumps(info,ensure_ascii=False,indent=2))
