# Parameter-only generation calibration (isolated)

This branch is an **experiment**, not a new generator and not a release candidate. It branches from `experiment/autonomous-generation-lab` at `42e6fad8` and has no effect on the main line or the scheduled generator automation.

## Why
Latest 11x11 zero-start HARD/VERY_HARD seed reports show that most generated cages do not achieve the requested classical difficulty, while those that do can fail the one-solution check. Before engineering a feedback-driven cycler, evaluate whether adjusting existing selection parameters can improve both criteria.

## Two opt-in controls

1. `gauguin.generation.experimentalLargeCagePercent` — 0..100 chance to try a feasible four-cell cage first at each cage selection step. Default `0` (no change). This is geometry preference, **not** permission to bypass spatial constraints.
2. `gauguin.generation.experimentalZeroMultiplicationWeight` — weight of multiplication in cages containing a zero, within `OPERATIONS_ALL`. Default `65`, preserving the legacy 65:16 weight relation before feasible subtraction/division weights. Test value `20` discourages ambiguous zero-product cages without prohibiting them.

Other settings—including 60s budget, allowed operations, numeral domain, classical difficulty thresholds, uniqueness, row/column constraints—are unchanged.

`ProgressiveGenerationMatrixTest` sets opt-in properties only when `GAUGUIN_MATRIX_LARGE_CAGE_PERCENT` or `GAUGUIN_MATRIX_ZERO_MULTIPLICATION_WEIGHT` are supplied; it clears both on exit.

## Four controlled configurations

| Configuration | Large-cage preference | Zero multiplication weight |
|---|---:|---:|
| BASELINE | 0 | 65 |
| LARGE_CAGES | 75 | 65 |
| ZERO_OPERATION_BIAS | 0 | 20 |
| COMBINED | 75 | 20 |

For each use identical `11x11`, `FIRST_DIGIT_ZERO`, `HARD` and `VERY_HARD`, seeds `1` and `5`. CI forces new test execution using `--rerun-tasks`, verifies every reported dimension and uniqueness, preserves bounded logs and full rejection diagnostics. A successful Gradle test here still does **not** establish human deduction-only certification.

## Decision rule

Do **not** merge based on a single pass. Inspect rejection histograms, uniqueness-test frequencies, costs and correctness, then repeat promising cases across more seeds and sizes and enforce human deduction-only certification. If tuning does not improve yield reliably, consider algorithmic candidate retention/refinement. Never weaken difficulty thresholds or uniqueness to manufacture success.

Run: [.github/workflows/generator-parameter-calibration-ci.yml](../../.github/workflows/generator-parameter-calibration-ci.yml).
