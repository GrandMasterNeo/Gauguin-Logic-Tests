# Gauguin v0.52.1 large-grid difficulty audit — 2026-11-07

## Scope and safety

- Exact upstream test baseline: `meikpiep/gauguin` tag `v0.52.1`.
- Grid sizes: 9×9, 10×10, 11×11.
- Operation-mix audit: four deterministic seeds per size × difficulty × generator = **120** boards.
- Human-solver audit: four seeds per size × classical difficulty = **60** boards. No revealed answers allowed.
- Beginner-tier audit: four seeds per size × Very Easy/Easy = **24** boards; uses a test-only solver strategy-weight cap. No production code or APK modified.
- These are deterministic samples, *not* universal proofs of human difficulty or correctness.

## Run evidence

- [120-board operation audit](https://github.com/GrandMasterNeo/Gauguin-Logic-Tests/actions/runs/42378583688) — all 3 jobs succeeded.
- [60-board no-reveal human-solver audit](https://github.com/GrandMasterNeo/Gauguin-Logic-Tests/actions/runs/42379581837) — all 3 jobs succeeded.
- [24-board strategy-ceiling audit](https://github.com/GrandMasterNeo/Gauguin-Logic-Tests/actions/runs/42380350235) — all 3 jobs succeeded.
- Workflows preserve machine-readable CSV artifacts.

## Classical difficulty vs generator

| Size | Old generator matches requested classical level | New merging generator matches requested classical level |
|---|---:|---:|
| 9×9 | 20/20 | 11/20 |
| 10×10 | 20/20 | 4/20 |
| 11×11 | 20/20 | 8/20 |
| Total | **60/60** | **23/60** |

The old generator explicitly filters candidates by the classical rating: this 100% match is expected by construction and **does not prove human accessibility**. The merging generator uses cage-density targets instead and often falls outside the old classical thresholds. Do not equate this difference with a formal human-difficulty defect.

The classical score is the logarithm of the product of cage candidate-combination counts:

`score = Σ log(valid_cage_combinations)`

### Operation mix in classic generation

Percentages below exclude fixed-number cages and aggregate four generated puzzles per difficulty/size.

| Size | Addition in Very Easy | Addition in Very Hard | Multiplication in Very Easy | Multiplication in Very Hard |
|---|---:|---:|---:|---:|
| 9×9 | 33.9% | 49.5% | 55.0% | 36.2% |
| 10×10 | 26.5% | 48.8% | 56.6% | 32.6% |
| 11×11 | 28.6% | 48.7% | 57.8% | 32.5% |

Across classical puzzles, larger addition share coincides with greater classical and human difficulty, but **within grid size and requested difficulty** the residual correlation between addition share and normalized human score was approximately **0.11** among the 53 puzzles the solver could finish without Nishio. This is weak sample-level evidence. An operator ratio is not a reliable stand-alone human difficulty filter.

## Human solver reachability

| Size | Solved without Nishio or reveals | Solved allowing Nishio but no reveals |
|---|---:|---:|
| 9×9 | 20/20 | 20/20 |
| 10×10 | 17/20 | 19/20 |
| 11×11 | 16/20 | 17/20 |
| Total | **53/60** | **56/60** |

Four Very Hard puzzles remained unfinished even allowing Nishio. A solver failure means the current automated strategy suite could not finish; it does not mean the puzzle lacks a unique solution. **All 24 Very Easy/Easy** puzzles solved without Nishio.

## Beginner-tier strategy ceilings

A test-only filter restricted the Human Solver to methods with a strategy-weight at most 5, 25, 70, 125 or 210, choosing the smallest tested tier that could complete each grid without reveals or Nishio.

| Size | Very Easy: minimum successful caps across four seeds | Easy: caps across four seeds | VE mean successful strategy steps |
|---|---|---|---:|
| 9×9 | 5,25,5,25 | 5,25,5,5 | 395 |
| 10×10 | 25,25,25,5 | 125,25,25,5 | 549 |
| 11×11 | 25,25,5,25 | 70,70,25,70 | 731 |

This is a strong directional result: **all twelve sampled Very Easy boards were solvable with techniques at weight ≤25**, yet the 11×11 boards required about **85% more successful solver steps** than 9×9 boards. These counts represent the automated solver's successful deductions, not necessarily an equal number of actions by a person.

The 11×11 Easy boards often required tier 70, versus mostly tier 25 or below on 9×9. Sample size is four boards per size/level; do not generalize these percentages to all generated puzzles.

## Engineering decision

1. **Do not modify the APK difficulty thresholds or operation probabilities yet.** Current samples do not isolate causation or establish that a ratio adjustment improves human solvability.
2. Keep the **new algorithm OFF** as the classical calibration baseline.
3. In a test-only controlled experiment, vary multiplication/addition selection odds and the quantity of fixed single cells separately, keeping grid size, difficulty, uniqueness checks, and operation visibility comparable.
4. Rank Very Easy candidates using both **maximum allowed strategy tier** and **successful deduction count** (and optionally workload per cell). Verify candidate counts, unique solutions, no-nishio completion, and generation performance before editing production.
5. Expand the sample count and validate the chosen change against held-out seeds for 9×9, 10×10 and 11×11.

**Status:** All three audits succeeded; no app or APK change applied.
