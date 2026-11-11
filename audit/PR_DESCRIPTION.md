## Source and safety changes

Import the complete official Gauguin v0.52.1 source from meikpiep/gauguin at
`e7e9763d1cbd627e18e692b8fc9b623cdebedeaf` into `upstream/gauguin`, preserving
its licenses and legal notices. Apply the supplied six-file patch: reject
contradictory candidate/cache states, guard empty combinations, correct the
two-line occurrence threshold, restrict Nishio strategy deductions to
contradictions, and check trial consistency.

The 37-strategy registry/order, Android UI and game rules are unchanged. The
existing branch history and staged patch are preserved. CI now tests this
branch's corrected source and test changes, rather than a separate upstream
checkout with the two known old expectations. The upstream baseline workflow
remains unchanged.

## All 299 unit tests

| Suite | Tests | Passed | Failed | Skipped |
| --- | ---: | ---: | ---: | ---: |
| Core | 263 | 263 | 0 | 0 |
| Human solver (33 upstream + 3 new regression cases) | 36 | 36 | 0 | 0 |
| **Total** | **299** | **299** | **0** | **0** |

These are the completed local Gradle results, using JDK 21 and the original tasks:

```sh
cd upstream/gauguin
./gradlew :gauguin-core:test :gauguin-human-solver:test --no-daemon --stacktrace -Pbuildserver
```

The final run reused the passing core task results because its inputs were
unchanged and reran the solver suite successfully. Individual results for all
299 tests are retained in `audit/results/final-junit/`, with a machine-readable
summary in `audit/results/final-test-summary.json`. The initial patched-source
run, before changing upstream tests, passed 263/263 core and 31/33 upstream solver
cases; all 3 added regressions also passed. Its two failures, logs and XML are
preserved separately under `audit/results/original-junit/` and explained in
`audit/RESULTS.md`. Remote PR CI will provide a separate result.

## Why the two Nishio test changes do not relax the safety contract

1. **Repair missing fixture metadata; retain the original solved-trial assertion.**
   In the shared fixture, cell 6 is a completed single cage with target/user value
   3, but its stored solution value was the unset sentinel `2147483647`.
   Production `GridCage.isMathsCorrect()` compares a single cage against that
   stored value, so the new consistency check correctly rejected the malformed
   fixture. Setting only `grid.cells[6].value = 3` makes its metadata consistent.
   `tryWithNishio results nishio solved` still requires `NishioResult.Solved`;
   it was not changed to accept a contradiction. The isolated before/after
   diagnostic and captured output are included in `audit/`.

2. **Check the proven deduction instead of committing a speculative completion.**
   The old `fillCells fills all cells as the grid got solved` test expected the
   strategy to copy a successful speculative branch into the real grid. A
   successful trial alone does not establish that its placements hold in every
   valid completion. The supplied patch accepts deductions only when a trial
   contradicts the puzzle constraints. The revised test requires `Success`,
   cell 0's specific proven value **1**, exactly **one new placement**, and a
   changed-cell list matching every actual value/candidate change. It does not
   merely accept `false` or remove the success assertion. The separate solved
   trial assertion and the existing contradiction test remain.

An additional production-model regression uses an ambiguous 2×2 puzzle to check
that Nishio leaves it unchanged rather than adopting one speculative solution.
The other new cases check empty-combination preservation and rejection of an
incompatible cage without erasing candidates. No production code was altered to
bypass the trial/cage consistency checks when fixing the tests.

These assertions support the stated safety behavior; they are not a proof of all
37 strategies. Separate integrationTest tasks, all sizes/variants, performance,
Android instrumentation and device gameplay have not been validated. Nishio's
restriction can affect completion/difficulty and needs compatibility review.

No private signing keys or keystores are included. No APK was built, signed,
published or installed, and the separate working v8 installation was not accessed.
This PR is for review; do not merge or release yet.
