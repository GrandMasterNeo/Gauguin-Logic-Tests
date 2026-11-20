# Final exhaustive configuration certification
Status: PLANNED. Execute only after staged, production and human compatibility gates pass.

## Coverage
Enumerate all valid supported configurations from source definitions: grid sizes 3–11, seven digit sequences, five difficulties, six numeral systems, three single-cage settings, arithmetic operation options and operator visibility. Document unsupported combinations. Numeral display bases must be tested for representation correctness, not mistaken for changed mathematical values.

## Execution
1. Preflight compilation, dependency resolution, numeral conversions, production generation and human compatibility.
2. Freeze commit SHA and deterministic case manifest; split into independently rerunnable size/sequence/difficulty shards with bounded GitHub Actions concurrency.
3. Record seed, time budget, case ID, uniqueness, difficulty, human solver result, rejection counters, DLX timing and failure classification.
4. On failure rerun with Gradle --info --stacktrace --rerun-tasks and upload compact debug logs and XML; always retain machine-readable shard summaries.
5. Test arithmetic and Latin constraints, exactly one solution, difficulty acceptance and human-deduction-only certification where supported.
6. After TAFT fixes rerun impacted shards; perform final clean pass pinned to one commit. Publish totals, exclusions, failures, timeouts and artifact links.

## Gates
Never silently relax uniqueness or difficulty. Do not merge to main automatically. Do not launch exhaustive jobs while staged checks fail.

## Known blockers on 2026-11-20
Nullable difficulty diagnostic compilation fix committed; current workflow results still need inspection. Gradle Foojay resolution has been intermittent. Earlier 11x11 ZERO and MINUS_FIVE cases need diagnostic review.