#!/usr/bin/env python3
"""Synthetic, local, no-device diagnosis tests for private instrumentation output."""
import importlib.util
from pathlib import Path
import tempfile
import unittest

path = Path(__file__).with_name('qa-instrumentation-diagnose.py')
spec = importlib.util.spec_from_file_location('diag', path)
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)


class TestSanitizedDiagnostic(unittest.TestCase):
    def test_missing_file(self):
        self.assertEqual(module.classify('REAL_INSTANCE_INPUT_MISSING')[0], 'INPUT_FILE_MISSING')

    def test_invalid_file(self):
        self.assertEqual(module.classify('REAL_INSTANCE_INPUT_INVALID')[0], 'INPUT_JSON_INVALID')

    def test_instrumentation_framework_failure(self):
        self.assertEqual(module.classify('INSTRUMENTATION_FAILED')[0], 'INSTRUMENTATION_START_FAILED')

    def test_class_loading_failure(self):
        self.assertEqual(module.classify('java.lang.ClassNotFoundException')[0], 'CLASS_LOAD_FAILURE')

    def test_binary_incompatibility(self):
        self.assertEqual(module.classify('java.lang.NoSuchMethodError')[0], 'BINARY_INCOMPATIBILITY')

    def test_stage_and_verified_marker(self):
        reason, count = module.classify('REAL_STAGE_AUTH_SERVER_SAVE_FAILED\nINSTRUMENTATION_STATUS: devdigi.rc.case=MUSIC-64')
        self.assertEqual((reason, count), ('REPORTED_FUNCTIONAL_STAGE', 1))

    def test_unclassified_is_privacy_safe(self):
        secret = 'https://private.test/a?token=secret username=person password=hello'
        result = module.classify(secret)
        self.assertEqual(result, ('UNCLASSIFIED', 0))
        self.assertNotIn('secret', str(result))

    def test_source_not_persist_raw_data(self):
        source = path.read_text()
        self.assertNotIn('print(content)', source)
        self.assertNotIn('print(path)', source)

    def test_emitted_reason_never_contains_credentials(self):
        for marker, _ in module.RULES:
            label = module.classify(marker.pattern)[0]
            self.assertNotIn('PASSWORD=', label)


if __name__ == '__main__':
    unittest.main(verbosity=2)
