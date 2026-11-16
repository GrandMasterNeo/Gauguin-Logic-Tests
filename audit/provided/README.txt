Gauguin human-strategy integration audit, November 5, 2026.

This ZIP contains: (a) report, (b) source-only patch against user-supplied Gauguin 0.52.1, (c) test models and oracles, and (d) test output logs.

To apply: unpack original gauguin-0.52.1.zip, cd gauguin-0.52.1, patch -p1 < Combined_Source_Fixes.patch (relative path adjusted as needed).

The patch is NOT an Android APK. The test models are NOT production Android classes. For a release, build the complete app with the official project Gradle configuration, port prior v7 UI/performance fixes, run the full project tests and device tests, and preserve the app update signing key.

No APK signing keys or user bug reports are included in this archive.
