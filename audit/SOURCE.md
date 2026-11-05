# Source provenance and scope

Official repository: https://github.com/meikpiep/gauguin

Release tag: `v0.52.1`

Resolved commit: `e7e9763d1cbd627e18e692b8fc9b623cdebedeaf`

The complete tracked tree was imported using `git archive` into `upstream/gauguin`.
Original license, legal notices, assets and source headers are retained. The initial
import is a separate commit, allowing the six-file safety patch to be reviewed alone.
The attachment ZIP SHA-256 is `7b5cc43680f5a88e720a5c6da420c9f32f09a4a1dc6adbd4f2cc3d782f9742ef`.
The supplied patch is preserved in `provided/Combined_Source_Fixes.patch` and applied
without changes. `provided/REPORT.md` contains historical claims from the supplied
bundle; these are not fresh test results from this repository.

The original baseline workflow remains unchanged and checks out upstream. The new
corrected-source workflow checks out this repository and runs the same original
Gradle unit test tasks against the patched tree. It does not build or publish an APK.
No Android UI, rules, strategy priority, signing or installation changes are made.
The safety patch intentionally changes solver deductions and contradiction handling;
compatibility must be assessed from tests, not assumed from patch application.

This work does not establish mathematical correctness of all 37 strategies, cover
all puzzle sizes/variants, or validate the user's separately installed v8 APK.
