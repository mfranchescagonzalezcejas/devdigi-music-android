#!/usr/bin/env python3
"""Contract tests: synthetic instrumentation logs only; no ADB or network."""
import importlib.util
from pathlib import Path
import subprocess
import tempfile
import unittest
import xml.etree.ElementTree as ET

MODULE = Path(__file__).with_name("qa-report.py")
spec = importlib.util.spec_from_file_location("devdigi_qa_report", MODULE)
qa = importlib.util.module_from_spec(spec)
spec.loader.exec_module(qa)
SOURCE_SHA = "6fae53aa7d9085c94ca11f177c9de4205d1be412"


class LocalSmokeContract(unittest.TestCase):
    def test_successful_smoke_case_marker_has_exact_stdout(self):
        script = (MODULE.parent / "rc-smoke.sh").read_text()
        command = next(
            line for line in script.splitlines()
            if line.startswith("echo 'RC_SMOKE_VERIFIED_CASES=13'")
        )
        result = subprocess.run(
            ["bash", "-c", command], check=True, capture_output=True, text=True
        )
        self.assertEqual(result.stdout, "RC_SMOKE_VERIFIED_CASES=13\n")

    def test_rc_smoke_entrypoint_warns_about_saved_server_precondition(self):
        script = (MODULE.parent / "qa").read_text()
        warning = "SMOKE_PRECONDITION=SIGNED_OUT_WITH_SAME_OR_EMPTY_SERVER"
        self.assertEqual(script.count(warning), 1)
        self.assertIn("MUSIC-64 is BLOCKED", script)
        self.assertIn("DIFFERENT server", script)
        self.assertIn("adb pm clear", script)
        self.assertIn("Full 13/13 requires", script)
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

    def test_saved_server_reused_remains_blocked_if_later_case_fails(self):
        self.log.write_text(
            "INSTRUMENTATION_STATUS: devdigi.rc.precondition=SAVED_SERVER_REUSED\n"
            "INSTRUMENTATION_STATUS: devdigi.rc.case=MUSIC-65\n"
            "FAILURES!!!\n"
        )
        report = qa.reduce_result(
            "FAIL", "RECENT_ALBUMS_FAILED", str(self.log), SOURCE_SHA
        )
        statuses = self.statuses(report)
        self.assertEqual(report["status"], "FAIL")
        self.assertEqual(statuses["MUSIC-64"], "BLOCKED")
        self.assertEqual(statuses["MUSIC-65"], "PASS")
        self.assertEqual(statuses["MUSIC-72"], "FAIL")

    def test_mismatched_profile_is_checked_before_input_draft_mutation(self):
        src = (MODULE.parent.parent / "app/src/androidTest/java/dev/devdigi/music/realinstance/RealInstanceAuthenticationTest.kt").read_text()
        auth = src.split("    private fun authenticate(", 1)[1].split("    private fun openAlbum(", 1)[0]
        self.assertLess(auth.index("if (savedProfile != null)"),
                        auth.index("performTextReplacement("))
        self.assertIn("requireMatchingSavedProfile(savedProfile, targetEndpoint)", auth)

    def test_saved_profile_is_authoritative_before_any_url_mutation(self):
        src = (MODULE.parent.parent / "app/src/androidTest/java/dev/devdigi/music/realinstance/RealInstanceAuthenticationTest.kt").read_text()
        auth = src.split("    private fun authenticate(", 1)[1].split("    private fun openAlbum(", 1)[0]
        self.assertIn("AuthenticationProfilePrecondition.read(serverProfileRepository(context))", auth)
        self.assertLess(auth.index("AuthenticationProfilePrecondition.read(serverProfileRepository(context))"),
                        auth.index("performTextReplacement("))
        helper = (MODULE.parent.parent / "app/src/androidTest/java/dev/devdigi/music/realinstance/RealInstanceAuthenticationPreconditionTest.kt").read_text()
        self.assertIn("withTimeout(10_000L)", helper)

    def test_saved_endpoint_comparison_uses_production_canonicalization(self):
        src = (MODULE.parent.parent / "app/src/androidTest/java/dev/devdigi/music/realinstance/RealInstanceAuthenticationTest.kt").read_text()
        auth = src.split("    private fun authenticate(", 1)[1].split("    private fun openAlbum(", 1)[0]
        self.assertIn("canonicalEndpoint(endpoint)", auth)
        self.assertIn("profile.endpoint == expected", (MODULE.parent.parent / "app/src/androidTest/java/dev/devdigi/music/realinstance/RealInstanceAuthenticationPreconditionTest.kt").read_text())

    def test_fresh_save_rechecks_persisted_profile_before_click(self):
        src = (MODULE.parent.parent / "app/src/androidTest/java/dev/devdigi/music/realinstance/RealInstanceAuthenticationTest.kt").read_text()
        auth = src.split("    private fun authenticate(", 1)[1].split("    private fun openAlbum(", 1)[0]
        click = auth.index('.onNodeWithTag("connection-save-server")', auth.index("REAL_STAGE_AUTH_SERVER_SAVE_FAILED"))
        self.assertIn("savedProfile = runBlocking { AuthenticationProfilePrecondition.read(serverProfileRepository(context)) }", auth[:click])
        self.assertLess(auth.index("savedProfile = runBlocking { AuthenticationProfilePrecondition.read(serverProfileRepository(context)) }"), click)
        self.assertIn("waitUntilDisplayed", auth)

    def test_auth_precondition_android_tests_cover_delayed_flow_and_decisions(self):
        src = (MODULE.parent.parent / "app/src/androidTest/java/dev/devdigi/music/realinstance/RealInstanceAuthenticationPreconditionTest.kt").read_text()
        self.assertIn("delayedProfileEmission", src)
        self.assertIn("profileMismatch", src)
        self.assertIn("canonicalEquivalentEndpoint", src)
        self.assertIn("absentProfileAllowsFreshSave", src)
        self.assertIn("profileAppearingBeforeFinalSaveRecheck", src)

    def test_fresh_save_waits_for_matching_persisted_ui_state(self):
        src = (MODULE.parent.parent / "app/src/androidTest/java/dev/devdigi/music/realinstance/RealInstanceAuthenticationTest.kt").read_text()
        auth = src.split("    private fun authenticate(", 1)[1].split("    private fun openAlbum(", 1)[0]
        persist = auth.split("REAL_STAGE_AUTH_SERVER_PERSIST_FAILED", 1)[1]
        self.assertIn("waitForSavedProfileUi", persist)
        self.assertNotIn('.onNodeWithTag("connection-save-server")\n                        .assertIsNotEnabled()', persist)

    def test_post_save_wait_skips_initial_null_until_profile_arrives(self):
        helper = (MODULE.parent.parent / "app/src/androidTest/java/dev/devdigi/music/realinstance/RealInstanceAuthenticationPreconditionTest.kt").read_text()
        self.assertIn("nullThenProfileEmission", helper)
        self.assertIn("awaitSavedProfile", helper)
        self.assertIn("filterNotNull()", helper)

    def test_late_profile_is_blocked_without_draft_replacement_or_save(self):
        src = (MODULE.parent.parent / "app/src/androidTest/java/dev/devdigi/music/realinstance/RealInstanceAuthenticationTest.kt").read_text()
        auth = src.split("    private fun authenticate(", 1)[1].split("    private fun openAlbum(", 1)[0]
        branch = auth.split("AuthenticationProfilePrecondition.read(serverProfileRepository(context))", 2)[2]
        branch = branch.split("REAL_STAGE_AUTH_SERVER_SAVE_FAILED", 1)[0]
        self.assertNotIn("performTextReplacement", branch)
        self.assertIn("PROFILE_CHANGED_BEFORE_SAVE", branch)
        runner = (MODULE.parent.parent / "tools/rc-smoke.sh").read_text()
        self.assertIn("AUTH_SERVER_PROFILE_CHANGED_BEFORE_SAVE_FAILED", runner)
        self.assertIn("RC_SMOKE_PRECONDITION=SAVED_SERVER_CHANGED_BEFORE_SAVE", runner)
        changed_branch = runner.split("if [[ \"$FAIL_STAGE\" == 'AUTH_SERVER_PROFILE_CHANGED_BEFORE_SAVE_FAILED' ]]", 1)[1].split("fi", 1)[0]
        self.assertNotIn("RC_SMOKE_PRECONDITION=DIFFERENT_SAVED_SERVER", changed_branch)

    def test_mismatch_and_ui_sync_stages_are_distinct(self):
        src = (MODULE.parent.parent / "app/src/androidTest/java/dev/devdigi/music/realinstance/RealInstanceAuthenticationTest.kt").read_text()
        mismatch = src.split("private fun requireMatchingSavedProfile(", 1)[1].split("private fun waitForSavedProfileUi(", 1)[0]
        ui_wait = src.split("private fun waitForSavedProfileUi(", 1)[1].split("private fun openAlbum(", 1)[0]
        self.assertIn("AUTH_SERVER_PROFILE_MISMATCH_FAILED", mismatch)
        self.assertNotIn("AUTH_SERVER_PROFILE_MISMATCH_FAILED", ui_wait)
        self.assertIn("AUTH_SERVER_PROFILE_UI_SYNC_FAILED", ui_wait)
        runner = (MODULE.parent.parent / "tools/rc-smoke.sh").read_text()
        self.assertIn('if [[ "$FAIL_STAGE" == \'AUTH_SERVER_PROFILE_MISMATCH_FAILED\' ]]', runner)
        mismatch_branch = runner.split("if [[ \"$FAIL_STAGE\" == 'AUTH_SERVER_PROFILE_MISMATCH_FAILED' ]]", 1)[1].split("fi", 1)[0]
        self.assertIn("RC_SMOKE_PRECONDITION=DIFFERENT_SAVED_SERVER", mismatch_branch)
        self.assertNotIn("AUTH_SERVER_PROFILE_UI_SYNC_FAILED", mismatch_branch)

    def test_saved_server_reused_is_twelve_pass_one_blocked(self):
        self.log.write_text(
            "INSTRUMENTATION_STATUS: devdigi.rc.precondition=SAVED_SERVER_REUSED\n"
            + "\n".join(f"INSTRUMENTATION_STATUS: devdigi.rc.case={key}"
                         for key in qa.EXPECTED[1:])
            + "\nOK (1 test)\n"
        )
        report = qa.reduce_result("PASS", "", str(self.log), SOURCE_SHA)
        self.assertEqual(report["status"], "BLOCKED")
        self.assertTrue(report["saved_server_reused"])
        self.assertEqual(report["counts"]["PASS"], 12)
        self.assertEqual(report["counts"]["BLOCKED"], 1)
        self.assertEqual(self.statuses(report)["MUSIC-64"], "BLOCKED")
        self.assertEqual(self.statuses(report)["MUSIC-65"], "PASS")
        self.assertEqual(ET.fromstring(qa.xml_report(report)).get("skipped"), "2")

    def test_reused_mode_is_never_accepted_as_full_pass_without_marker(self):
        report = self.reduce("PASS", qa.EXPECTED[1:], success=True)
        self.assertEqual(report["status"], "FAIL")
        self.assertEqual(report["counts"]["PASS"], 0)

    def test_duplicate_or_late_reuse_marker_fails_closed(self):
        marker = "INSTRUMENTATION_STATUS: devdigi.rc.precondition=SAVED_SERVER_REUSED\n"
        self.evidence([], success=True, extra=marker + marker)
        with self.assertRaises(ValueError):
            qa.evidence(self.log)
        self.evidence(["MUSIC-64"], success=True, extra=marker)
        with self.assertRaises(ValueError):
            qa.evidence(self.log)

    def test_unknown_precondition_marker_fails_closed(self):
        self.evidence([], success=True,
                      extra="INSTRUMENTATION_STATUS: devdigi.rc.precondition=UNTRUSTED\n")
        with self.assertRaises(ValueError):
            qa.evidence(self.log)

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
