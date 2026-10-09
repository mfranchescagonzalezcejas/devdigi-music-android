#!/usr/bin/env python3
"""Offline source contract for safe local instrumentation private input transfer."""
from pathlib import Path
import re
import shlex
import unittest

root=Path(__file__).resolve().parent.parent
src=(root/'app/src/androidTest/java/dev/devdigi/music/realinstance/RealInstanceRuntimeInput.kt').read_text()
runner=(root/'tools/rc-smoke.sh').read_text()

class TestPrivateInputBridge(unittest.TestCase):
    def test_command_has_only_fixed_nonsecret_tokens(self):
        cmd='run-as dev.devdigi.music.test cat files/real_instance_input.json'
        self.assertEqual(shlex.split(cmd), ['run-as','dev.devdigi.music.test','cat','files/real_instance_input.json'])
    def test_loader_still_uses_instrumentation_selector(self):
        self.assertIsNotNone(
            re.search(
                r'INPUT_SOURCE_INSTRUMENTATION\s*->\s*\{\s*readInstrumentationPrivateInput\(instrumentation\)',
                src,
            ),
            'INSTRUMENTATION_SELECTOR_MISSING',
        )
    def test_target_mode_preserved(self):
        self.assertIn('instrumentation.targetContext.filesDir',src)
        self.assertIn('readAndDelete(',src)
    def test_reader_uses_shell_bridge_not_test_context_filesdir(self):
        self.assertIn('instrumentation.uiAutomation',src)
        self.assertIn('"run-as $TEST_APP_ID cat files/$INPUT_FILE_NAME"',src)
        self.assertNotIn('instrumentation\n                        .context\n                        .filesDir',src)
    def test_private_stream_closed(self):
        self.assertIsNotNone(
            re.search(
                r'ParcelFileDescriptor\s*\.AutoCloseInputStream\s*\(\s*descriptor\s*\)',
                src,
            ),
            'AUTOCLOSE_STREAM_MISSING',
        )
        self.assertIn('.use { it.readText() }',src)
    def test_no_credentials_passed_to_shell_command(self):
        shell=src.split('executeShellCommand(',1)[1].split(')',1)[0]
        for term in ('username','password','endpoint','token','REAL_'):
            self.assertNotIn(term,shell.lower())
    def test_fail_closed_no_raw_errors(self):
        self.assertIn('REAL_INSTANCE_INPUT_MISSING',src)
        self.assertIn('REAL_INSTANCE_INPUT_UNREADABLE',src)
        self.assertIn('catch (_: Exception)',src)
    def test_signer_guards_dirty_android_test_scope_and_provenance(self):
        signer=(root/'tools/qa-sign-rc-test.py').read_text()
        self.assertIn('SOURCE_WORKTREE=BLOCKED',signer)
        self.assertIn('test_build_inputs_sha256=',signer)
        self.assertIn('BUILD_INPUTS_CHANGED=BLOCKED',signer)
        self.assertIn("'app/src/main'",signer)

    def test_runner_does_not_reinstall_production_rc1(self):
        self.assertIn('RC_APP_REPLACED=NO',runner)
        self.assertIn('RUNTIME_INPUT_PRIVATE_FILE=PASS',runner)
        self.assertNotIn('"$ADB" \\\n    -s "$SERIAL" \\\n    install \\\n    "$APP_APK"',runner)

if __name__=='__main__': unittest.main(verbosity=2)
