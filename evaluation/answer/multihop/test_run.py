import json
import tempfile
import unittest
from pathlib import Path
import run


class ScoringTest(unittest.TestCase):
    def setUp(self):
        self.q = {"id": "q", "split": "FORMAL", "type": "inference_query", "answer": "Google",
                  "facts": [{"id": "f1", "alternatives": [[1, 2]]}]}
        self.row = {"arm": "VECTOR", "evidence": [{"id": "E1", "text": "Google spent money.", "chunks": [1, 2]}]}
        self.value = {"answered": True, "answer": "Google", "explanation": "Supported.",
                      "citations": [{"evidenceId": "E1", "quote": "Google spent money."}], "reason": ""}

    def result(self, value=None):
        return {"status": "OK", "finishReason": "stop", "content": json.dumps(value or self.value), "elapsedMs": 10}

    def test_normalization(self):
        self.assertEqual(run.normalize("The GOOGLE!"), "google")
        self.assertEqual(run.normalize("Ｇｏｏｇｌｅ"), "google")
        self.assertNotEqual(run.normalize("yes"), run.normalize("no"))

    def test_exact_supported_proxy(self):
        score = run.score(self.q, self.row, self.result())
        self.assertTrue(score["exact"])
        self.assertTrue(score["referenceSupportedExactProxy"])
        self.assertEqual(score["semanticReview"], "PENDING")

    def test_partial_span_not_supported(self):
        self.row["evidence"][0]["chunks"] = [1]
        self.assertFalse(run.score(self.q, self.row, self.result())["referenceSupportedExactProxy"])

    def test_invalid_citation_not_supported(self):
        self.value["citations"][0]["evidenceId"] = "E99"
        score = run.score(self.q, self.row, self.result())
        self.assertTrue(score["exact"])
        self.assertEqual(score["validCitationCount"], 0)
        self.assertFalse(score["referenceSupportedExactProxy"])

    def test_fabricated_or_empty_quote(self):
        for quote in ("", "Google never spent money."):
            self.value["citations"][0]["quote"] = quote
            self.assertEqual(run.score(self.q, self.row, self.result())["validCitationCount"], 0)

    def test_abstention(self):
        value = {"answered": False, "answer": "", "explanation": "", "citations": [], "reason": "Missing evidence"}
        self.assertFalse(run.score(self.q, self.row, self.result(value))["exact"])
        self.q["type"] = "null_query"
        self.q["facts"] = []
        score = run.score(self.q, self.row, self.result(value))
        self.assertTrue(score["exact"])
        self.assertFalse(score["referenceSupportedExactProxy"])

    def test_failure_and_truncation(self):
        for patch in ({"status": "HTTP_ERROR"}, {"finishReason": "length"}, {"content": "not json"}):
            result = self.result() | patch
            self.assertIsNone(run.parse(result))
            self.assertFalse(run.score(self.q, self.row, result)["exact"])

    def test_schema_types_and_empty_answers(self):
        for patch in ({"answered": "false"}, {"answer": ""}, {"citations": []}, {"extra": 1}, {"reason": "contradictory"}):
            self.assertIsNone(run.parse(self.result(self.value | patch)))

    def test_f1_and_semantic_alias_not_silently_accepted(self):
        self.assertEqual(run.token_f1("Google", "Google"), 1)
        self.assertEqual(run.token_f1("no", "yes"), 0)
        self.value["answer"] = "Alphabet's Google"
        self.assertFalse(run.score(self.q, self.row, self.result())["exact"])

    def test_immutable_write(self):
        with tempfile.TemporaryDirectory() as folder:
            path = Path(folder) / "test.json"
            run.write_new(path, {"a": 1})
            with self.assertRaises(FileExistsError):
                run.write_new(path, {"a": 2})
            self.assertEqual(run.read(path), {"a": 1})

    def test_aggregates_keep_failure_denominators(self):
        good = run.score(self.q, self.row, self.result())
        bad = run.score(self.q, self.row, self.result() | {"status": "HTTP_ERROR"})
        summary = run.summarize([good, bad])
        self.assertEqual(summary["em"], .5)
        self.assertEqual(summary["schemaFailures"], 1)
        self.assertIsNone(summary["humanVerifiedGroundedAccuracy"])

    def test_request_does_not_include_labels(self):
        payload = run.body({"query": "Question?", "context": "Evidence", "answer": "SECRET_LABEL", "arm": "SECRET_ARM"})
        self.assertNotIn("SECRET", json.dumps(payload))
        self.assertEqual(payload["model"], "deepseek-flash")


if __name__ == "__main__":
    unittest.main()
