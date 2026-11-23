# Gauguin player-solvability diagnostic — evidence and forward TAFT plan

Status: source-level diagnostic, NOT an exhaustive test pass. Scope: generator, difficulty, human solver, gameplay/reveal, and user-facing puzzle contract. Branch: experiment/generator-constraint-hardening.

## Verified findings

1. **Generation filters by a classical combinatorial difficulty, not deduction-only completion.** `DifficultyAwareGridCreator.isWantedDifficulty` calls `GameDifficultyRatingService.difficultyOfGrid` -> `GameDifficultyRater.difficulty` -> `Grid.ensureDifficultyCalculated()` -> `GridDifficultyCalculator.calculate()`. The calculator takes the natural logarithm of the product of each cage's possible combination count. This is a proxy for search-space size, NOT a proof that a human strategy can finish. `RandomCageGridCalculator` then requires `MathDokuDLX.solve(MULTIPLE) == 1`, which establishes uniqueness but not deduction-only solvability.

2. **Human solver fallback uses the stored answer.** `HumanSolver.solveAndCalculateDifficulty(avoidReveal: Boolean = false)` sets `success=false` when strategies stall; if avoidReveal is false, `revealUnsolvedCell` fills an unresolved cell with `GridCell.value`. A filled grid is therefore not equivalent to deduction-only success. The solver records the number of reveals in a private counter/log, but `HumanSolverResult` exposes only success, usedNishio, difficulty.

3. **Human difficulty metadata exists but is not the generator's acceptance gate.** `HumanDifficultyCalculatorImpl` records `solvedViaHumanDifficulty` and Nishio usage in `GridDifficulty`. Default `avoidNishioAndReveal=false` means a run may include Nishio and fallback reveals. The field `solvedViaHumanDifficultyIncludingNishio` is populated from `usedNishio`; despite its name it is an indicator of Nishio usage, not an independent certification of solving.

4. **Gameplay reveal differs from solver fallback.** `GameSolveService.revealSelectedCell` and `revealSelectedCage` call `Game.revealCell`, which sets `GridCell.isCheated` and replaces an incorrect entry with the stored value. `Game.enterPossibleNumber`, `eraseSelectedCell`, and `longClickOnSelectedCell` block on `isCheated`. `GameSolveService.cheatedOnGame` resets streak statistics. This is an intentional gameplay policy in code, not proof of developer intent to make puzzles impossible.

5. **Restart resets reveal flags.** `Game.restartGame` invokes `Grid.clearUserValues`, which clears user entries and `isCheated`. Thus prior Reveal Cell actions should not remain locked after a normal restart.

6. **The exact Android debug-menu invocation remains unverified.** The repository code reviewed here does not yet establish whether the user's 'Solve via human solver (from start)' action passed `avoidReveal=true`, nor its completion status. Do not claim this particular 11x11 puzzle required fallback.

## Mechanism-design interpretation (hypothesis, not code fact)

The designer controls the feasible information and action space. A unique mathematical solution is a property of the *answer space*; human deductibility is a property of the *information/strategy path*; editability is a property of the *interface action space*. These must be tested separately. Salen & Zimmerman, *Rules of Play* (MIT Press, 2003), analyze rules, uncertainty, information, and meaningful play. Tadelis, *Game Theory: An Introduction* (Princeton University Press, 2013), covers mechanism design and incentive compatibility. These are conceptual lenses, not proof of intent.

## Implementation gates — test -> apply -> fix -> test

A. **Capture reproducible 11x11 artifact**: preserve original cage layout, digit variant, random seed if available, stored answer, and human solver option's exact invocation.

B. **Instrument solver result**: report `success`, `usedNishio`, `revealedCells`, first stalled step, and strategy trace. Ensure no stored `GridCell.value` is consulted on the deduction-only path except optional post-step validation.

C. **Separate certifications**: uniqueness (DLX exactly one); deduction-only success with `avoidReveal=true`; optional Nishio-free success; allowed Nishio cap and policy; gameplay interaction correctness. Fail closed on requested certification, not on unrelated metrics.

D. **Implement an acceptance gate without introducing a core/human-solver circular dependency**: use existing `HumanDifficultyCalculatorFactory` injection or a higher-layer validation service, after checking module dependency graph. Do not automatically require Nishio-free completion for all existing difficulties without reviewing generation feasibility.

E. **Regression matrix**: 3x3–11x11 x all supported difficulties x relevant digit sets/operations; deterministic seeds; verify solution uniqueness, cage arithmetic, correct classification, no fallback reveals, and solver strategy trace. Distinguish unsupported variants from genuine failures. Include direct tests of reveal-overwrite, restart, selection, undo, pencil marks, streak changes, and save/load.

F. **Feedback loop**: report baseline failure distribution and first failing reason; change one policy or algorithmic cause; rerun affected tests; expand to matrix only after local passes. Keep a record of why each constraint exists and what behavior it produces.

## Important limits

This is a targeted multi-module source audit. It does NOT claim all 1,064 files were read or that any new tests have passed. Earlier reveal fix commits are still unverified by CI here. No inference about deliberate manipulation by the original developer is justified.
