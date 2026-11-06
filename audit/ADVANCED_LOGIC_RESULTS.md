# Advanced human-logic safety audit — 2026-11-05

## Scope

The `AdvancedDifficultyStrategySafetyTest` uses the *default* square-board
`RandomCageGridCalculator` (classical difficulty filtering and DLX uniqueness)
on 9×9, 10×10 and 11×11 grids. It generates two deterministic seeds (0 and 1)
for each of MEDIUM, HARD and EXTREME. It traces the human solver strategy
registry in normal priority order, stops when no strategy makes progress, and
**never reveals hidden answers**.

Unlike a final-state test, each strategy invocation is followed by assertions
that it has not placed a wrong number, eliminated the actual solution candidate,
silently modified cells, or misreported modified cells. A reported success with
no changed cell also fails the test. Stored solution values are used only as a
test oracle, never to make deductions.

## Independently verified GitHub Actions result

- Run: https://github.com/GrandMasterNeo/Gauguin-Logic-Tests/actions/runs/42135903329
- Workflow: `Advanced human strategy safety (Medium Hard Extreme)`
- **All three jobs succeeded**. All three JUnit test cases have **zero failures
  and zero errors**, as verified in the downloaded JUnit XML artifacts
  `advanced-strategy-9`, `advanced-strategy-10`, and `advanced-strategy-11`.
- Tests exercised **25,612 strategy invocations** and **8,743 successful
  deduction steps**, covering **24 of the 37 registered strategies**. The other
  13 strategies were not observed making successful deductions in these seeds.

| Size | Medium solved | Hard solved | Extreme solved | Combined |
| --- | ---: | ---: | ---: | ---: |
| 9×9 | 2/2 | 2/2 | 1/2 | 5/6 |
| 10×10 | 2/2 | 1/2 | 1/2 | 4/6 |
| 11×11 | 2/2 | 2/2 | 1/2 | 5/6 |
| **Total** | **6/6** | **5/6** | **3/6** | **14/18** |

### Reproducible stalled puzzles

All four were already verified by the default generator as unique-solution
puzzles of their requested classical difficulty. A lack of progress means this
37-strategy solver cannot finish *in the tested state* without revealing an
answer; it does not mean the mathematical puzzle is unsolvable.

| Board | Requested difficulty | Seed | Progress |
| --- | --- | ---: | --- |
| 9×9 | EXTREME | 1 | 5/81 cells filled; 167 sound deduction steps |
| 10×10 | HARD | 0 | 12/100 cells filled; 312 sound deduction steps |
| 10×10 | EXTREME | 1 | 10/100 cells filled; 314 sound deduction steps |
| 11×11 | EXTREME | 0 | 8/121 cells filled; 415 sound deduction steps |

The remaining 14 puzzles finished fully without hidden-answer reveals. These
are intentionally **diagnostic** tests: incompleteness is reported, not masked
by accepting an incorrect solution or retrofitting the expected outcome.

## Strategy-coverage interpretation

24 distinct strategies made at least one deduction; this includes both
Nishio-based strategies. The registry still contains 37 strategies.

Successful steps by strategy (aggregated over all 18 puzzles):

```text
ARemovePossibleWithoutCombination: 6166
ASinglePossibleInCell: 1253
ARemoveImpossibleCombinationInLineBecauseOfSingleCell: 508
APossibleMustBeContainedInSingleCageInLineDeleteFromOtherCages: 309
APossibleMustBeContainedInSingleCageInLine: 123
ASinglePossibleInLine: 101
AAdvancedNishioWithPairs: 95
ANakedPair: 64
AMinMaxSumOneLine: 27
AMinMaxSumTwoLines: 23
AMinMaxSumThreeLines: 16
ASinglePossibleExhaustingTwoLines: 13
AHiddenPair: 9
ATwoCagesTakeAllPossiblesTwoLines: 9
ANishioWithPairs: 7
APairOfPossiblesExhaustingTwoLines: 4
ADualLinesPossiblesSum: 3
ANakedTriple: 2
AOddEvenCheckSumDual: 2
ANakedQuartet: 2
AOddEvenCheckSumSingle: 2
AOddEvenCheckSumTriple: 2
ATripleLinesPossiblesSum: 2
ASingleLinePossiblesSum: 1
```

The most common two strategies account for most steps. Therefore 25,612
invocations must **not** be treated as mathematical proof of all 37.

## Recommended next checks

1. Broaden seeded samples and variants, especially 10×10 Hard and 9–11×11
   Extreme. Measure rates at which unique-solution puzzles are not completed by
   the no-reveal human strategy suite.
2. Exercise the 13 unobserved strategies in independent valid-state tests and
   inspect strategy-specific fixtures.
3. Examine the four reproducible stalled grids to distinguish a missing
   deduction technique from incomplete candidate propagation or a premature
   solver stop.
4. Compare any contemplated generation filtering with puzzle-generation latency
   before changing production code. On-device new-game behavior and working v8
   are explicitly out of scope.

No APK was built, signed or installed. No production code or existing v8 app
was modified. Leave both pull requests in draft, with PR #2 targeting the
unmerged safety-review branch.
