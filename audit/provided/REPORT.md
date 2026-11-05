# Gauguin — Integrated human-strategy verification (Round 5)

**Date:** 2026-11-05  
**Installed baseline:** User-tested Gauguin experimental v8, unchanged.  
**Source base:** Upstream Gauguin v0.52.1 ZIP supplied by the user.  
**Purpose:** Advance beyond isolated strategy tests and investigate whether the 37 registered strategies operate soundly *together*, including cache invalidation and safety repairs, without duplicating previous basic arithmetic tests.

## What was actually tested

The original Kotlin code for **all 37 registered strategies** was compiled together using the available JDK 21 and local Kotlin compiler. The tests used minimal substitute grid models to provide candidate, cage, and arithmetic data, rather than an Android runtime. The strategies themselves were not rewritten into reference Python equivalents. An independent 4×4 Latin-square enumerator checked every candidate elimination and placement against **every completion still allowed by the original puzzle state and its arithmetic cages**. Some test states intentionally permitted multiple solutions.

| New run | Puzzle states | Strategy calls | Deduction steps | Candidate removals | Number placements | Bad moves / exceptions |
|---|---:|---:|---:|---:|---:|---:|
| Normal in-game strategy priority | 220 | 11,021 | 2,806 | 2,649 | 2,733 | 0 |
| Deliberately varied strategy priority | 360 | 69,417 | 6,603 | 3,923 | 4,424 | 0 |
| Varied priority, **original incremental cache** | 240 | 47,829 | 4,560 | 2,865 | 3,047 | 0 |
| Fresh regression after combining all source fixes, **original incremental cache** | 180 | 34,490 | 3,360 | 2,041 | 2,253 | 0 |
| **Total for this round** | **1,000** | **162,757** | **17,329** | **11,478** | **12,457** | **0** |

Counts are produced by four reproducible, seeded Kotlin test runs. These four runs are additional to earlier direct-strategy test rounds.

### Strategy coverage and interpretation

In normal priority order, only **3 of 37** strategies performed moves: early singles handled most states. In the varied-order run, **34 of 37** made at least one deduction, including Nishio, X-Wing, Y-Wing, cage sums, and the corrected two-line method. Varied order was for testing only; it is **not a proposed change to Gauguin's solving order**. The remaining strategies have earlier dedicated direct-source tests, but none is exhaustively certified over all dimensions and variants.

The cache-specific runs compiled and exercised the developer's actual `PossiblesCache` and `HumanSolverCacheImpl` methods, fed by an independently implemented raw cage-combination generator. After each changed-cell report, the real cache updated incrementally, then every cage cache was compared against a fresh independently enumerated set of combinations. **No stale cache state, omitted dirty-cell report, invalid deduction, or exception** was detected in those runs.

### Source fixes integrated into one patch

The source candidate is a cumulative patch against the user's original 0.52.1 source, modifying **six Kotlin files**:

1. `HumanSolver.kt` — stop strategy execution when an unfilled cell has no candidates or any cage has no remaining combinations.
2. `PossiblesReducer.kt` — do not interpret an empty combination list as proof every candidate can be removed (preserves the v8 safety behavior).
3. `AbstractTwoCagesTakeAllPossibles.kt` — replace the erroneous hard-coded 3-line requirement with `numberOfLines`.
4. `NishioWithPairs.kt` — accept proof by contradiction only, not a successful speculative branch.
5. `AdvancedNishioWithPairs.kt` — same proof-by-contradiction restriction.
6. `NishioCore.kt` — retain the v8-style candidate-emptiness, row/column duplication, and cage-validity checks on each trial step; prevent an empty trial combination set from deleting candidates.

**Patch verification:** `patch --dry-run -p1` succeeded for all six files against the original source. Every strategy class plus the actual `HumanSolver.kt`, `PossiblesCache.kt`, `HumanSolverCacheImpl.kt`, and related classes compiled locally using the stubs described above. A focused integration test rejected a contradictory arithmetic-cage state and accepted a valid solved state.

## Boundaries of the evidence

- The tests use **the original Kotlin strategy implementations** and original human-solver/cache methods, but a **test-double Grid model**, not Gauguin's full `gauguin-core` module or the Android app. The raw cage-combination enumerator is also a test double.
- The exhaustive oracle covers 4×4 standard digits in these **new combined runs**, not every 3×3–11×11 case or all alternate number sets. Larger-grid tests were undertaken in earlier rounds but are not rerun here.
- These tests cannot guarantee zero invalid deductions in all possible states. They show no counterexamples among the recorded tests. A complete proof or systematic production-class instrumentation would be stronger.
- The candidate source patch is **not an installable APK** and does not include v7's Android UI/preview performance changes. Do not replace the working v8 APK with a binary built blindly from this source alone.
- The project's full Gradle task `:gauguin-human-solver:check` was attempted, but its wrapper requires Gradle 9.7 and Maven/Android dependencies absent from this isolated environment. Downloading Gradle from `services.gradle.org` failed with `UnknownHostException` (network unavailable). Consequently the **full upstream tests and Android build were not run**.
- Before shipping an update, integrate the cumulative fixes with the appropriate v8 or newer upstream app sources, run full project-level unit/integration tests and Android instrumentation, and measure puzzle generation at 11×11. Keep the signing identity consistent for an in-place app update.

## Recommendation

**Maintain v8 as the working installation.** Treat the combined source patch and tests as a verified local engineering candidate, not yet a release. The next high-value dependency is a build environment with **Gradle 9.7 and project dependencies** (such as a configured GitHub Actions runner). Firebase billing is not needed for the local mathematical tests or the compiled Kotlin checks reported here. No user download is necessary until a full build route is selected.
