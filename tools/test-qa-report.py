#!/usr/bin/env python3
"""Contract tests: synthetic instrumentation logs only; no ADB or network."""
import importlib.util
from pathlib import Path
import tempfile
import unittest
import xml.etree.ElementTree as ET

MODULE = Path(__file__).with_name("qa-report.py")
spec = importlib.util.spec_from_file_location("devdigi_qa_report", MODULE)
qa = importlib.util.module_from_spec(spec)
spec.loader.exec_module(qa)
SOURCE_SHA = "6fae53aa7d9085c94ca11f177c9de4205d1be412"


class LocalSmokeContract(unittest.TestCase):
    def test_rc_smoke_entrypoint_warns_about_saved_server_precondition(self):
        script = (MODULE.parent / "qa").read_text()
        warning = "SMOKE_PRECONDITION=SIGNED_OUT_AND_NO_SAVED_SERVER"
        self.assertEqual(script.count(warning), 1)
        self.assertIn("AUTH_SERVER_ENABLE_FAILED", script)
        self.assertIn("Delete server", script)
        self.assertIn("adb pm clear", script)
        self.assertIn("manual preflight", script)
        self.assertLess(script.index(warning), script.index("RUN_ID="))
        self.assertIn("RC=0", script)
        self.assertIn("bash \"$ROOT/tools/rc-smoke.sh\"", script)
        self.assertNotIn("pm clear dev.devdigi.music", script)

    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.log = Path(self.temp.name) / "instrumentation.log"

    def evidence(self, keys, success=False, extra=""):
        self.log.write_text(
            "\n".join(f"INSTRUMENTATION_STATUS: devdigi.rc.case={key}" for key in keys)
            + ("\nOK (1 test)\n" if success else "\nFAILURES!!!\n")
            + extra
        )
        return str(self.log)

    def reduce(self, status, keys=(), stage="UNCLASSIFIED", success=False, extra=""):
        return qa.reduce_result(status, stage, self.evidence(keys, success, extra), SOURCE_SHA)

    def statuses(self, report):
        return {case["key"]: case["status"] for case in report["cases"]}

    def test_full_13_pass(self):
        report = self.reduce("PASS", qa.EXPECTED, success=True)
        self.assertEqual(report["status"], "PASS")
        self.assertEqual(report["counts"]["PASS"], 13)
        self.assertEqual(report["publication"], "DRY_RUN_ONLY")
        root = ET.fromstring(qa.xml_report(report))
        self.assertEqual(root.get("tests"), "13")
        self.assertEqual(root.get("failures"), "0")
        self.assertEqual(root.get("skipped"), "0")

    def test_claimed_pass_missing_marker_fails_closed(self):
        report = self.reduce("PASS", qa.EXPECTED[:-1], success=True)
        self.assertEqual(report["status"], "FAIL")
        self.assertEqual(report["counts"]["PASS"], 0)

    def test_fail_preserves_verified_prefix(self):
        report = self.reduce("FAIL", qa.EXPECTED[:2], "RECENT_ALBUMS_FAILED")
        results = self.statuses(report)
        self.assertEqual(results["MUSIC-64"], "PASS")
        self.assertEqual(results["MUSIC-65"], "PASS")
        self.assertEqual(results["MUSIC-72"], "FAIL")
        self.assertEqual(results["MUSIC-75"], "NOT RUN")
        self.assertEqual(report["counts"]["NOT RUN"], 10)
        root = ET.fromstring(qa.xml_report(report))
        self.assertEqual(root.get("failures"), "2")  # case + harness

    def test_preflight_blocked(self):
        report = qa.reduce_result("BLOCKED", "RC_APK", "", SOURCE_SHA)
        self.assertEqual(report["counts"]["BLOCKED"], 13)
        self.assertEqual(report["status"], "BLOCKED")

    def test_unknown_case_blocks_marker_trust(self):
        report = self.reduce("FAIL", ["MUSIC-64", "MUSIC-999"], "UNCLASSIFIED")
        self.assertEqual(report["counts"]["PASS"], 0)
        self.assertEqual(report["status"], "FAIL")

    def test_duplicate_case_blocks_marker_trust(self):
        report = self.reduce("FAIL", ["MUSIC-64", "MUSIC-64"], "UNCLASSIFIED")
        self.assertEqual(report["counts"]["PASS"], 0)

    def test_wrong_order_blocks_marker_trust(self):
        report = self.reduce("FAIL", ["MUSIC-65", "MUSIC-64"], "UNCLASSIFIED")
        self.assertEqual(report["counts"]["PASS"], 0)

    def test_unknown_fail_stage_does_not_invent_case_fail(self):
        report = self.reduce("FAIL", qa.EXPECTED[:2], "PROBE_EXECUTION_FAILED")
        self.assertEqual(report["counts"]["FAIL"], 0)
        self.assertEqual(report["counts"]["PASS"], 2)

    def test_never_persist_secret_from_raw_log(self):
        report = self.reduce("FAIL", qa.EXPECTED[:2], "RECENT_ALBUMS_FAILED", extra="secret-PRIVATE-URL\n")
        report_bytes = (str(report) + qa.xml_report(report).decode()).encode()
        self.assertNotIn(b"secret-PRIVATE-URL", report_bytes)

    def test_path_symlink_refused(self):
        actual = Path(self.temp.name) / "private"
        actual.write_text("should-not-overwrite")
        alias = Path(self.temp.name) / "alias"
        alias.symlink_to(actual)
        with self.assertRaises(ValueError):
            qa.atomic_write(alias, b"altered")
        self.assertEqual(actual.read_text(), "should-not-overwrite")

    def test_invalid_source_commit_refused(self):
        with self.assertRaises(ValueError):
            qa.reduce_result("BLOCKED", "", "", "not-a-commit")

    def test_junit_case_identity_matches_preexisting_rc_smoke(self):
        report = self.reduce("PASS", qa.EXPECTED, success=True)
        root = ET.fromstring(qa.xml_report(report))
        observed = [(n.attrib["classname"], n.attrib["name"]) for n in root.findall("testcase")]
        expected = [
            ("devdigi.music.rc.smoke", f"{key} — {title}")
            for key, title in qa.CASES
        ]
        self.assertEqual(observed, expected)
        self.assertEqual(observed[0][1], "MUSIC-64 — Saving a compatible server does not authenticate the user")
        self.assertEqual(observed[-1][1], "MUSIC-69 — Sign out clears authenticated presentation state")

    def test_unreached_later_stage_does_not_invent_failure(self):
        report = self.reduce("FAIL", qa.EXPECTED[:2], "SIGN_OUT_RUNTIME_CLEAR_FAILED")
        self.assertEqual(report["counts"]["FAIL"], 0)
        self.assertEqual(self.statuses(report)["MUSIC-89"], "NOT RUN")

    def test_presentation_failure_can_be_attributed_before_runtime_clear(self):
        report = self.reduce("FAIL", qa.EXPECTED[:11], "SIGN_OUT_PRESENTATION_CLEAR_FAILED")
        self.assertEqual(self.statuses(report)["MUSIC-69"], "FAIL")
        self.assertEqual(self.statuses(report)["MUSIC-89"], "NOT RUN")

    def test_blocked_distinct_from_not_run_in_json_and_junit(self):
        report = qa.reduce_result("BLOCKED", "RC_APK", "", SOURCE_SHA)
        self.assertEqual(report["counts"]["BLOCKED"], 13)
        root = ET.fromstring(qa.xml_report(report))
        self.assertEqual(len(root.findall("testcase/skipped")), 14)
        # Standard JUnit cannot distinguish blocked from skipped reliably.
        # AgileTest will require an explicit mapping from the JSON per-case state.


if __name__ == "__main__":
    unittest.main(verbosity=2)
