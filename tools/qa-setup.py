#!/usr/bin/env python3
"""One-time per-machine QA configuration. Never stores a release signing key."""
import getpass
import hashlib
import os
from pathlib import Path
import re
import shlex
import shutil
import stat
import sys
import tempfile

CONFIG_HOME = Path(os.environ.get('XDG_CONFIG_HOME', str(Path.home() / '.config'))) / 'devdigi-music'
CONFIG_FILE = CONFIG_HOME / 'qa-local.env'
NAVIDROME_FILE = CONFIG_HOME / 'navidrome-test.env'
PRIVATE_APK = Path.home() / '.local/share/devdigi-music/qa/devdigi-music-rc1-test-signed.apk'
RC_DIR = Path.home() / 'Descargas/devdigi-music-rc1'
TEST_DEFAULT = Path(__file__).resolve().parent.parent / 'app/build/outputs/rc-smoke/devdigi-music-rc1-test-signed.apk'
APK_NAME = re.compile(r'[A-Za-z0-9._+-]+\.apk\Z')


def require_external_private_storage():
    """Reject relative or repository-backed private QA storage paths."""
    repository = Path(__file__).resolve().parent.parent
    for directory in (CONFIG_HOME, PRIVATE_APK.parent):
        if not directory.is_absolute() or directory.resolve().is_relative_to(repository):
            raise ValueError('QA_PRIVATE_STORAGE_UNSAFE')


def private_file(path):
    if path.is_symlink() or not path.is_file():
        return False
    st = path.stat()
    return (stat.S_ISREG(st.st_mode) and st.st_uid == os.getuid()
            and stat.S_IMODE(st.st_mode) in (0o400, 0o600) and st.st_nlink == 1)


def private_dir(path):
    if path.is_symlink() or (path.exists() and not path.is_dir()):
        raise ValueError('PRIVATE_DIR_UNSAFE')
    path.mkdir(parents=True, exist_ok=True, mode=0o700)
    if path.is_symlink() or path.stat().st_uid != os.getuid():
        raise ValueError('PRIVATE_DIR_OWNER')
    if stat.S_IMODE(path.stat().st_mode) != 0o700:
        raise ValueError('PRIVATE_DIR_PERMISSIONS')


def atomic_new(path, payload):
    """Create one private file; fail closed on existing files or symlinks."""
    private_dir(path.parent)
    if path.exists() or path.is_symlink():
        raise ValueError('PRIVATE_FILE_ALREADY_EXISTS')
    fd, temp = tempfile.mkstemp(prefix='.qa-', dir=path.parent)
    try:
        with os.fdopen(fd, 'wb') as f:
            os.fchmod(f.fileno(), 0o600)
            f.write(payload)
            f.flush()
            os.fsync(f.fileno())
        # Never overwrite after a concurrent file appeared.
        os.link(temp, path, follow_symlinks=False)
    finally:
        Path(temp).unlink(missing_ok=True)


def manifest_apk(directory):
    manifest = directory / 'release-sha256.txt'
    if manifest.is_symlink() or not manifest.is_file():
        raise ValueError('RC_MANIFEST_MISSING')
    names = []
    for line in manifest.read_text(encoding='utf-8').splitlines():
        parts = line.split()
        if len(parts) == 2 and re.fullmatch('[0-9a-fA-F]{64}', parts[0]) and APK_NAME.fullmatch(parts[1]):
            names.append(parts[1])
    if len(names) != 1:
        raise ValueError('RC_APK_NOT_UNIQUE')
    apk = directory / names[0]
    if apk.is_symlink() or not apk.is_file():
        raise ValueError('RC_APK_MISSING')
    return apk, manifest


def verified_signed_test(source):
    if source.is_symlink() or not source.is_file():
        raise ValueError('SIGNED_TEST_APK_MISSING')
    manifest = source.parent / 'rc-test-sha256.txt'
    if manifest.is_symlink() or not manifest.is_file():
        raise ValueError('SIGNED_TEST_CHECKSUM_MISSING')
    fields = manifest.read_text(encoding='utf-8').strip().split()
    if (len(fields) != 2 or not re.fullmatch('[0-9a-fA-F]{64}', fields[0])
            or fields[1] != source.name):
        raise ValueError('SIGNED_TEST_CHECKSUM_INVALID')
    if hashlib.sha256(source.read_bytes()).hexdigest() != fields[0].lower():
        raise ValueError('SIGNED_TEST_CHECKSUM_MISMATCH')
    return fields[0].lower()


def import_signed_test(source, destination=PRIVATE_APK):
    digest = verified_signed_test(source)
    private_dir(destination.parent)
    if destination.exists() or destination.is_symlink():
        if not private_file(destination):
            raise ValueError('SIGNED_TEST_CACHE_UNSAFE')
        if hashlib.sha256(destination.read_bytes()).hexdigest() != digest:
            raise ValueError('SIGNED_TEST_CACHE_DIFFERENT')
        receipt = destination.parent / 'rc-test-sha256.txt'
        if not receipt.exists():
            atomic_new(receipt, (digest + '  ' + destination.name + '\n').encode('utf-8'))
        elif not private_file(receipt) or receipt.read_text().strip() != digest + '  ' + destination.name:
            raise ValueError('SIGNED_TEST_RECEIPT_UNSAFE')
        return destination
    fd, temp = tempfile.mkstemp(prefix='.signed-test-', dir=destination.parent)
    try:
        with os.fdopen(fd, 'wb') as handle, source.open('rb') as inp:
            os.fchmod(handle.fileno(), 0o600)
            shutil.copyfileobj(inp, handle)
            handle.flush()
            os.fsync(handle.fileno())
        if hashlib.sha256(Path(temp).read_bytes()).hexdigest() != digest:
            raise ValueError('SIGNED_TEST_COPY_MISMATCH')
        os.link(temp, destination, follow_symlinks=False)
    finally:
        Path(temp).unlink(missing_ok=True)
    receipt = destination.parent / 'rc-test-sha256.txt'
    if not receipt.exists():
        atomic_new(receipt, (digest + '  ' + destination.name + '\n').encode('utf-8'))
    elif not private_file(receipt):
        raise ValueError('SIGNED_TEST_RECEIPT_UNSAFE')
    elif receipt.read_text(encoding='utf-8').strip() != digest + '  ' + destination.name:
        raise ValueError('SIGNED_TEST_RECEIPT_MISMATCH')
    return destination


def safe_path(raw):
    if not raw or '\n' in raw or '\r' in raw or not Path(raw).is_absolute():
        raise ValueError('QA_PATH_INVALID')
    return raw


def config_text(rc_apk, manifest, signed_test, nav_env):
    items = {
        'DEVDIGI_RC_APK': rc_apk,
        'DEVDIGI_RC_SHA256_FILE': manifest,
        'DEVDIGI_RC_TEST_APK': signed_test,
        'DEVDIGI_NAVIDROME_ENV': nav_env,
    }
    return ''.join(f'{k}={safe_path(str(v))}\n' for k, v in items.items()).encode('utf-8')


def navidrome_text(url, username, password):
    if not url.startswith('https://') or not username or not password:
        raise ValueError('NAVIDROME_INPUT_INVALID')
    if any('\x00' in x or '\n' in x or '\r' in x for x in (url, username, password)):
        raise ValueError('NAVIDROME_INPUT_INVALID')
    return ''.join(f'export {k}={shlex.quote(v)}\n' for k, v in (
        ('DEVDIGI_NAVIDROME_LOCAL_URL', url),
        ('DEVDIGI_NAVIDROME_USER', username),
        ('DEVDIGI_NAVIDROME_PASSWORD', password),
    )).encode('utf-8')


def prompt(question, default):
    return input(f'{question} [{default}]: ').strip() or str(default)


def main():
    os.umask(0o077)
    require_external_private_storage()
    print('===== DEVDIGI MUSIC QA: ONE-TIME MACHINE SETUP =====')
    print('PRIVATE_FILES=OUTSIDE_REPOSITORY')
    print('RELEASE_KEYSTORE_PASSWORD_STORAGE=FORBIDDEN')
    if CONFIG_FILE.exists() or CONFIG_FILE.is_symlink():
        if not private_file(CONFIG_FILE):
            raise ValueError('QA_CONFIG_UNSAFE')
        print('QA_CONFIG=ALREADY_PRESENT')
    else:
        directory = Path(safe_path(prompt('Approved RC1 artifact directory', RC_DIR))).expanduser()
        rc_apk, manifest = manifest_apk(directory)
        proposed = PRIVATE_APK if PRIVATE_APK.is_file() else TEST_DEFAULT
        test_input = Path(safe_path(prompt('Existing signed AndroidTest APK', proposed)))
        digest = verified_signed_test(test_input)
        if test_input != PRIVATE_APK:
            answer = input('Copy signed driver to private persistent cache (0600)? [yes/no]: ').strip().lower()
            if answer != 'yes':
                print('SIGNED_TEST_APK=USING_EXISTING_LOCAL_PATH')
                signed_test = test_input
            else:
                signed_test = import_signed_test(test_input)
                print('SIGNED_TEST_APK=PRIVATE_CACHE_READY')
        else:
            signed_test = test_input
        if not digest:
            raise ValueError('SIGNED_TEST_NOT_VERIFIED')
        atomic_new(CONFIG_FILE, config_text(rc_apk, manifest, signed_test, NAVIDROME_FILE))
        print('QA_CONFIG=READY')
    if NAVIDROME_FILE.exists() or NAVIDROME_FILE.is_symlink():
        if not private_file(NAVIDROME_FILE):
            raise ValueError('NAVIDROME_ENV_UNSAFE')
        print('NAVIDROME_PRIVATE_INPUT=REUSED')
    else:
        print('Private Navidrome URL, username and password can be saved locally.')
        print('SECURITY_WARNING=PLAINTEXT_AT_REST_CHMOD_0600')
        agree = input('Store Navidrome test credentials in your local private file? [yes/no]: ').strip().lower()
        if agree != 'yes':
            raise ValueError('NAVIDROME_CREDENTIAL_PERSISTENCE_NOT_APPROVED')
        url = getpass.getpass('Navidrome HTTPS URL (hidden): ')
        username = getpass.getpass('Navidrome username (hidden): ')
        password = getpass.getpass('Navidrome password (hidden): ')
        atomic_new(NAVIDROME_FILE, navidrome_text(url, username, password))
        del password
        print('NAVIDROME_PRIVATE_INPUT=READY')
    print('QA_SETUP=PASS')
    print('NEXT=./tools/qa smoke')


if __name__ == '__main__':
    try:
        main()
    except (OSError, ValueError, EOFError, KeyboardInterrupt):
        # Never echo private paths or secret inputs on error.
        print('QA_SETUP=BLOCKED', file=sys.stderr)
        sys.exit(2)
