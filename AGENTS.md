# Gauguin — Codex development instructions

## Mission and source of truth
Gauguin is an Android Calcudoku/KenKen-style puzzle project. This repository contains imported upstream source, logic tests and experimental development work. Preserve the original creator's gameplay intentions while improving mathematical correctness, reliability, performance and maintainability.

Read `README.md`, `audit/SOURCE.md`, relevant tests and workflow files before implementing anything. The repository and passing tests—not conversational summaries—are the authority for what is actually implemented. Separate verified facts, user requirements, experimental ideas and assumptions in reports.

ChatGPT is the project discussion, education and decision-making workspace; Codex is the code investigation and implementation specialist; GitHub/GitHub Actions is the shared, auditable record.

## Scope and non-negotiable project constraints
- Target puzzle sizes **3×3 through 11×11**, not 12×12.
- Preserve valid cage geometry, permitted arithmetic expressions, row/column uniqueness and mathematically sound puzzle constraints. Validate the allowed numeral domain for each experimental integer mode; **do not assume all modes use 1..N**.
- Aim for **exactly one solution per accepted generated puzzle**; do not weaken uniqueness as a workaround.
- Accepted puzzles must be solvable with the intended **human-deduction-only** difficulty policy. Do not silently substitute brute-force completion or unbounded guessing for human-solvability evidence.
- Preserve working gameplay, particularly **pencil marks**. Avoid modifying gameplay/UI unless explicitly required and covered by regression tests.
- Use the displayed difficulty name **Very Hard** (and `very_hard` for newly standardized identifiers), not Extreme. Treat migration of existing persisted identifiers as a compatibility issue requiring explicit review.
- Respect the project's intended candidate-combination restriction: no candidate cage combination with an individual numeral occurring more than twice, where that rule is applicable. Verify current implementation and tests rather than assuming enforcement.
- Respect the intended Nishio policy: bounded to **10 moves** in the discussed experimental configuration; it should not by itself count as satisfying human-deduction solvability unless used together with another human strategy. Confirm semantics and current implementation before modifying scoring or acceptance.
- Preserve all existing verified solver strategies; the historical target is **37 strategies**, but do not claim they are all correct without per-strategy evidence.

## Current investigations, not blanket implementation instructions
These are topics for hypothesis testing, not permission to rewrite the generator:
- Reproduce the **11×11 seed 1** pass/fail regression and distinguish randomness, configuration, strategy/scoring, timeouts and actual logic errors.
- Test difficulty-by-size matrices and numeral-domain variations, including **0–9** experiments when mathematically valid.
- Evaluate a potential adaptive/self-correcting generator loop against the existing pipeline. Measure attempts, latency, memory, distribution, uniqueness and human-solver success before changing architecture.
- Consider efficiency improvements to pruning, randomization and diagnostics only with correctness-preserving evidence.
- Arithmetic distribution and difficulty calibration targets have evolved; inspect current configuration and report observed behavior before changing thresholds, percentages or attempt caps.

## Working rules: TAFT
1. **Test:** Record the baseline: exact repository, branch, commit, command, size, difficulty, numeral set, seed, timeout and failure type.
2. **Investigate:** Reproduce deterministically when possible. Trace the earliest violated invariant. Compare multiple feasible fixes for correctness, sustainability and performance.
3. **Apply fix:** Work on an **isolated Codex branch**. Prefer the most effective mathematically sound and sustainable solution, not necessarily the smallest patch. Explain tradeoffs.
4. **Test again:** Run focused regressions first, then broaden the matrix according to risk and resource constraints. Distinguish tests executed from tests merely proposed.
5. **Report:** Summarize what failed, why, which files changed, the actual test results, remaining risks and next recommended test.

Do not modify or commit to `main`, `experiment/autonomous-generation-lab`, or other ongoing automation branches as part of Codex setup/investigation. Do not merge or publish an APK without an explicit task authorizing it. Leave existing scheduled automations and workflow dispatch behavior unchanged unless specifically requested.

## Build, tests and evidence
According to this repository's current `README.md`, the imported upstream code is in `upstream/gauguin` and the documented baseline uses **JDK 21**:

```sh
cd upstream/gauguin
./gradlew :gauguin-core:test :gauguin-human-solver:test --no-daemon --stacktrace -Pbuildserver
```

Check whether that command and toolchain still apply to the checked-out commit before running it. Keep original-version compatibility separate from experimental build modernization. Do not assume this test repository can compile an installable APK: its README explicitly says the existing workflows do **not** build or publish APKs, and unit tests alone do not establish Android gameplay compatibility or prove all strategies correct.

Prefer diagnostic logs that record actionable failures, capped artifacts and reproducible seeds; avoid runaway multi-gigabyte logs. Parallelize only independent tests, with measured resource limits.

## Communication and learning
Explain **what changed and why**, in accessible terms, so a developer learning as they go can follow. Preserve an engineering audit trail (commit, test command/results, and links when available). State uncertainty clearly, challenge assumptions, and recommend next steps based on evidence.

When the user assigns only a setup or investigation task, **do not implement functional changes** until the requested scope is clear.
