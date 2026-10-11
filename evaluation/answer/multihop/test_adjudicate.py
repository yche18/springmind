import copy
from html.parser import HTMLParser
import unittest
from unittest.mock import patch
import adjudicate
import run


class AdjudicationTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.raw = run.read(run.OUT / "formal-metrics.json")
        cls.decisions = run.read(adjudicate.DECISIONS)
        cls.result = adjudicate.analyze(cls.raw, cls.decisions)

    def test_exact_expected_counts_and_denominators(self):
        for arm, count in zip(run.ARMS, (26, 26, 28, 28)):
            s = self.result["groups"]["FORMAL"][arm]
            self.assertEqual((s["n"], s["answerable"], s["null"], s["correctNullRefusals"]), (49, 43, 6, 6))
            self.assertEqual(s["correct"], count)
            self.assertEqual(s["answerableCorrect"], count - 6)
            self.assertEqual(s["adjustedScore"], count / 49)
            self.assertIsNone(s["fullyHumanVerifiedAccuracy"])
            self.assertIsNone(s["fullyHumanVerifiedGroundedAccuracy"])

    def test_decisions_apply_symmetrically_and_keep_refusals_wrong(self):
        rows = {(r["id"], r["arm"]): r for r in self.result["rows"]}
        for arm in run.ARMS:
            self.assertTrue(rows["m0735", arm]["adjustedCorrect"])
            self.assertFalse(rows["m1727", arm]["adjustedCorrect"])
            self.assertTrue(rows["m2398", arm]["excluded"])
            self.assertIsNone(rows["m2398", arm]["adjustedCorrect"])
            self.assertEqual(rows["m1594", arm]["adjustedCorrect"], arm == "VECTOR")
            self.assertEqual(rows["m1748", arm]["adjustedCorrect"], arm != "RRF")

    def test_original_fields_preserved_and_other_scores_unchanged(self):
        edits = {d["id"] for d in self.decisions["questions"]}
        for old, new in zip(self.raw["rows"], self.result["rows"]):
            for key, value in old.items():
                self.assertEqual(new[key], value)
            if old["id"] not in edits:
                self.assertEqual(new["adjustedCorrect"], old["exact"])
                self.assertEqual(new["decisionSource"], "UNCHANGED_AUTOMATIC_SCORE")
                self.assertIsNone(new["adjudicatedCoreGrounding"])
        self.assertEqual(self.raw, run.read(run.OUT / "formal-metrics.json"))

    def test_grounding_never_replaces_verbatim_checks(self):
        rows = {(r["id"], r["arm"]): r for r in self.result["rows"]}
        for arm in run.ARMS:
            r = rows["m0735", arm]
            self.assertEqual(r["adjudicatedCoreGrounding"], "YES")
            self.assertLess(r["validCitationCount"], r["citationCount"])
        self.assertEqual(rows["m1594", "VECTOR"]["adjudicatedCoreGrounding"], "NO")

    def test_pairwise_accounting_and_sensitivity(self):
        for key, stats in self.result["paired"].items():
            self.assertEqual(len(stats["gains"]) + len(stats["losses"]) + stats["ties"], 49)
        self.assertAlmostEqual(self.result["paired"]["VECTOR -> RRF_QWEN"]["percentagePointChange"], 200 / 49)
        self.assertEqual(self.result["paired"]["VECTOR -> RRF"]["percentagePointChange"], 0)
        for arm, count in zip(run.ARMS, (26, 26, 28, 28)):
            keep = self.result["groups"]["KEEP_AMBIGUOUS_ORIGINAL_SCORE"][arm]
            self.assertEqual((keep["n"], keep["correct"]), (50, count))
            sensitivity = self.result["groups"]["EXCLUDE_ANSWER_LEAKING_M2521"][arm]
            self.assertEqual((sensitivity["n"], sensitivity["correct"]), (48, count - 1))

    def test_no_network_and_exact_recorded_regeneration(self):
        with patch("urllib.request.OpenerDirector.open", side_effect=AssertionError("Network forbidden")), patch.object(run, "collect", side_effect=AssertionError("Collection forbidden")):
            self.assertEqual(adjudicate.analyze(self.raw, self.decisions), run.read(adjudicate.DEST / "metrics.json"))
            for path, detailed in ((adjudicate.DEST / "report.html", False), (adjudicate.LOCAL_PAGE, True)):
                self.assertEqual(adjudicate.render(self.result, detailed), path.read_text(encoding="utf-8"))

    def test_original_and_derived_hashes(self):
        verification = run.read(adjudicate.DEST / "verification.json")
        self.assertEqual(verification["newModelCalls"], 0)
        self.assertEqual(verification["implementationSha256"], run.filehash(adjudicate.Path(adjudicate.__file__)))
        for group in ("originalHashesPreserved", "derivedHashes"):
            for name, value in verification[group].items():
                self.assertEqual(run.filehash(run.ROOT / name), value)

    def test_review_page_keeps_decisions_read_only(self):
        class Page(HTMLParser):
            def __init__(self):
                super().__init__(); self.ids = []; self.questions = []; self.editable = []
            def handle_starttag(self, tag, attrs):
                a = dict(attrs)
                if "id" in a: self.ids.append(a["id"])
                if tag == "details" and a.get("class") == "question": self.questions.append(a)
                if "data-key" in a: self.editable.append(a["data-key"])
        page = Page(); page.feed(adjudicate.LOCAL_PAGE.read_text(encoding="utf-8"))
        self.assertEqual(len(page.questions), 60)
        self.assertEqual(len(page.ids), len(set(page.ids)))
        self.assertEqual(sum(q["data-decided"] == "true" for q in page.questions), 5)
        edited = {q["id"] for q in self.decisions["questions"]}
        self.assertTrue(all(key.split('-')[0] not in edited for key in page.editable))
        self.assertEqual(self.result["pendingFlagged"], {"FORMAL": {"questions": 24, "responses": 96}, "CALIBRATION": {"questions": 5, "responses": 20}})


if __name__ == "__main__":
    unittest.main()
