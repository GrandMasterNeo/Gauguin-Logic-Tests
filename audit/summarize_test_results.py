"""Summarize Gradle JUnit XML without treating absent reports as passing tests."""
import json
from pathlib import Path
from xml.etree import ElementTree

root = Path(__file__).resolve().parents[1]
summary = {}
for module in ("gauguin-core", "gauguin-human-solver"):
    reports = sorted((root / "upstream/gauguin" / module / "build/test-results/test").glob("TEST-*.xml"))
    totals = {key: 0 for key in ("tests", "failures", "errors", "skipped")}
    failures = []
    regressions = 0
    for report in reports:
        suite = ElementTree.parse(report).getroot()
        for key in totals:
            totals[key] += int(suite.get(key, 0))
        for case in suite.findall("testcase"):
            if case.get("classname", "").endswith(".LogicSafetyRegressionTest"):
                regressions += 1
            for failure in [*case.findall("failure"), *case.findall("error")]:
                failures.append({"class": case.get("classname"), "test": case.get("name"),
                                 "message": failure.get("message"), "report": report.name})
    summary[module] = {"reports": len(reports), "status": "reported" if reports else "NOT RUN",
                       **totals, "added_regression_tests": regressions, "failure_details": failures}
print(json.dumps(summary, indent=2))
