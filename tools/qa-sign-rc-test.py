#!/usr/bin/env python3
"""Local-only signer for RC1 *instrumentation* APK. Never signs/installs the app.

Uses the same already-confirmed release keystore certificate, without copying
its contents, alias, password, or private location into Git or reporting logs.
"""

import getpass
import hashlib
import os
from pathlib import Path
import re
import shutil
import stat
import subprocess
import sys
import tempfile
import warnings

RELEASE_BASE = '06c863290733490dd04d342aef991157f71be314'
EXTENSIONS = frozenset({'.jks', '.keystore', '.p12', '.pfx', '.pkcs12'})
EXCLUDED = frozenset({'.cache', '.git', '.gradle', 'node_modules', 'build', '.venv', 'venv', 'Sdk', '.npm'})
MANIFEST_ENTRY = re.compile(r'([0-9a-fA-F]{64})\s+\*?([^\s/\\]+)\Z')
APK_CERT = re.compile(r'certificate\s+SHA-256\s+digest\s*:\s*([0-9a-fA-F:]+)', re.I)
ENTRY = re.compile(r'(?m)^\s*Entry type:\s*PrivateKeyEntry\s*$')
FIRST_CERT = re.compile(r'(?m)^\s*Certificate\[1\]:\s*$')
KEY_SHA = re.compile(r'(?mi)^\s*SHA-?256:\s*([0-9a-fA-F:]+)\s*$')
EXPECTED_TEST_CLASS = 'dev.devdigi.music.realinstance.RealInstanceAuthenticationTest'


def stop(label, code=2):
    print(label, flush=True)
    raise SystemExit(code)


def execute(args, *, input_text=None, timeout=240):
    try:
        return subprocess.run(
            args, input=input_text, capture_output=True, text=True, timeout=timeout,
            env={**os.environ, 'LC_ALL': 'C', 'LANG': 'C'}, check=False,
        )
    except (OSError, subprocess.TimeoutExpired):
        stop('LOCAL_SIGNING_TOOL=BLOCKED')


def normalized_digest(raw):
    cleaned = raw.replace(':', '').lower()
    return cleaned if re.fullmatch(r'[0-9a-f]{64}', cleaned) else None


def apk_digest(apksigner, apk):
    result = execute([str(apksigner), 'verify', '--print-certs', str(apk)], timeout=60)
    if result.returncode:
        stop('APK_SIGNATURE=FAIL', 1)
    digests = {
        normalized_digest(match.group(1))
        for match in APK_CERT.finditer(result.stdout)
    }
    digests.discard(None)
    if len(digests) != 1:
        stop('APK_SIGNER_COUNT=REQUIRES_REVIEW')
    return next(iter(digests))


def rc_reference(directory, apksigner, aapt):
    manifest = directory / 'release-sha256.txt'
    if manifest.is_symlink() or not manifest.is_file():
        stop('RC_MANIFEST=BLOCKED')
    try:
        lines = manifest.read_text(encoding='utf-8').splitlines()
    except (OSError, UnicodeError):
        stop('RC_MANIFEST=BLOCKED')
    apks = []
    for line in lines:
        match = MANIFEST_ENTRY.fullmatch(line.strip())
        if not match:
            stop('RC_MANIFEST_FORMAT=BLOCKED')
        if match.group(2).lower().endswith('.apk'):
            apks.append((match.group(2), match.group(1).lower()))
    if len(apks) != 1:
        stop('RC_APK_SELECTION=BLOCKED')
    name, expected = apks[0]
    apk = directory / name
    if apk.is_symlink() or not apk.is_file():
        stop('RC_APK=BLOCKED')
    with apk.open('rb') as stream:
        if hashlib.file_digest(stream, 'sha256').hexdigest() != expected:
            stop('RC_INTEGRITY=FAIL', 1)
    if apk_package(aapt, apk) != ('dev.devdigi.music', '1', '0.1.0'):
        stop('RC_PACKAGE_VERSION=BLOCKED')
    return apk, expected, apk_digest(apksigner, apk)


def apk_package(aapt, apk):
    result = execute([str(aapt), 'dump', 'badging', str(apk)], timeout=45)
    if result.returncode:
        stop('APK_MANIFEST=BLOCKED')
    found = re.search(r"(?m)^package:\s+name='([^']+)'[^\n]*", result.stdout)
    if not found:
        stop('APK_PACKAGE=BLOCKED')
    line = found.group(0)
    def attr(name):
        match = re.search(re.escape(name) + r"='([^']*)'", line)
        return match.group(1) if match else ''
    return attr('name'), attr('versionCode'), attr('versionName')


def instrumentation_manifest(aapt, apk):
    tree = execute([str(aapt), 'dump', 'xmltree', str(apk), 'AndroidManifest.xml'])
    if tree.returncode:
        stop('TEST_MANIFEST=BLOCKED')
    nodes = re.findall(r'E:\s*instrumentation\b(.*?)(?=\n\s*E:|\Z)', tree.stdout, re.S)
    if len(nodes) != 1:
        stop('TEST_INSTRUMENTATION_COUNT=BLOCKED')
    node = nodes[0]
    # aapt formats can use both the resource ID and the Raw: rendering.
    target = re.search(r'targetPackage[^\n]*?"([^"\n]+)"', node)
    runner = re.search(r'android:name[^\n]*?"([^"\n]+)"', node)
    if not target or target.group(1) != 'dev.devdigi.music':
        stop('TEST_INSTRUMENTATION_TARGET=BLOCKED')
    if not runner or runner.group(1) != 'androidx.test.runner.AndroidJUnitRunner':
        stop('TEST_INSTRUMENTATION_RUNNER=BLOCKED')


def private_candidates(home, repo):
    root = home / '.local'
    if not root.is_dir() or root.is_symlink():
        return []
    candidates = []
    for current, dirs, files in os.walk(root, followlinks=False):
        folder = Path(current)
        depth = len(folder.relative_to(home).parts)
        dirs[:] = [name for name in dirs if name not in EXCLUDED and depth < 6
                   and not (folder / name).is_symlink()]
        for name in files:
            path = folder / name
            if path.suffix.lower() not in EXTENSIONS or name.lower() == 'debug.keystore':
                continue
            try:
                if path.is_symlink() or not path.is_file():
                    continue
                info = path.stat()
                if (not stat.S_ISREG(info.st_mode) or info.st_uid != os.getuid()
                        or stat.S_IMODE(info.st_mode) & 0o077
                        or path.resolve().is_relative_to(repo)):
                    continue
                candidates.append(path)
            except OSError:
                continue
    return sorted(candidates)


def matching_private_aliases(keytool_output, expected):
    matching = []
    for match in re.finditer(r'(?m)^Alias name:\s*(.+?)\s*$', keytool_output):
        start = match.end()
        following = re.search(r'(?m)^Alias name:', keytool_output[start:])
        block = keytool_output[start:start + following.start()] if following else keytool_output[start:]
        if not ENTRY.search(block):
            continue
        first = FIRST_CERT.search(block)
        if first is None:
            continue
        tail = block[first.end():]
        later = re.search(r'(?m)^\s*Certificate\[\d+\]:\s*$', tail)
        if later:
            tail = tail[:later.start()]
        digest_match = KEY_SHA.search(tail)
        if digest_match and normalized_digest(digest_match.group(1)) == expected:
            matching.append(match.group(1))
    return matching



def sign_failure_category(raw):
    """Return only a fixed non-secret category. Never expose apksigner output."""
    low = raw.lower()
    if re.search(r'unrecoverablekeyexception|cannot recover key|unable to recover key|key password.{0,32}(?:incorrect|wrong|invalid)|failed to decrypt', low):
        return 'PRIVATE_KEY_PASSWORD'
    if re.search(r'keystore.{0,50}(?:password|tampered|integrity)|(?:password|integrity).{0,50}keystore', low):
        return 'KEYSTORE_AUTH'
    if re.search(r'outofmemoryerror|java heap space|gc overhead', low):
        return 'JAVA_MEMORY'
    if re.search(r'zipalign|not zip.aligned|invalid apk|malformed apk|zip file|zipexception', low):
        return 'APK_FORMAT'
    if re.search(r'permission denied|read.only file system|no such file or directory', low):
        return 'FILESYSTEM'
    return 'UNCLASSIFIED'

def password_prompt(prompt):
    with warnings.catch_warnings():
        warnings.simplefilter('error', getpass.GetPassWarning)
        try:
            return getpass.getpass(prompt)
        except (EOFError, OSError, getpass.GetPassWarning):
            stop('INTERACTIVE_PASSWORD=BLOCKED')


def git_guard(repo):
    head = execute(['git', '-C', str(repo), 'rev-parse', 'HEAD'])
    if head.returncode or not re.fullmatch(r'[a-f0-9]{40}\s*', head.stdout):
        stop('SOURCE_COMMIT=BLOCKED')
    source = head.stdout.strip()
    branch = execute(['git', '-C', str(repo), 'branch', '--show-current'])
    if branch.stdout.strip() != 'test/95-wu1-rc-smoke-runner':
        stop('SOURCE_BRANCH=BLOCKED')
    ancestry = execute(['git', '-C', str(repo), 'merge-base', '--is-ancestor', RELEASE_BASE, source])
    if ancestry.returncode:
        stop('RC_SOURCE_BASELINE=BLOCKED')
    production = execute(['git', '-C', str(repo), 'diff', '--quiet', RELEASE_BASE, source, '--', 'app/src/main'])
    if production.returncode:
        stop('RC_PRODUCTION_SOURCE_DRIFT=BLOCKED')
    local = execute(['git', '-C', str(repo), 'status', '--porcelain', '--', 'app/src/main', 'app/src/androidTest'])
    expected = 'M app/src/androidTest/java/dev/devdigi/music/realinstance/RealInstanceRuntimeInput.kt'
    if local.returncode or local.stdout.strip() != expected:
        stop('ANDROID_SOURCE_WORKTREE=BLOCKED')
    bridge = repo / 'app/src/androidTest/java/dev/devdigi/music/realinstance/RealInstanceRuntimeInput.kt'
    if 'readInstrumentationPrivateInput(instrumentation)' not in bridge.read_text(encoding='utf-8'):
        stop('ANDROID_TEST_BRIDGE=BLOCKED')
    diff = execute(['git', '-C', str(repo), 'diff', '--no-ext-diff', '--', str(bridge.relative_to(repo))])
    if diff.returncode or not diff.stdout or len(diff.stdout) > 24000:
        stop('ANDROID_TEST_DIFF=BLOCKED')
    return source, hashlib.sha256(diff.stdout.encode('utf-8')).hexdigest()


def main():
    os.umask(0o077)
    print('===== MUSIC-95 LOCAL RC1 INSTRUMENTATION SIGNING =====')
    if not sys.stdin.isatty():
        stop('INTERACTIVE_TERMINAL=REQUIRED')
    repo = Path(__file__).resolve().parent.parent
    if not (repo / 'gradlew').is_file() or not (repo / 'tools/rc-smoke.sh').is_file():
        stop('REPOSITORY_LAYOUT=BLOCKED')
    source, test_patch_sha = git_guard(repo)
    sdk = Path(os.environ.get('ANDROID_SDK_ROOT') or os.environ.get('ANDROID_HOME') or str(Path.home() / 'Android/Sdk'))
    available = sorted((p for p in (sdk / 'build-tools').glob('*/apksigner') if p.is_file() and os.access(p, os.X_OK)),
                       key=lambda p: tuple(int(v) if v.isdecimal() else v for v in re.split(r'(\d+)', p.parent.name)))
    if not available:
        stop('APKSIGNER=BLOCKED')
    signer = available[-1]
    aapt = signer.parent / 'aapt'
    if not aapt.is_file() or not os.access(aapt, os.X_OK):
        stop('AAPT=BLOCKED')
    rc_dir = Path(os.environ.get('DEVDIGI_RC_ARTIFACT_DIR') or (Path.home() / 'Descargas/devdigi-music-rc1'))
    _, rc_sha, rc_cert = rc_reference(rc_dir, signer, aapt)
    print('RC_ARTIFACT_SHA_AND_SIGNATURE=PASS')
    print('RC_PACKAGE_VERSION=PASS')
    out_dir = repo / 'app/build/outputs/rc-smoke'
    out_apk = out_dir / 'devdigi-music-rc1-test-signed.apk'
    out_sha = out_dir / 'rc-test-sha256.txt'
    out_provenance = out_dir / 'rc-test-provenance.txt'
    if any(p.exists() or p.is_symlink() for p in (out_apk, out_sha, out_provenance)):
        stop('SIGNED_TEST_OUTPUT_ALREADY_EXISTS')
    if (out_dir.is_symlink() or (out_dir.exists() and not out_dir.is_dir())
            or not out_dir.resolve().is_relative_to(repo.resolve())):
        stop('SIGNED_TEST_OUTPUT_DIR=BLOCKED')
    print('--- build credential-free AndroidTest APK (no device install) ---')
    build = execute([str(repo / 'gradlew'), ':app:assembleDebugAndroidTest', '--no-daemon'], timeout=600)
    if build.returncode:
        stop('ANDROID_TEST_BUILD=FAIL', 1)
    # Abort if reviewed AndroidTest sources changed while Gradle ran.
    if git_guard(repo)[1] != test_patch_sha:
        stop('ANDROID_TEST_DIFF_CHANGED=BLOCKED')
    source_apks = sorted((repo / 'app/build/outputs/apk/androidTest/debug').glob('*.apk'))
    if len(source_apks) != 1 or source_apks[0].is_symlink() or not source_apks[0].is_file():
        stop('TEST_APK_SELECTION=BLOCKED')
    test_apk = source_apks[0]
    if apk_package(aapt, test_apk)[0] != 'dev.devdigi.music.test':
        stop('TEST_APK_PACKAGE=BLOCKED')
    instrumentation_manifest(aapt, test_apk)
    # Verify the debug input is a well-formed signed APK before re-signing it.
    apk_digest(signer, test_apk)
    print('ANDROID_TEST_BUILD=PASS')
    print('TEST_PACKAGE_AND_INSTRUMENTATION=PASS')
    candidates = private_candidates(Path.home().resolve(), repo.resolve())
    print('PRIVATE_CANDIDATES=' + str(len(candidates)))
    for i, candidate in enumerate(candidates, 1):
        print('CANDIDATE_' + str(i) + '=PRIVATE_' + candidate.suffix.lstrip('.').upper())
    if not candidates:
        stop('PRIVATE_KEYSTORE=BLOCKED')
    try:
        selected = input('Selecciona keystore verificado (0 cancela): ').strip()
    except EOFError:
        stop('CANDIDATE_SELECTION=BLOCKED')
    if not selected.isdecimal():
        stop('CANDIDATE_SELECTION=INVALID')
    number = int(selected)
    if number == 0:
        stop('SIGNING=CANCELLED', 0)
    if not 1 <= number <= len(candidates):
        stop('CANDIDATE_SELECTION=INVALID')
    try:
        consent = input('Escribe SIGN-TEST-ONLY para firmar solo el APK de pruebas: ').strip()
    except EOFError:
        stop('LOCAL_SIGNING_CONFIRMATION=BLOCKED')
    if consent != 'SIGN-TEST-ONLY':
        stop('LOCAL_SIGNING_CONFIRMATION=BLOCKED')
    keystore = candidates[number - 1]
    storepass = password_prompt('Contraseña del keystore (oculta): ')
    if not storepass:
        stop('KEYSTORE_PASSWORD=BLOCKED')
    inspect = execute(['keytool', '-J-Duser.language=en', '-J-Duser.country=US', '-list', '-v',
                       '-keystore', str(keystore)], input_text=storepass + '\n', timeout=60)
    if inspect.returncode or re.search(r'integrity.*not been verified|keystore was tampered',
                                       inspect.stdout + '\n' + inspect.stderr, re.I):
        stop('KEYSTORE_AUTHENTICATION=BLOCKED')
    aliases = matching_private_aliases(inspect.stdout, rc_cert)
    if len(aliases) != 1:
        stop('MATCHING_PRIVATE_KEY_ENTRY=BLOCKED')
    print('MATCHING_PRIVATE_KEY_ENTRY=PASS')
    keypass = password_prompt('Contraseña de clave (Enter = misma que keystore): ')
    if not keypass:
        keypass = storepass
    print('--- sign ONLY instrumentation APK ---')
    out_dir.mkdir(mode=0o700, parents=True, exist_ok=True)
    tempdir = Path(tempfile.mkdtemp(prefix='.local-sign-', dir=out_dir))
    try:
        temp_apk = tempdir / 'test-signed.apk'
        signed = execute([
            str(signer), 'sign', '--ks', str(keystore), '--ks-key-alias', aliases[0],
            '--ks-pass', 'stdin', '--key-pass', 'stdin',
            '--in', str(test_apk), '--out', str(temp_apk),
        ], input_text=storepass + '\n' + keypass + '\n', timeout=120)
        if signed.returncode or not temp_apk.is_file():
            if not signed.returncode and not temp_apk.is_file():
                reason = 'OUTPUT_MISSING'
            else:
                reason = sign_failure_category(signed.stdout + '\n' + signed.stderr)
            print('LOCAL_SIGNING_FAILURE_CATEGORY=' + reason)
            print('LOCAL_SIGNING_EXIT_NONZERO=' + ('YES' if signed.returncode else 'NO'))
            stop('LOCAL_TEST_SIGNING=FAIL', 1)
        temp_apk.chmod(0o600)
        if apk_digest(signer, temp_apk) != rc_cert:
            stop('SIGNED_TEST_CERTIFICATE_MISMATCH', 1)
        if apk_package(aapt, temp_apk)[0] != 'dev.devdigi.music.test':
            stop('SIGNED_TEST_PACKAGE=FAIL', 1)
        instrumentation_manifest(aapt, temp_apk)
        with temp_apk.open('rb') as stream:
            test_sha = hashlib.file_digest(stream, 'sha256').hexdigest()
        (tempdir / 'sha.txt').write_text(test_sha + '  ' + out_apk.name + '\n')
        (tempdir / 'provenance.txt').write_text(
            'source_commit=' + source + '\n' +
            'android_test_local_diff_sha256=' + test_patch_sha + '\n' +
            'rc_source_baseline=' + RELEASE_BASE + '\n' +
            'rc_artifact_sha256=' + rc_sha + '\n' +
            'signed_test_sha256=' + test_sha + '\n' +
            'test_package=dev.devdigi.music.test\n' +
            'target_package=dev.devdigi.music\n' +
            'local_only=yes\n'
        )
        for staged, destination in ((temp_apk, out_apk),
                                    (tempdir / 'sha.txt', out_sha),
                                    (tempdir / 'provenance.txt', out_provenance)):
            try:
                os.link(staged, destination)  # fail closed; never overwrite
            except FileExistsError:
                stop('OUTPUT_ALREADY_EXISTS')
    finally:
        storepass = None
        keypass = None
        shutil.rmtree(tempdir)
    print('LOCAL_TEST_SIGNING=PASS')
    print('SIGNED_TEST_CERT_MATCH=PASS')
    print('TEST_APK_PROVENANCE=PASS')
    print('RC1_APP_RESIGNED=NO')
    print('ADB_OPERATIONS=NONE')
    print('JENKINS_CHANGED=NO')
    print('N8N_CHANGED=NO')
    print('GIT_COMMIT=NO')
    print('GIT_PUSH=NO')
    print('AGILETEST_PUBLISHED=NO')
    print('NEXT=CONFIGURE_LOCAL_RC_SMOKE_ENV_AND_REVIEW_DEVICE_PREFLIGHT')


if __name__ == '__main__':
    try:
        main()
    except KeyboardInterrupt:
        print('\nLOCAL_SIGNING=CANCELLED')
        sys.exit(130)
    except (OSError, ValueError, UnicodeError):
        print('LOCAL_SIGNING=BLOCKED')
        sys.exit(2)
