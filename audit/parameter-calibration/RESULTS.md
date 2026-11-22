# Initial parameter calibration results — 11x11, zero-start

**GitHub Actions:** [run #44970931549](https://github.com/GrandMasterNeo/Gauguin-Logic-Tests/actions/runs/44970931549), tested commit `a792c32a0a80aa5de357ca6ee1dea345f5de6cbb` (parameter source and CI; docs-only commit added after execution).  
**Configurations:** same grid 11x11; `FIRST_DIGIT_ZERO`; operations ALL; fixed single-cage setting; seeds 1 and 5; target HARD and VERY_HARD; maximum generation budget 60 seconds.  
**Certifications:** classical difficulty plus one-solution uniqueness only. Human deduction-only proof **not executed**.  
**Isolation:** original generator behavior is unchanged when experimental knobs are unset.

## Tested cases

| Scenario | Large-cage preference | Zero-multiply weight | Hard seed 1 | Hard seed 5 | Very Hard seed 1 | Very Hard seed 5 |
|---|---:|---:|---|---|---|---|
| BASELINE | 0 | 65 | Fail (267 candidates; 6 nonunique) | **Pass** (42 candidates, 1 solution) | Fail (296 difficulty rejections) | Fail (295 difficulty rejections) |
| ZERO_OPERATION_BIAS | 0 | 20 | Fail (391 candidates; 2 nonunique) | **Pass** (82 candidates, 1 solution) | Fail (408 difficulty rejections) | Fail (404 difficulty rejections) |
| LARGE_CAGES | 75 | 65 | Fail (1 candidate; nonunique, DLX 65 s) | Fail (13 candidates; 8 nonunique) | Timed out at 180 s | Timed out at 180 s |
| COMBINED | 75 | 20 | Fail (29 candidates; 8 nonunique) | Fail (22 candidates; 9 nonunique) | Fail (96 candidates; 3 nonunique) | Fail (41 candidates; 4 nonunique) |

## Evidence-based interpretation

- **Parameter changes have real effects**: favoring larger cages increases the proportion of candidates entering the Hard/Very Hard uniqueness check, and produces far fewer easy candidates than baseline.
- **The constraints compete**: in these samples, larger-cage candidates that reached uniqueness checking had **two solutions** and DLX runtime increased sharply, including >60s individual solver calls. Merely biasing toward four-cell cages was not a successful fix.
- **Reducing zero-cage multiplication weight from 65 to 20** reduced candidate difficulty in these cases (more Very Easy rejections), despite producing more candidate grids per minute. It did not repair Very Hard generation.
- **Combined parameters also failed** all four cases. It shifted rejection distributions toward medium/hard and increased expensive uniqueness checks, but yielded no certified unique puzzle.
- The outer 60s generation budget is **not a hard interrupt** for a single DLX solve; observed individual calls reached 98 seconds and total elapsed reached >120 seconds. This should be addressed independently when evaluating mobile performance.
- Sample size is very small (two seeds, two difficulty targets). This does **not** prove that no parameter values could work, and no original-generation behaviour was changed in main or the active automation branch.

## Decision

**Do not merge these parameter values.** Use this as a screening result, then evaluate a more targeted parameter search with two independent objectives: meaningful difficulty and uniqueness with acceptable solver cost. Candidate avenues include a controlled single-cage quota, arithmetic information/ambiguity measures, and the proportion of distinct zero-result cages. Do not silently change division semantics, thresholds, exact-one-solution, or human deduction-only rules.

If carefully selected parameter families cannot satisfy both constraints on reproducible representative seeds, a bounded feedback-driven generator (retaining and refining accepted intermediate structures) becomes justified. Keep the original algorithm as a fallback until the new approach proves better.
