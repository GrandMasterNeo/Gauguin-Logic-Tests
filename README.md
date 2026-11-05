# Gauguin Logic Tests

This development branch imports official Gauguin v0.52.1 and applies the supplied
six-file human-solver safety patch. See [source provenance](audit/SOURCE.md).
The original licenses remain in [the imported source](upstream/gauguin/LICENSE)
and its `legal/` directory.

Run the original core and human-solver unit tests with JDK 21:

```sh
cd upstream/gauguin
./gradlew :gauguin-core:test :gauguin-human-solver:test --no-daemon --stacktrace -Pbuildserver
```

The original baseline workflow continues to test upstream. The separate
`logic-safety-ci.yml` workflow tests this branch's source and uploads reports.
The original test run and subsequent Nishio test corrections are documented in
`audit/RESULTS.md`; focused safety regressions
use the production core classes. Supplied stub audits live under `audit/provided`
and are outside Gradle source sets.

No APK is built or published by these workflows. This branch does not contain or
modify the user's installed v8 APK. Unit test success alone would not prove all
37 strategies mathematically correct or establish Android gameplay compatibility.
