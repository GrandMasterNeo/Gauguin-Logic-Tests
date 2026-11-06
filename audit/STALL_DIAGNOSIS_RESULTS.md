# Stalled-puzzle reproduction and generation-gate feasibility

**Run:** https://github.com/GrandMasterNeo/Gauguin-Logic-Tests/actions/runs/42143315088
**Source branch:** `logic-matrix-audit` (diagnostic-only; not merged)
**Date:** 2026-11-05/06 UTC

The test `StalledPuzzleDiagnosisTest` uses the actual `HumanSolver`
implementation, not the earlier manual per-strategy tracer. It independently
recreates the four exact seeded puzzles from
`audit/ADVANCED_LOGIC_RESULTS.md` with the normal **default square-board
generator**, which checks classical difficulty and unique solutions.

For each case we compare solving with and without Nishio, with
`avoidReveal=true`. Stored correct answers are used *only* for assertions; no
answers are revealed or inserted. We also inspect the post-stall state for
contradictory cages, empty candidate sets, forced cage values, remaining
row/column hidden singles, and candidates unsupported by their cage's viable
combinations.

## Reproduction — confirmed in actual production HumanSolver

| Puzzle | Fully solved without Nishio? | Fully solved with Nishio? | Filled at stall | Solver time* | Contradiction/empty cage | Basic singles at stall | Unsupported cage candidates |
| --- | --- | --- | ---: | ---: | ---: | ---: | ---: |
| 9×9 Extreme, seed 1 | No | No | 5/81 | 2,484 ms | 0 | 0 | 0 |
| 10×10 Hard, seed 0 | No | No | 12/100 | 3,877 ms | 0 | 0 | 0 |
| 10×10 Extreme, seed 1 | No | No | 10/100 | 6,691 ms | 0 | 0 | 0 |
| 11×11 Extreme, seed 0 | No | No | 8/121 | 2,990 ms | 0 | 0 | 0 |

*Time measured for the trial-enabled human solver on a GitHub Actions runner,
not on the user's phone. CI hardware, JIT, caches and prior task loads differ
from Android. No strict time budgets were asserted by this test.

These are **true incompleteness cases** rather than artifacts of the manual
strategy runner. Each post-stall grid still contains the correct solution
among its candidates. None has a forced unfilled cage cell, row/column
single-value deduction, or candidate not supported by a viable local cage
combination. This supports the *hypothesis* that the current strategy set
cannot derive necessary more-global deductions; it does not mathematically
prove that no other local method exists.

## Bounded retry experiment — two further generated boards at same level

The test reused each initial case's seeded random-number generator to produce
up to two *new* uniquely-solvable, correctly-classified candidates. A candidate
was considered eligible if `HumanSolver(...).solveAndCalculateDifficulty(
avoidReveal=true)` succeeded with Nishio allowed.

| Original stalled case | Replacement #1 | Replacement #2 | First solvable replacement |
| --- | --- | --- | --- |
| 9×9 Extreme seed 1 | Solved 81/81 | Not needed | 1 |
| 10×10 Hard seed 0 | Solved 100/100 | Not needed | 1 |
| 10×10 Extreme seed 1 | Stalled 7/100 | Stalled 13/100 | None within 2 |
| 11×11 Extreme seed 0 | Stalled 5/121 | Stalled 6/121 | None within 2 |

### Measured incremental generation and human-check latency

| Candidate | Grid generation | Human solve/check | Total of these two |
| --- | ---: | ---: | ---: |
| 9×9 Extreme, replacement #1 | 440 ms | 933 ms | 1,373 ms |
| 10×10 Hard, replacement #1 | 235 ms | 763 ms | 998 ms |
| 10×10 Extreme, replacement #1 | 1,494 ms | 893 ms | 2,387 ms |
| 10×10 Extreme, replacement #2 | 261 ms | 10,296 ms | 10,557 ms |
| 11×11 Extreme, replacement #1 | 2,089 ms | 2,962 ms | 5,051 ms |
| 11×11 Extreme, replacement #2 | 2,120 ms | 14,656 ms | 16,776 ms |

On 11×11 Extreme, the two unsuccessful replacements took about **21.8 seconds
combined**, *after* the original generated candidate was already known to
stall. There is no evidence yet for a practical success rate or acceptable
on-device latency of unlimited reject-and-retry. Do **not** ship an unlimited
synchronous human-solvability gate.

### Confirmed facts, limits and next action

- Both the mathematical uniqueness checker and classical difficulty filter
  are already part of the default generator. **Neither ensures that the
  37-strategy human solver can complete a puzzle.**
- The four repeated stalls did **not** introduce an incorrect placement or
  erase a true candidate.
- The bounded retry approach works on one 9×9 Extreme and one 10×10 Hard
  instance, but produced no acceptable candidate within two attempts for
  10×10 and 11×11 Extreme.
- The displayed difficulty label and *practical* human-solvability
  are different properties; changing threshold values alone is not a
  human-solvability guarantee.
- Promising alternatives for evaluation, **not yet approved for production**:
  (a) precomputed and cached logically verified puzzles with time/cap limits;
  (b) a construction process that preserves a verified human-solution path;
  (c) additional sound, finite-cost inference rules, with difficulty
  recalibration and user-facing progression.
- Android device generation latency, alternate board variants, and large
  random-seed samples remain unmeasured. CI results must not be presented
  as an on-device performance benchmark.

**No production Kotlin code, Android UI behavior, pencil marks, APKs or the
separate working v8 installation were modified.** Keep the PR draft/unmerged.
