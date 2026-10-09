#!/usr/bin/env python3
"""Regression for nested Android adb shell/run-as command quoting. No ADB."""
from pathlib import Path
import os
import shlex
import stat
import subprocess
import tempfile
import unittest

SCRIPT = Path(__file__).with_name('rc-smoke.sh')
PACKAGE = 'dev.devdigi.music.test'
WRITE_COMMAND = (
    f"run-as {PACKAGE} sh -c 'umask 077; mkdir -p files; "
    "cat > files/real_instance_input.json'"
)
DELETE_COMMAND = (
    f"run-as {PACKAGE} sh -c 'rm -f files/real_instance_input.json'"
)


class AdbRemoteShellContract(unittest.TestCase):
    def test_stage_command_preserves_one_shell_program(self):
        args = shlex.split(WRITE_COMMAND)
        self.assertEqual(args[:4], ['run-as', PACKAGE, 'sh', '-c'])
        self.assertEqual(len(args), 5)
        self.assertEqual(
            args[4],
            'umask 077; mkdir -p files; cat > files/real_instance_input.json',
        )

    def test_script_contains_correct_nested_quoting(self):
        source = SCRIPT.read_text()
        self.assertEqual(source.count('"' + WRITE_COMMAND.replace(PACKAGE, '$TEST_APP_ID') + '"'), 1)
        self.assertEqual(source.count('"' + DELETE_COMMAND.replace(PACKAGE, '$TEST_APP_ID') + '"'), 2)
        self.assertIn('shell -T', source)

    def test_no_broken_split_shell_c(self):
        source = SCRIPT.read_text()
        self.assertNotIn('sh -c \\\n        \'umask 077;', source)
        self.assertNotIn('sh -c     \'rm -f files/', source)

    def test_emulated_android_shell_stages_private_input_and_deletes(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            app_dir = root / 'app'
            app_dir.mkdir()
            bin_dir = root / 'bin'
            bin_dir.mkdir()
            fake_run_as = bin_dir / 'run-as'
            fake_run_as.write_text(
                '#!/bin/sh\n'
                'test "$1" = "dev.devdigi.music.test" || exit 8\n'
                'shift\n'
                'exec "$@"\n'
            )
            fake_run_as.chmod(0o700)
            env = dict(os.environ, PATH=str(bin_dir) + ':' + os.environ.get('PATH', ''))
            sample = '{"status":"synthetic-only"}'
            staged = subprocess.run(
                ['sh', '-c', WRITE_COMMAND], input=sample,
                text=True, capture_output=True, cwd=app_dir, env=env,
            )
            self.assertEqual(staged.returncode, 0, staged.stderr)
            private_file = app_dir / 'files' / 'real_instance_input.json'
            self.assertEqual(private_file.read_text(), sample)
            self.assertEqual(stat.S_IMODE(private_file.stat().st_mode), 0o600)
            deleted = subprocess.run(
                ['sh', '-c', DELETE_COMMAND], capture_output=True,
                text=True, cwd=app_dir, env=env,
            )
            self.assertEqual(deleted.returncode, 0, deleted.stderr)
            self.assertFalse(private_file.exists())

    def test_release_target_is_never_installed_by_smoke(self):
        source = SCRIPT.read_text()
        self.assertIn("APP_ID='dev.devdigi.music'", source)
        self.assertIn("TEST_APP_ID='dev.devdigi.music.test'", source)
        self.assertIn('RC_INSTALLED_ARTIFACT=PASS', source)
        self.assertIn('RC_APP_REPLACED=NO', source)


if __name__ == '__main__':
    unittest.main(verbosity=2)
