# Mathematical proof obligations for Gauguin's 37 human strategies

**Status: active review, not a claim of formal program verification.**
**Working Android v8 remains unchanged.** Source review concerns the imported
Gauguin v0.52.1 human-solver engine on `logic-matrix-audit`.

This ledger connects each strategy's intended *mathematical inference* to the
implementation assumptions required for the inference to preserve every
compatible complete solution. It is not enough for a deduction to agree with
the single hidden solution of a uniquely generated puzzle: the inference must
be valid for **every** solution compatible with the given clues and candidates.

## Global preconditions and shared lemmas

Let `S` be the set of currently admissible complete grid assignments. A
deduction is sound exactly when its effect never removes an assignment in
`S`. When a puzzle already has a uniquely determined answer, `S` is a
singleton, but our direct strategy tests also use multi-solution states.

- **L1: candidate feasibility.** Every possible true value for an unfilled cell
  is included in that cell's candidates; a placed value is authoritative.
  Hidden-single strategies must not ignore a digit already placed in a line.
  Stale notes may contain extra values, but may never justify discarding a
  value used by a member of `S`.
- **L2: exhaustive cage combinations.** For each cage `C`, `P(C)` must
  contain every arithmetic- and geometry-valid tuple consistent with the
  currently placed values and candidates. An empty `P(C)` is a contradiction,
  not a vacuous universal truth. This is a *critical* prerequisite used in many
  strategies. Independent enumeration is being introduced as a separate test.
- **L3: full-line all-different/completeness.** Every completed-length row or
  column contains each permitted digit once. In square `n × n` boards, every
  row and column has exactly `n` cells, each digit occurs `n` times overall,
  and the sum of each line is `T = sum(possibleDigits)`. Only lines actually
  meeting this completeness property may use the full-line sum/count argument.
- **L4: cage cardinality.** If a group of `k` cells has only `k` candidate
  digits, those cells use those digits exactly once, so other cells in the
  same all-different line cannot use them.
- **L5: exhaustive arithmetic bounds.** If all cage contributions except
  one are fixed, the remainder is forced. If other contributions are bounded
  by minima/maxima, the unknown must be inside the remaining feasible
  interval. Analogously, arithmetic modulo 2 gives parity deductions.
- **L6: contradiction by assumption.** Assuming a candidate `v` is valid
  and deriving a genuine contradiction proves `v` impossible. **Finishing**
  a speculative branch without contradiction is *not* sufficient justification
  to commit its guessed values. Trial checks must include duplicate digits,
  completed arithmetic, empty cell candidates, impossible unfinished cages,
  and missing mandatory line digits where applicable.

## Registry-by-registry proof obligations

The first column is **exactly the enum entry in
`HumanSolverStrategies.kt`**. These are concise mathematical proof sketches,
not certifications of implementation correctness.

| # | Registry entry | Valid inference / proof obligation | Relies on |
|---:|---|---|---|
| 1 | `ASinglePossibleInCell` | If a cell has exactly one feasible value, that value is forced. | L1 |
| 2 | `ARemovePossibleWithoutCombination` | A value absent at the cell's position in every viable cage tuple cannot be assigned there. | L1, L2 |
| 3 | `ASinglePossibleInLine` | In a complete line, an unplaced digit with exactly one remaining candidate position is forced there; **must exclude digits already placed elsewhere in line**. | L1, L3 |
| 4 | `ARemoveImpossibleCombinationInLineBecauseOfSingleCell` | Reject an otherwise available cage candidate when its unique cage tuple would consume every candidate of a different cell in the same line, or overlap a value forced into that line by another cage. | L1–L3 |
| 5 | `ANakedPair` | Two unfilled cells with the same two candidates in an all-different line use both; remove them from other line cells. | L1, L4 |
| 6 | `APossibleMustBeContainedInSingleCageInLine` | When only one cage can supply mandatory digit `d` in a complete line, restrict that cage to tuples placing `d` in the line. | L2, L3 |
| 7 | `APossibleMustBeContainedInSingleCageInLineDeleteFromOtherCages` | If every viable tuple of a cage places `d` in a line, no outside cell of the same line may use `d`. | L2, L3 |
| 8 | `ANakedTriple` | Three unfilled line cells whose candidate union has cardinality three exhaust those three digits. | L1, L4 |
| 9 | `ANakedQuartet` | Four unfilled line cells whose candidate union has cardinality four exhaust those four digits. | L1, L4 |
| 10 | `AHiddenPair` | A two-candidate cell plus a disjoint cage which must contain at least one of the same two digits in that line force their use between those positions; other line cells exclude both. | L2–L4 |
| 11 | `ASinglePossibleExhaustingTwoLines` | A digit appears exactly twice in two complete parallel lines; mandatory occurrences in other cages bound repetitions of that digit in the target cage. | L2, L3 |
| 12 | `ATwoCagesTakeAllPossiblesTwoLines` | Two fully contained cages force at least two appearances of `d` in two complete lines, exhausting the capacity for `d`. | L2, L3 |
| 13 | `ATwoCagesTakeAllPossiblesThreeLines` | As above for three lines; minimum forced appearances must equal three. | L2, L3 |
| 14 | `ASingleLinePossiblesSum` | Subtract the known cage-part sums from one complete line's fixed total; filter one unknown cage part by the exact remainder. | L2, L3, L5 |
| 15 | `ATwoCellsPossiblesSumSingleLine` | If exactly two line cells contribute an unknown sum, remove any value without a matching complement in the second cell. | L1, L3, L5 |
| 16 | `ATwoCellsPossiblesSumTwoLines` | Same forced-complement reasoning over two complete lines. | L1, L3, L5 |
| 17 | `ATwoCellsPossiblesSumThreeLines` | Same forced-complement reasoning over three complete lines. | L1, L3, L5 |
| 18 | `AGridNumberOfCagesWithPossibleForcesPossibleInCage` | There are exactly `n` copies of each digit on a square board; subtract guaranteed occurrences in static cages to constrain remaining dynamic cages. | L2, L3 |
| 19 | `AMinMaxSumOneLine` | Candidate cage-part sums must leave a remaining sum between the other cage parts' minimum and maximum possible sums. | L2, L3, L5 |
| 20 | `AOddEvenCheckSumSingle` | The parity of the only parity-variable cage part is forced by the total parity and the other fixed cage-part parities. | L2, L3, L5 |
| 21 | `ADetectPossiblesBreakingOtherCagesPossiblesDualLines` | A forced occurrence of `d` in a second cage makes a first cage's hypothetical double-`d` tuple impossible when two lines contain only two occurrences of `d`. | L2, L3 |
| 22 | `ADetectPossibleUsedInLinesByOtherCagesDualLines` | Two separate cages each guarantee `d` in two lines; these two occurrences exhaust those lines, excluding `d` from other cells. | L2, L3 |
| 23 | `AGridEachCageWithPossibleMustIncludePossibleOnce` | If exactly `n` cages can contain a digit and each can contain it at most once, the required `n` overall occurrences force one in every cage. | L2, L3 |
| 24 | `ADualLinesPossiblesSum` | The total sum of two complete lines minus fixed cage-part sums forces the one remaining cage-part sum. | L2, L3, L5 |
| 25 | `AOddEvenCheckSumDual` | Same parity conservation over two complete lines. | L2, L3, L5 |
| 26 | `AXWing` | Its rectangle's two same-pair rows/columns force each common candidate to occupy one selected position per relevant line; only then may it be removed from other visible cells. **Requires careful inspection of both equality arrangements and filled-cell filtering.** | L1, L3–L4 |
| 27 | `AXWingSameCage` | If every feasible cage tuple assigns complementary pair values to two diagonal cells, both values are excluded from their two common row-column intersections. Never infer from an empty cage tuple set. | L2, L3 |
| 28 | `AYWing` | Two bivalue wings each see a common bivalue pivot and share an alternative `z`; one wing must be `z`, so the cell visible to both wings cannot be `z`. | L1, L3 |
| 29 | `APairOfPossiblesExhaustingTwoLines` | Each of two digits occurs twice in two complete lines. Lower bounds forced by other cages cap how many occurrences the target cage may contribute. **The code's set intersection must not be confused with multiplicity; undercounting is conservative, overcounting is unsafe.** | L2, L3 |
| 30 | `AMinMaxSumTwoLines` | As #19 for two complete lines. | L2, L3, L5 |
| 31 | `ATripleLinesPossiblesSum` | As #14 for three complete lines. | L2, L3, L5 |
| 32 | `AOddEvenCheckSumTriple` | As #20 for three complete lines. | L2, L3, L5 |
| 33 | `AGridSumEnforcesCageSum` | All cages partition the board; if all but one have fixed sums, the remaining cage sum is the board's fixed total minus those sums. | L2, L3, L5 |
| 34 | `AGridSumOddEvenCheck` | As #33 modulo 2. | L2, L3, L5 |
| 35 | `AMinMaxSumThreeLines` | As #19 for three complete lines. | L2, L3, L5 |
| 36 | `ANishioWithPairs` | Assume one candidate in a bivalue cell; if trial propagation establishes contradiction, the other candidate is forced. Do not commit speculative solution paths. | L2, L3, L6 |
| 37 | `AAdvancedNishioWithPairs` | Assume one candidate in a cell with more than two candidates; if contradictory, remove *only that candidate* until further proof. | L2, L3, L6 |

## Evidence and remaining gaps

- The prior no-reveal integration tests and per-strategy tracers found no lost
  stored answers over their covered seeds. That alone is not a general proof.
- The initial two-solution state audit passed 444 checks, and its extended
  96-variation version passed 3,552 checks on the corrected Nishio branch.
  All 37 registry entries were invoked; only some were triggered in each
  sampled state. See the JUnit reports of the matching workflows.
- The stronger enumerated-state oracle independently enumerates all 576 valid
  4×4 Latin squares before each deduction. Its first run exposed a **real
  conditional flaw** in `ASinglePossibleInLine`: with a stale candidate mask,
  a digit already placed in a line could be placed again. A guard and a focused
  regression test have been added on the draft branch; their final CI outcome
  must be inspected.
- A separate independent reference oracle compares the core cage combination
  generator to exhaustive arithmetic and row/column geometry. Until that
  oracle passes, **L2 remains an unverified prerequisite**, not an axiom we
  can silently assume.
- Confirm row/column completeness checks for all supported non-square and
  nonstandard-digit variants; the 37 strategies were built around the full-line
  constraints and cannot be applied indiscriminately to partial lines.
- Inspect all rare strategy paths, empty-cache behavior, and trial solver
  performance; no finite empirical corpus constitutes universal formal proof.

## Review policy

**Do not** label a strategy proven merely because it was invoked without
changing a cell, nor because an algorithm agreed with one known solution.
Accept a production change only with a stated inference invariant, at least
one positive fixture, at least one negative/counterexample fixture where
possible, a passing all-feasible-solutions oracle where tractable, and no
unacceptable regression on large-board performance.

This ledger is a proof plan and an audit record; it is **not** a declaration
that every implementation has been mathematically certified.
