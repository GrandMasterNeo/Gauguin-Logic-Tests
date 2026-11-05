Import Gauguin v0.52.1 and apply human-solver safety patch

This repository's baseline workflow tests a separate upstream checkout. Import the
complete official v0.52.1 source at `e7e9763d1cbd627e18e692b8fc9b623cdebedeaf`
under `upstream/gauguin`, retaining original licenses, and apply the supplied
six-file logic safety patch in a separate commit for review.

The patch rejects contradictory candidate/cache states, guards empty combination
reductions, corrects the two-line occurrence threshold, and restricts Nishio
strategy deductions to contradictions. It adds trial consistency checks. The
37-strategy registry and order, gameplay UI are retained. Original test failures are recorded before adapting the Nishio fixture and expectation to the new safety contract.

A separate workflow tests this branch's corrected source with the original
`:gauguin-core:test :gauguin-human-solver:test` tasks and uploads reports. Focused
regressions use the real core classes. Supplied historical stub-model tests and
logs are preserved outside production/Gradle source sets.

Validation details and limitations are recorded in `audit/RESULTS.md`. The
successful upstream baseline is run 42051608359; it does not validate this patch.
Do not merge as a release or infer full mathematical verification from this PR.
No APK publication or changes to the separately installed v8 application are part
of this change.

Validation: the first run against unchanged upstream tests passed all 263 core
cases and 34/36 solver cases (including 3 new regressions). The two failures were
traced to unset stored solution metadata in the Nishio fixture and the old
speculative-solve expectation. After fixing the fixture and asserting the proven
single placement plus accurate changed-cell reporting, all 263 core and 36 solver
cases pass with zero skips. Original failure logs, diagnostic evidence and final
JUnit XML are included. Core results were reused unchanged on the final run.

Not covered: separate integrationTest tasks, device tests, all puzzle sizes and
variants, performance or full mathematical proof. Nishio's restriction may affect
completion/difficulty and needs broader compatibility review.
