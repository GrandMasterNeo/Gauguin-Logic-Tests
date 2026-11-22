# Gauguin architecture audit — working record

**Audit branch:** `experiment/architecture-audit` (isolated)  
**Source baseline:** `experiment/autonomous-generation-lab` at `42e6fad8e4bb7002b89b9343fc9f5e4b0c147049`  
**Scope:** static code/structure, existing CI evidence, reproducible dynamic diagnostics, and adaptive-cycler design comparison.  
**Current status:** first-pass source inspection and audit instrumentation; **not** a completed repository-wide Codex review or certified runtime proof.

## Existing empirical baseline

[GitHub Actions run 44922979432](https://github.com/GrandMasterNeo/Gauguin-Logic-Tests/actions/runs/44922979432), for 11x11, FIRST_DIGIT_ZERO, seeds 0–10:

- HARD: seed 5 passes uniqueness and matches HARD; 10 other seeds fail the generation test.
- VERY_HARD: 0 of 11 pass the generation test.
- Passing HARD seed 5 is explicitly `humanCertified=false`; this is **not** evidence of human-only deduction.
- These job summaries show `Gradle integrationTest` failure, not whether every failed candidate violated uniqueness. Per-seed detailed artifact logs should be used to classify each failure before modifying behavior.
- Prior experiments on other branches include successful test runs; do **not** extrapolate these 11x11 zero-start failures to all sizes, modes, or production versions.

## First-pass findings and hypotheses (source-based; verify in runtime tests)

| Priority | Classification | Source | Evidence and why it matters | Next test |
|---|---|---|---|---|
| P0 | **Confirmed architectural mechanism, effect to quantify** | `RandomCageGridCalculator.kt:50-116`, `DifficultyAwareGridCreator.kt:33-45` | Each difficulty-rejected candidate is discarded. No feedback modifies the next candidate's shape, operator balance, or proposal strategy. The outer calculator has time/attempt limits, but those alone do not improve success probability. | Record rejection class, candidate fingerprint, score/difficulty histograms and time per accepted candidate; baseline vs adaptive refinement |
| P0 | **Potential nested unbounded path** | `GridCageCreator.kt:19-24,41-49` | Cage creation can clear all cages and restart with `do...while(restart)`, without a local restart cap or cancellation check. Outer generator budget is tested before `GridCreator.createRandomizedGridWithCages`, not inside this restart loop. | Force repeated no-fit shapes in a deterministic test; prove termination or enforce a separate bounded restart/cancellation policy |
| P0 | **Acceptance-policy gap, conditional** | `DifficultyAwareGridCreator.kt:48-56` | When difficulty calibration is unsupported for a variant, `isWantedDifficulty` returns true, even if a specific difficulty is requested. Explicit integration assertions may catch mismatched results, but normal generation can bypass that difficulty gate. | Enumerate supported vs unsupported variants; decide desired behavior and add an acceptance-policy test |
| P1 | **Background-state failure path** | `GridCalculationService.kt:72-84,87-113` | Both jobs set state to `CURRENTLY_CALCULATING` and reset only after successful completion. A thrown generation-budget exception or cancellation can skip reset; state may remain stale. | Inject an exception/cancellation; assert UI calculation state and recovery |
| P1 | **Undefined-or-special-case arithmetic semantics, needs product decision** | `GridCageOperationDecider.kt:80-95`, `GridCageResultCalculator.kt:34-44`, `DivideCreator.kt:12-23` | A division operation with one zero operand is intentionally accepted and represented by target 0. This is not conventional division; mathematical validity and human solving need an explicit domain rule. Do not silently change creator intent. | Exhaustively compare creation, possible-combination enumeration, and solver for zero/negative/prime modes |
| P1 | **Human-certification gap** | `ProgressiveGenerationMatrixTest.kt:21-24,65-70` | The active matrix checks classical difficulty and uniqueness, but explicitly reports `humanCertified=false`. `HumanSolver.kt:54-65` has a reveal fallback unless `avoidReveal` is true. | Add an independent deduction-only gate on copied grids with reveal disabled, explicit Nishio policy, strategy evidence and bounded time |
| P2 | **Potential search overhead** | `MergingCageGridCalculator.kt:129-243` | Pairwise shuffled cage loops call a DLX solver for merger candidates. Failures cause repeated scans; three no-success passes cap outer merge phases, but cost can increase sharply for large grids. | Profile number of pair checks and DLX calls per successful merge; cache infeasible pairs when state unchanged |
| P2 | **Potential reproducibility issue** | `RandomSingleton.kt:5-22`, `GridCalculatorFactory.kt:12-21`, `RandomPossibleDigitsShuffler.kt:6-10` | The default shuffler captures a Random instance at creation; `RandomSingleton.discard()` changes the singleton reference, but an existing shuffler may retain the old instance. Analyze whether relevant production paths mix streams, rather than assuming seed regression. | Trace seed source and shuffle stream through both generator factories, compare deterministic outputs |
| P2 | **Potential inefficiency** | `GridDifficultyCalculator.kt:24-34` | Classical difficulty recomputes cage combination cardinalities and multiplies huge BigIntegers. Cache per immutable cage candidate or compare with sum-of-logs only after proving classifications stable. | Profile allocations and exact rating agreement, including zero combinations |

## Codebase review plan

1. Inventory every production source module, test module, and workflow. Run a broad but **heuristic** source scan, then manually investigate every reported high-severity result.
2. Trace generator `GridCalculatorFactory -> RandomCageGridCalculator / MergingCageGridCalculator -> GridCreator/GridRandomizer/GridCageCreator -> GameDifficultyRater -> MathDokuDLX`. Trace `HumanDifficultyCalculatorImpl -> HumanSolver -> HumanSolverStrategies` separately; document whether each route is actually required before acceptance.
3. Audit termination and progress invariants for nested loops, recursion, solver passes, and calculation coroutines; check failure recovery and re-entrancy.
4. Run targeted debug reproductions on the exact failing seed/configuration/commit; classify **difficulty / uniqueness / math / timeout / crash / missing support** separately. Keep bounded logs and attach per-run diagnostics.
5. Only then prototype a new feedback-driven candidate cycler **on a separate child branch**. Compare it against the unmodified original implementation on matched configurations and lower-powered-device budgets.
6. Confirm no loss of row/column validity, cage correctness, one-solution uniqueness, human deduction-only policy, prior passing variants, or working pencil marks.

## Acceptance criteria before any cycler integration

- Explain which rejection reason a refinement addresses; show measurable improvement across several deterministic seeds, not one cherry-picked seed.
- No unbounded loops or runaway memory; adaptive session includes finite budgets, state fingerprinting, stagnation detection, and original-generator fallback.
- No silent threshold weakening or transition from deduction to guessing.
- Preserve versioned, rollback-friendly commits and keep `main` and active automation branches untouched.
