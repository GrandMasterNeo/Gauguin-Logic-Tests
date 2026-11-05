# Gauguin v0.52.1 safety-patch results

Run date: 2026-11-05. Official source commit:
`e7e9763d1cbd627e18e692b8fc9b623cdebedeaf`.

## Exact results

| Run | Core | Human solver | Outcome |
| --- | --- | --- | --- |
| Existing upstream GitHub baseline | Successful task | Successful task | Success; counts not emitted in baseline log |
| Patched source, unchanged upstream tests plus 3 new regressions | 263 passed | 34 passed, 2 failed (36 total) | Gradle exit 1 |
| After Nishio fixture/expectation corrections | 263 passed | 36 passed | Gradle exit 0 |

There were no skips or errors in the two local completed suites. Of the 36 human
solver cases, 33 are upstream tests and 3 are new production-model regressions.
The final run reran the human-solver suite and reused the core task's passing
results because core inputs were unchanged. It reported `BUILD SUCCESSFUL in 14s`.
The first fully executed local suite reported `BUILD FAILED in 1m 9s`.

Original baseline: https://github.com/GrandMasterNeo/Gauguin-Logic-Tests/actions/runs/42051608359
The baseline checked out the same upstream SHA and reported success in 2m 17s.
It did not run this branch's patch.

## Failure investigation and corrections

Both original failures were in `NishioWithPairsTest`:

1. `fillCells fills all cells as the grid got solved`, line 40: expected `true`,
   actual `false` for all cells having user values.
2. `tryWithNishio results nishio solved`, line 56: expected `NishioResult.Solved`,
   actual `NishioResult.Contradictions`.

The shared fixture has a prefilled single cage at cell 6, with target/user value
3 but stored solution `GridCell.value = 2147483647` (the unset sentinel).
Production `GridCage.isMathsCorrect()` validates a single cage using that stored
solution value. The new trial guard therefore rejects this fixture immediately.
An isolated diagnostic with the exact upstream factory confirmed:

- Original fixture: completed cage 4 is already invalid; trial = `Contradictions`.
- Set only that single cell's stored solution value to 3: trial = `Solved`.
- With repaired metadata, the patched strategy returns `Success` and places only
  cell 0; the previously filled cells are 2, 5, 6 and 8.

The fixture now sets `grid.cells[6].value = 3`. The direct trial test retains its
original `Solved` assertion. The strategy test now requires the proven value 1
at cell 0, exactly one new placement, and a changed-cell report matching the actual
value/candidate changes. Expecting all cells to be committed from a successful
speculative branch conflicts with the supplied contradiction-only strategy rule.
No production changes beyond the supplied six-file patch were needed.

The unmodified tests and import remain available in the source-import commit.
The original failing XML/logs are preserved in `results/original-junit`,
`results/original-test-summary.json` and `results/gradle-tests-jdk21-trust.log`.
Final XML/summary are in `results/final-junit` and `results/final-test-summary.json`.
The diagnostic source is `NishioFixtureDiagnostic.kt`, outside Gradle source sets;
its captured output is in `results/nishio-fixture-diagnostic.xml`. Its one diagnostic
case is not counted in either full-suite result above. To repeat it, temporarily
copy it beside `NishioWithPairsTest.kt`, run the solver test task with
`--tests '*NishioFixtureDiagnostic'`, then remove the temporary copy.

## Environment and reproduction

Original command, run from `upstream/gauguin`:

```sh
./gradlew :gauguin-core:test :gauguin-human-solver:test --no-daemon --stacktrace -Pbuildserver
```

The first attempt could not fetch Gradle because Java did not use the session's
required HTTP proxy. Explicit Java proxy properties allowed the original wrapper
to download and checksum-validate Gradle 9.7.0. The preinstalled Debian Java 21
runtime lacked a compiler, causing upstream Foojay 0.9.0 to attempt toolchain
provisioning and fail with `NoSuchFieldError: JvmVendorSpec.IBM_SEMERU`.
A workspace-local Temurin JDK 21.0.13 was downloaded from the Adoptium API.
Its default CA store did not trust the session proxy; using the existing system
Java CA store fixed TLS verification. Build files/dependency versions were not
changed. Earlier failed attempts are preserved as separate logs.

The successful local command used these environment selectors in addition to the
original arguments (these paths/proxy settings are specific to this workspace):

```sh
JAVA_HOME=/workspace/toolchains/jdk21 \
JAVA_TOOL_OPTIONS='-Dhttps.proxyHost=proxy -Dhttps.proxyPort=8080 -Dhttp.proxyHost=proxy -Dhttp.proxyPort=8080 -Djavax.net.ssl.trustStore=/etc/ssl/certs/java/cacerts' \
./gradlew :gauguin-core:test :gauguin-human-solver:test --no-daemon --stacktrace -Pbuildserver
```

`python audit/summarize_test_results.py` summarizes current build XML. Saved
summaries preserve each completed full run separately. `git diff --check` passed.
The new GitHub workflow uses JDK 21 and this repository's corrected source; it has
not run remotely because the development branch could not be pushed.

## Scope and remaining limitations

All 999 original tracked files were imported. Hash comparison before test changes
found exactly the six patched production files different; licenses and all other
upstream contents matched. The original baseline workflow is unchanged. The
37-entry strategy registry/order and Android/gameplay code are unchanged.

The new regressions verify empty combination handling, contradictory cage rejection
without candidate erasure, and refusal to commit a speculative solution of an
ambiguous puzzle, using actual core classes. The bundled historical oracle tests
use stubs and were preserved but not rerun or counted as fresh evidence.

This is unit-test evidence, not full mathematical verification of 37 strategies.
The separate Gradle `integrationTest` tasks, all puzzle sizes/variants, performance,
Android instrumentation and device gameplay were not tested. Restricting Nishio
can affect solver completion/difficulty behavior; unchanged UI/rules do not prove
identical puzzle generation or gameplay experience. No APK was built, signed,
published or installed, and the separate v8 installation was not accessed.

## Pull request status

Local development branch: `dev/gauguin-0.52.1-logic-safety`.
Git push returned HTTP 403 (permission denied), and the GitHub connector's branch
creation also returned HTTP 403 (`Resource not accessible by integration`).
The branch is therefore local only. No remote PR exists. `PR_DESCRIPTION.md` is
ready for a draft PR targeting `main` once GitHub Contents and Pull requests write
access is available. Main was not changed or merged.
