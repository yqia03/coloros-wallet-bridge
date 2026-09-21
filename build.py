#!/usr/bin/env python3
"""Build a signed sideload APK using Android SDK 36 tools, without Gradle."""
import argparse
import os
from pathlib import Path
import secrets
import shutil
import subprocess
import zipfile

root = Path(__file__).resolve().parent
parser = argparse.ArgumentParser()
parser.add_argument('--sdk', required=True, type=Path)
parser.add_argument('--jdk', required=True, type=Path)
parser.add_argument('--signing-dir', type=Path, default=root / 'build' / 'signing')
args = parser.parse_args()
sdk, jdk = args.sdk.resolve(), args.jdk.resolve()
tools = sdk / 'build-tools' / '36.0.0'
android_jar = sdk / 'platforms' / 'android-36' / 'android.jar'
build = root / 'build'
source = root / 'app' / 'src' / 'main'
env = dict(os.environ, JAVA_HOME=str(jdk), PATH=str(jdk / 'bin') + os.pathsep + os.environ.get('PATH', ''))

def run(*command):
    subprocess.run([str(item) for item in command], check=True, env=env)

for folder in ('generated', 'classes', 'dex', 'tests'):
    path = build / folder
    if path.exists():
        shutil.rmtree(path)
    path.mkdir(parents=True)

# Tests use actual routing behavior without Android dependencies.
run(jdk / 'bin/javac', '--release', '8', '-Xlint:-options', '-encoding', 'UTF-8', '-d', build / 'tests',
    source / 'java/local/uway/walletbridge/RoutePolicy.java', root / 'tests/RoutePolicyTest.java')
run(jdk / 'bin/java', '-cp', build / 'tests', 'local.uway.walletbridge.RoutePolicyTest')

run(tools / 'aapt2', 'compile', '--dir', source / 'res', '-o', build / 'resources.zip')
run(tools / 'aapt2', 'link', '-I', android_jar, '--manifest', source / 'AndroidManifest.xml',
    '--java', build / 'generated', '-o', build / 'base.apk', build / 'resources.zip')
java_files = sorted((source / 'java').rglob('*.java')) + sorted((build / 'generated').rglob('*.java'))
run(jdk / 'bin/javac', '--release', '8', '-Xlint:-options', '-encoding', 'UTF-8', '-classpath', android_jar,
    '-d', build / 'classes', *java_files)
class_files = sorted((build / 'classes').rglob('*.class'))
run(tools / 'd8', '--min-api', '31', '--lib', android_jar, '--output', build / 'dex', *class_files)
with zipfile.ZipFile(build / 'base.apk', 'a', zipfile.ZIP_DEFLATED) as archive:
    for dex in sorted((build / 'dex').glob('*.dex')):
        archive.write(dex, dex.name)
run(tools / 'zipalign', '-f', '4', build / 'base.apk', build / 'aligned.apk')

signing = args.signing_dir.resolve()
signing.mkdir(parents=True, exist_ok=True, mode=0o700)
key, password = signing / 'wallet-bridge.p12', signing / 'password.txt'
if not key.exists():
    if not password.exists():
        password.write_text(secrets.token_urlsafe(32), encoding='utf-8')
        password.chmod(0o600)
    run(jdk / 'bin/keytool', '-genkeypair', '-keystore', key, '-storetype', 'PKCS12',
        '-storepass:file', password, '-alias', 'wallet-bridge', '-keyalg', 'RSA', '-keysize', '3072',
        '-validity', '10000', '-dname', 'CN=Wallet Bridge Personal Build', '-noprompt')
    key.chmod(0o600)
if not password.exists():
    raise SystemExit('Keystore exists, but signing password file is missing.')

apk = build / 'Wallet-Bridge-0.1.0.apk'
signer = [jdk / 'bin/java', '-jar', tools / 'lib/apksigner.jar']
run(*signer, 'sign', '--ks', key, '--ks-key-alias', 'wallet-bridge', '--ks-pass', 'file:' + str(password),
    '--out', apk, build / 'aligned.apk')
run(*signer, 'verify', '--verbose', '--print-certs', apk)
run(tools / 'zipalign', '-c', '4', apk)
print('Signed APK:', apk)
