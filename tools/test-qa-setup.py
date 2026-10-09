#!/usr/bin/env python3
"""Offline contracts for local QA credential/path bootstrap. No device/network."""
import importlib.util
import os
from pathlib import Path
import shlex
import stat
import tempfile
import unittest
from unittest import mock

SCRIPT = Path(__file__).with_name('qa-setup.py')
spec = importlib.util.spec_from_file_location('devdigi_qa_setup', SCRIPT)
qa = importlib.util.module_from_spec(spec)
spec.loader.exec_module(qa)


class QaSetupContracts(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.root = Path(self.tmp.name)

    def test_private_storage_must_not_be_inside_repository(self):
        with mock.patch.object(qa, 'CONFIG_HOME', SCRIPT.parent / 'private-env'):
            with self.assertRaises(ValueError):
                qa.require_external_private_storage()
        with mock.patch.object(qa, 'CONFIG_HOME', Path('relative-env')):
            with self.assertRaises(ValueError):
                qa.require_external_private_storage()
        with mock.patch.object(qa, 'PRIVATE_APK', SCRIPT.parent / 'signed-test.apk'):
            with self.assertRaises(ValueError):
                qa.require_external_private_storage()

    def test_private_creation_no_overwrite(self):
        dest = self.root / 'private' / 'values.env'
        qa.atomic_new(dest, b'PRIVATE=YES\n')
        self.assertTrue(qa.private_file(dest))
        self.assertEqual(stat.S_IMODE(dest.stat().st_mode), 0o600)
        with self.assertRaises(ValueError):
            qa.atomic_new(dest, b'PRIVATE=NO\n')
        self.assertEqual(dest.read_bytes(), b'PRIVATE=YES\n')

    def test_reject_symlink(self):
        original = self.root / 'original'
        original.write_text('private')
        alias = self.root / 'alias'
        alias.symlink_to(original)
        self.assertFalse(qa.private_file(alias))
        with self.assertRaises(ValueError):
            qa.atomic_new(alias, b'bad')

    def test_quote_private_input_without_execution(self):
        url = 'https://example.invalid/path?x=$(touch /tmp/should-not-run)'
        username = "reader'with spaces"
        password = 'secret\"; export LEAK=YES'
        text = qa.navidrome_text(url, username, password).decode()
        for k, v in [('DEVDIGI_NAVIDROME_LOCAL_URL', url),
                     ('DEVDIGI_NAVIDROME_USER', username),
                     ('DEVDIGI_NAVIDROME_PASSWORD', password)]:
            self.assertIn(f'export {k}={shlex.quote(v)}', text)
        self.assertNotIn('RELEASE_STORE_PASSWORD', text)
        with self.assertRaises(ValueError):
            qa.navidrome_text('http://example.invalid', username, password)

    def test_manifest_unique_apk(self):
        root = self.root / 'release'
        root.mkdir()
        (root / 'release-sha256.txt').write_text('a' * 64 + '  app-release.apk\n')
        (root / 'app-release.apk').write_bytes(b'rc')
        apk, manifest = qa.manifest_apk(root)
        self.assertEqual(apk.name, 'app-release.apk')
        self.assertEqual(manifest.name, 'release-sha256.txt')
        (root / 'release-sha256.txt').write_text('a' * 64 + '  app-release.apk\n' + 'b' * 64 + '  other.apk\n')
        with self.assertRaises(ValueError):
            qa.manifest_apk(root)

    def test_signed_test_integrity_and_private_import(self):
        import hashlib
        src_dir = self.root / 'build'
        src_dir.mkdir()
        apk = src_dir / 'devdigi-music-rc1-test-signed.apk'
        apk.write_bytes(b'SYNTHETIC-APK')
        digest = hashlib.sha256(apk.read_bytes()).hexdigest()
        (src_dir / 'rc-test-sha256.txt').write_text(digest + '  ' + apk.name + '\n')
        self.assertEqual(qa.verified_signed_test(apk), digest)
        private = self.root / 'private' / apk.name
        self.assertEqual(qa.import_signed_test(apk, private), private)
        self.assertTrue(qa.private_file(private))
        self.assertTrue(qa.private_file(private.parent / 'rc-test-sha256.txt'))
        self.assertEqual(qa.verified_signed_test(private), digest)
        self.assertEqual(qa.import_signed_test(apk, private), private)
        apk.write_bytes(b'TAMPERED')
        with self.assertRaises(ValueError):
            qa.import_signed_test(apk, private)

    def test_config_is_data_only(self):
        content = qa.config_text('/tmp/app.apk', '/tmp/manifest.txt', '/tmp/test.apk', '/tmp/secrets.env').decode()
        self.assertEqual(len(content.splitlines()), 4)
        self.assertNotIn('PASSWORD', content)
        with self.assertRaises(ValueError):
            qa.safe_path('relative/file')

    def test_one_command_entrypoint_and_no_automatic_signing(self):
        src = (SCRIPT.parent / 'qa').read_text()
        self.assertIn("SMOKE_PRECONDITION=SIGNED_OUT_AND_NO_SAVED_SERVER", src)
        self.assertIn('qa-setup.py', src)
        self.assertIn('sanity)', src)
        self.assertIn('regression)', src)
        self.assertIn('smoke)', src)
        self.assertNotIn('python3 tools/qa-sign-rc-test.py', src)
        self.assertNotIn('pm clear dev.devdigi.music', src)


if __name__ == '__main__':
    unittest.main(verbosity=2)
