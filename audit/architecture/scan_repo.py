#!/usr/bin/env python3
"""Read-only, reproducible *heuristic* repository survey for Gauguin.

This tool does not parse full Kotlin semantics or certify correctness.
Every flagged item requires manual verification and usually a runtime test.
"""
from __future__ import annotations

import argparse
from collections import Counter, defaultdict
from pathlib import Path
import re

RULES = (
    ("LOOP-WHILE-TRUE", "Open-ended while(true) loop; verify an inner finite budget and cancellation.",
     re.compile(r"\bwhile\s*\(\s*true\s*\)")),
    ("LOOP-RESTART", "Restart-until-success style loop; check internal bounds and repeated states.",
     re.compile(r"\}\s*while\s*\(\s*(?:restart|again|retry)\b")),
    ("RISK-FORCED-NULL", "Forced non-null assertion; verify failure path and recovery.",
     re.compile(r"!!")),
    ("STATE-GLOBAL", "Global mutable state/configuration; evaluate test isolation and concurrency.",
     re.compile(r"\b(?:System\.(?:setProperty|clearProperty)|Random\.Default)\b")),
    ("SEARCH-RANDOMIZE", "Random sampling/shuffle; evaluate deterministic reproducibility and duplicate proposals.",
     re.compile(r"\.(?:shuffled|random)\s*\(")),
    ("COST-SOLVE", "Solver invocation; profile calls inside nested candidate loops.",
     re.compile(r"\.(?:solve|solveAndCalculateDifficulty)\s*\(")),
    ("ERROR-FAIL", "Explicit runtime termination; ensure caller catches failure and resets state.",
     re.compile(r"\b(?:error|throw|check|require)\s*\(")),
    ("POLICY-SKIP", "Difficulty support/acceptance logic; verify unsupported settings cannot bypass requirements.",
     re.compile(r"\b(?:isSupported|isWantedDifficulty|avoidReveal|humanCertified)\b")),
)

def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--root", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    root = args.root.resolve()
    if not root.is_dir():
        parser.error(f"missing source root: {root}")

    candidates = sorted(
        p for p in root.rglob("*")
        if p.is_file() and p.suffix in {".kt", ".java"}
        and not any(x in p.parts for x in ("build", ".gradle", ".git"))
    )
    if not candidates:
        parser.error("no Kotlin/Java sources found")
    hits = defaultdict(list)
    categories = Counter()
    lines_total = 0
    modules = Counter()
    function_count = 0
    class_count = 0

    for path in candidates:
        relative = path.relative_to(root).as_posix()
        module = relative.split("/")[0]
        modules[module] += 1
        if "/src/test" in relative or "/src/integrationTest" in relative:
            categories["test"] += 1
        elif "/src/main/" in relative:
            categories["production"] += 1
        else:
            categories["other"] += 1
        text = path.read_text(encoding="utf-8", errors="replace")
        for n, line in enumerate(text.splitlines(), 1):
            lines_total += 1
            function_count += len(re.findall(r"\bfun\s+\w+\s*\(", line))
            class_count += len(re.findall(r"\b(?:class|interface|object)\s+\w+", line))
            for rule_id, _, pattern in RULES:
                if pattern.search(line):
                    hits[rule_id].append((relative, n, line.strip()[:155]))

    summary = [
        "# Gauguin structural scan (heuristic only)", "",
        "The entries below are **review signals, not proven bugs**. The scanner uses",
        "line-oriented matching rather than a Kotlin AST or runtime profiler.", "",
        f"- Files examined: **{len(candidates)}**",
        f"- Lines examined: **{lines_total}**",
        f"- Approximate function declarations: **{function_count}**",
        f"- Approximate class/interface/object declarations: **{class_count}**",
        f"- Production files: **{categories['production']}**; test files: **{categories['test']}**",
        "", "## Source modules", "",
    ]
    summary.extend(f"- \`{m}\`: {count} Kotlin/Java files" for m, count in sorted(modules.items()))
    summary.extend(["", "## Signals requiring review", ""])
    for rule_id, explanation, _ in RULES:
        items = hits[rule_id]
        summary.extend([f"### {rule_id} — {len(items)} matches", explanation, ""])
        if not items:
            summary.append("No matches found.\n")
            continue
        summary.extend(
            f"- \`{filename}:{line_no}\` — \`{sample.replace(chr(96), chr(39))}\`"
            for filename, line_no, sample in items[:35]
        )
        if len(items) > 35:
            summary.append(f"- ... {len(items) - 35} additional matches omitted; query the repository directly.")
        summary.append("")
    summary.extend([
        "## Interpretation and next steps", "",
        "1. Manually trace suspected critical loop/restart paths and exceptions.",
        "2. Use targeted reproducible tests and bounded instrumentation to validate each hypothesis.",
        "3. Review dependency interactions, acceptance invariants, dead branches, and state snapshots.",
        "4. Evaluate an adaptive cycler against an unchanged baseline; never infer a pass from static signals.",
        "",
        "This survey does **not** certify 37 human strategies, a unique puzzle solution,",
        "mathematical validity, or human deduction-only solvability.",
    ])
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text("\n".join(summary) + "\n", encoding="utf-8")
    print(f"Examined {len(candidates)} files / {lines_total} lines; wrote {args.output}")
    print("Rule match counts:", {rule_id: len(hits[rule_id]) for rule_id, _, _ in RULES})
    return 0

if __name__ == "__main__":
    raise SystemExit(main())
