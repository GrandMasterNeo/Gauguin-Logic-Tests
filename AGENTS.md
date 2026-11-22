# Gauguin architecture audit — Codex instructions

## Mission
Perform a repository-wide, evidence-first inspection of the current Gauguin experimental codebase. Investigate loops, unreachable paths, generation dead ends, contradictory acceptance conditions, seed determinism, duplicated work, performance bottlenecks, and interactions between puzzle generation, uniqueness checking, difficulty rating, and the 37 human-solver strategies.

The repository is the primary source of truth. Start with `audit/architecture/README.md`, `README.md`, `audit/SOURCE.md`, production source under `upstream/gauguin`, and the existing GitHub Actions failures.

## Strict isolation
- Work **only** on `experiment/architecture-audit` or a child audit branch.
- Do not commit to `main`, `experiment/autonomous-generation-lab`, `experiment/personal-logic-integration`, or the original upstream branch.
- Do not modify or disable scheduled automations, production gameplay, or pencil marks.
- This is an audit-first assignment. No generator rewrite or difficulty calibration changes until the audit explains a reproducible root cause and compares alternatives.
- No APK publication or automatic merge.

## Required investigation
1. Inventory all source and test modules, dependencies, difficulty gates, solver strategy registry, mutable state, recursive paths, and background jobs.
2. Trace each puzzle candidate: grid fill -> cage construction -> difficulty rating -> uniqueness solver -> human solver -> acceptance. Mark which gates are actual production gates versus tests or debug-only diagnostics.
3. Identify all ways a candidate can be discarded or the same state revisited; check finite budgets at **each** nested loop and recursive call, not just the outer generator loop.
4. Investigate the historical 11x11 seed-1 regression and current zero-start HARD / VERY_HARD seeds 0–10. Distinguish generation budget exhaustion, invalid cage mathematics, rejection distributions, classical difficulty mismatch, uniqueness, and human-deduction-only status.
5. Verify arithmetic with zero, negatives, nonconsecutive integer sets and nonstandard division semantics. Preserve deliberate behavior until clarified, not label unusual rules as bugs by assumption.
6. Audit the 37 human strategies for soundness, progress monotonicity, conflicting state updates, order bias, repeated computations, Nishio cap, and revelation avoidance. A unique solution is **not** proof of human-only solvability.
7. Inspect concurrency and cancellation, cache invalidation, error handling, performance on lower-powered Android devices, and data/save compatibility.

## Deliverables
- A prioritized, reproducible report: confirmed defects vs probable bottlenecks vs hypotheses; source file/line, mechanism, scenario, tests, and user impact.
- A diagram or textual call graph showing where candidate data travels and where loops are nested.
- A finite-state adaptive-cycler recommendation: reuse verified parts, candidate retention, state fingerprinting, stagnation detection, structured error feedback, bounded attempts/time/memory, fallback to original generator.
- Comparative benchmark protocol using identical seed/digit/grid/difficulty/operator configurations and explicit human-certification results.
- Tests executed, exact commits, logs/artifacts and links, coverage gaps, and rollback-friendly proposed patches.

## Engineering requirements
Preserve sizes 3x3–11x11 only, one valid solution, cage geometry, row/column rules, intended digit sets, human deduction-only acceptance, existing gameplay and pencil marks. Use Very Hard terminology with persistence migration awareness. Do not silently relax thresholds, count unbounded guesses as logical deduction, or claim static heuristics prove a defect. Apply TAFT (Test -> Analyze -> Fix -> Test) once a fix is specifically justified.
