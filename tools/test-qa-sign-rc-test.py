#!/usr/bin/env python3
"""Synthetic tests of the local-only signed AndroidTest workflow; no real signing."""

import importlib.util
from pathlib import Path
import stat
import subprocess
import tempfile
import unittest
from unittest import mock

SCRIPT = Path(__file__).with_name('qa-sign-rc-test.py')
spec = importlib.util.spec_from_file_location('qa_sign_test', SCRIPT)
qa = importlib.util.module_from_spec(spec)
spec.loader.exec_module(qa)
SHA = 'a' * 64


class SigningContract(unittest.TestCase):
    def setUp(self):
        self.work = tempfile.TemporaryDirectory()
        self.addCleanup(self.work.cleanup)
        self.root = Path(self.work.name)

    def test_digest_normalize_colon_hex(self):
        self.assertEqual(qa.normalized_digest(':'.join(['aa'] * 32)), 'aa' * 32)

    def test_digest_refuses_invalid(self):
        self.assertIsNone(qa.normalized_digest('nonhex'))
        self.assertIsNone(qa.normalized_digest('ab' * 31))

    def test_manifest_path_rejects_parent_traversal(self):
        self.assertIsNone(qa.MANIFEST_ENTRY.fullmatch(SHA + '  ../../release.apk'))

    def test_manifest_path_accepts_safe_filename(self):
        self.assertIsNotNone(qa.MANIFEST_ENTRY.fullmatch(SHA + '  app-release.apk'))

    def test_one_matching_private_entry(self):
        output = (
            'Alias name: release-key\n'
            'Entry type: PrivateKeyEntry\n'
            'Certificate[1]:\n'
            'Owner: Test\n'
            'SHA256: ' + ':'.join(['AA'] * 32) + '\n'
            'Alias name: root-ca\n'
            'Entry type: trustedCertEntry\n'
            'Certificate[1]:\n'
            'SHA256: ' + ':'.join(['AA'] * 32) + '\n'
        )
        self.assertEqual(qa.matching_private_aliases(output, SHA), ['release-key'])

    def test_reject_cert_entry_without_private_key(self):
        output = 'Alias name: trusted\nEntry type: trustedCertEntry\nCertificate[1]:\nSHA256: ' + SHA + '\n'
        self.assertEqual(qa.matching_private_aliases(output, SHA), [])

    def test_reject_chain_certificate_not_leaf(self):
        output = ('Alias name: test\nEntry type: PrivateKeyEntry\n'
                  'Certificate[1]:\nSHA256: ' + ('b' * 64) + '\n'
                  'Certificate[2]:\nSHA256: ' + SHA + '\n')
        self.assertEqual(qa.matching_private_aliases(output, SHA), [])

    def test_private_candidate_excludes_public_and_debug(self):
        home = self.root / 'home'
        private = home / '.local/keys/private.jks'
        private.parent.mkdir(parents=True)
        private.write_bytes(b'fake')
        private.chmod(0o600)
        public = private.with_name('world.jks')
        public.write_bytes(b'fake')
        public.chmod(0o644)
        debug = private.with_name('debug.keystore')
        debug.write_bytes(b'fake')
        debug.chmod(0o600)
        self.assertEqual(qa.private_candidates(home, home / 'my-repo'), [private])

    def test_excludes_symlink_keystore(self):
        home = self.root / 'home'
        folder = home / '.local'
        folder.mkdir(parents=True)
        orig = folder / 'original.jks'
        orig.write_bytes(b'fake')
        orig.chmod(0o600)
        (folder / 'link.jks').symlink_to(orig)
        self.assertEqual(qa.private_candidates(home, home / 'repo'), [orig])

    @mock.patch.object(qa, 'execute')
    def test_apk_signer_one_cert(self, execute):
        execute.return_value = mock.Mock(returncode=0, stdout='Signer #1 certificate SHA-256 digest: ' + SHA)
        self.assertEqual(qa.apk_digest('apksigner', 'app.apk'), SHA)

    @mock.patch.object(qa, 'execute')
    def test_apk_signer_multiple_certs_rejected(self, execute):
        execute.return_value = mock.Mock(returncode=0, stdout=(
            'Signer #1 certificate SHA-256 digest: ' + SHA + '\n'
            'Signer #2 certificate SHA-256 digest: ' + 'b' * 64))
        with self.assertRaises(SystemExit):
            qa.apk_digest('apksigner', 'app.apk')

    @mock.patch.object(qa, 'execute')
    def test_instrumentation_package_and_runner(self, execute):
        execute.return_value = mock.Mock(returncode=0, stdout=(
            'E: manifest\n'
            '  E: instrumentation\n'
            '    A: android:name(0x01010003)="androidx.test.runner.AndroidJUnitRunner"\n'
            '    A: android:targetPackage(0x01010021)="dev.devdigi.music"\n'))
        qa.instrumentation_manifest('aapt', 'test.apk')

    @mock.patch.object(qa, 'execute')
    def test_instrumentation_wrong_target_refused(self, execute):
        execute.return_value = mock.Mock(returncode=0, stdout=(
            'E: manifest\n'
            '  E: instrumentation\n'
            '    A: android:name(0x01010003)="androidx.test.runner.AndroidJUnitRunner"\n'
            '    A: android:targetPackage(0x01010021)="other.application"\n'))
        with self.assertRaises(SystemExit):
            qa.instrumentation_manifest('aapt', 'test.apk')

    def clean_git_fixture(self):
        repo = self.root / 'repo'
        repo.mkdir()
        def git(*args):
            return subprocess.check_output(['git', '-C', str(repo), *args], text=True).strip()
        git('init', '-q', '-b', 'test/95-wu1-rc-smoke-runner')
        git('config', 'user.email', 'qa@example.invalid')
        git('config', 'user.name', 'QA')
        main = repo / 'app/src/main/Main.kt'
        main.parent.mkdir(parents=True)
        main.write_text('production-baseline')
        git('add', '.')
        git('commit', '-qm', 'RC baseline')
        baseline = git('rev-parse', 'HEAD')
        test = repo / 'app/src/androidTest/java/dev/devdigi/music/realinstance/RealInstanceRuntimeInput.kt'
        test.parent.mkdir(parents=True)
        test.write_text('readInstrumentationPrivateInput(instrumentation)')
        for name in ('app/build.gradle.kts', 'gradle/libs.versions.toml'):
            p = repo / name
            p.parent.mkdir(parents=True, exist_ok=True)
            p.write_text('reviewed-test-dependencies')
        git('add', '.')
        git('commit', '-qm', 'add instrumentation')
        return repo, baseline, git

    def test_git_guard_clean_revision_and_fingerprint(self):
        repo, baseline, git = self.clean_git_fixture()
        with mock.patch.object(qa, 'RELEASE_BASE', baseline):
            head, fingerprint = qa.git_guard(repo)
            self.assertEqual(head, git('rev-parse', 'HEAD'))
            self.assertRegex(fingerprint, r'^[0-9a-f]{64}$')
            self.assertEqual(qa.git_guard(repo), (head, fingerprint))
            git('checkout', '-qb', 'develop')
            self.assertEqual(qa.git_guard(repo), (head, fingerprint))

    def test_git_guard_refuses_uncommitted_inputs(self):
        repo, baseline, _ = self.clean_git_fixture()
        with mock.patch.object(qa, 'RELEASE_BASE', baseline):
            for name in ('app/build.gradle.kts',
                         'app/src/androidTest/java/dev/devdigi/music/realinstance/RealInstanceRuntimeInput.kt'):
                path = repo / name
                original = path.read_text()
                path.write_text(original + '\nextra')
                with self.assertRaises(SystemExit):
                    qa.git_guard(repo)
                path.write_text(original)
            (repo / 'untracked-secret-file').write_text('not-allowed')
            with self.assertRaises(SystemExit):
                qa.git_guard(repo)

    def test_git_guard_refuses_committed_production_drift(self):
        repo, baseline, git = self.clean_git_fixture()
        (repo / 'app/src/main/Main.kt').write_text('modified-production')
        git('add', '.')
        git('commit', '-qm', 'unexpected production change')
        with mock.patch.object(qa, 'RELEASE_BASE', baseline):
            with self.assertRaises(SystemExit):
                qa.git_guard(repo)

    def test_jenkins_does_not_archive_privileged_test_apk(self):
        jenkins = (SCRIPT.parent.parent / 'Jenkinsfile').read_text()
        self.assertNotIn("artifacts: 'app/build/outputs/rc-smoke/*.apk", jenkins)
        self.assertIn("artifacts: 'app/build/outputs/rc-smoke/*.txt'", jenkins)
        self.assertIn("sh 'rm -f -- app/build/outputs/rc-smoke/*.apk'", jenkins)

    def test_no_release_apk_mutation_in_script(self):
        code = SCRIPT.read_text()
        self.assertIn("'--in', str(test_apk)", code)
        self.assertIn("'--out', str(temp_apk)", code)
        self.assertIn("'--ks-pass', 'stdin'", code)
        self.assertIn("'--key-pass', 'stdin'", code)
        self.assertNotIn('adb install', code)
        self.assertNotIn('gradlew assembleRelease', code)

    def test_no_secret_output_in_provenance(self):
        code = SCRIPT.read_text()
        self.assertNotIn('key_alias=', code)
        self.assertNotIn('keystore_path=', code)
        self.assertNotIn('store_password=', code)
        self.assertNotIn('key_password=', code)



    def test_sign_error_safe_password_classification(self):
        self.assertEqual(qa.sign_failure_category('java.security.UnrecoverableKeyException: Cannot recover key'), 'PRIVATE_KEY_PASSWORD')

    def test_sign_error_safe_keystore_classification(self):
        self.assertEqual(qa.sign_failure_category('Keystore was tampered with, or password was incorrect'), 'KEYSTORE_AUTH')

    def test_sign_error_safe_apk_classification(self):
        self.assertEqual(qa.sign_failure_category('zipalign: APK is not zip-aligned'), 'APK_FORMAT')

    def test_sign_error_never_exposes_sensitive_text(self):
        secret = 'pass=TOP_SECRET username=PRIVATE path=/private/key.jks'
        category = qa.sign_failure_category(secret)
        self.assertEqual(category, 'UNCLASSIFIED')
        self.assertNotIn('TOP_SECRET', category)
        self.assertNotIn('/private/', category)

    def test_sign_error_safe_filesystem_and_memory_classification(self):
        self.assertEqual(qa.sign_failure_category('Permission denied'), 'FILESYSTEM')
        self.assertEqual(qa.sign_failure_category('java.lang.OutOfMemoryError: Java heap space'), 'JAVA_MEMORY')


if __name__ == '__main__':
    unittest.main(verbosity=2)
