"""Optional recorded-artifact tests; never collect answers."""
from html.parser import HTMLParser
import unittest
import run


class Page(HTMLParser):
    def __init__(self):
        super().__init__()
        self.ids = []
        self.questions = 0
        self.script_count = 0

    def handle_starttag(self, tag, attrs):
        values = dict(attrs)
        if "id" in values:
            self.ids.append(values["id"])
        if tag == "details" and values.get("class") == "question":
            self.questions += 1
        if tag == "script":
            self.script_count += 1


@unittest.skipUnless((run.OUT / "formal-metrics.json").exists(), "Formal capture not collected yet")
class RecordedTest(unittest.TestCase):
    def test_metrics_reproduce_without_network(self):
        self.assertEqual(run.read(run.OUT / "formal-metrics.json"), run.analyze())

    def test_html_has_every_question_and_unique_anchors(self):
        for path in (run.OUT / "report.html", run.LOCAL / "review.html"):
            page = Page()
            page.feed(path.read_text(encoding="utf-8"))
            self.assertEqual(page.questions, 60)
            self.assertEqual(len(page.ids), len(set(page.ids)))
            self.assertEqual(page.script_count, 1)

    def test_four_arms_and_null_denominators(self):
        report = run.read(run.OUT / "formal-metrics.json")
        for arm in run.ARMS:
            formal = report["groups"]["FORMAL"][arm]
            self.assertEqual((formal["n"], formal["answerable"], formal["null"]), (50, 44, 6))
            self.assertEqual(report["excludingAnswerLeakingQuestion"][arm]["n"], 49)
            self.assertEqual(report["groups"]["CALIBRATION"][arm]["n"], 10)

    def test_no_paid_execution_without_consent_flag(self):
        with self.assertRaises(AssertionError):
            run.collect("FORMAL", False)


if __name__ == "__main__":
    unittest.main()
