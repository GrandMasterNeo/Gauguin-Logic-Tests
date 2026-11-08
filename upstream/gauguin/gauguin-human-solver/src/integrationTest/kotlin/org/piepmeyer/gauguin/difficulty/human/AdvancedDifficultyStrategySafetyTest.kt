package org.piepmeyer.gauguin.difficulty.human

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.piepmeyer.gauguin.creation.RandomCageGridCalculator
import org.piepmeyer.gauguin.creation.RandomPossibleDigitsShuffler
import org.piepmeyer.gauguin.creation.SeedRandomizerMock
import org.piepmeyer.gauguin.difficulty.GameDifficultyRater
import org.piepmeyer.gauguin.difficulty.GameDifficultyRatingService
import org.piepmeyer.gauguin.grid.Grid
import org.piepmeyer.gauguin.grid.GridSize
import org.piepmeyer.gauguin.options.DifficultySetting
import org.piepmeyer.gauguin.options.GameOptionsVariant
import org.piepmeyer.gauguin.options.GameVariant

/**
 * Audits every strategy invocation on generated unique-solution puzzles at
 * MEDIUM, HARD and EXTREME. The stored solution is an oracle for assertions
 * only; no strategy is given the solution and no answer is revealed.
 *
 * Unlike a final-state check, this catches loss of a valid candidate, wrong
 * placements, false reports of progress, and incorrect changed-cell reporting
 * immediately after the responsible strategy is invoked.
 */
class AdvancedDifficultyStrategySafetyTest :
    FunSpec({
        val size = System.getenv("GAUGUIN_AUDIT_SIZE")?.toIntOrNull() ?: 9
        val seedCount = System.getenv("GAUGUIN_ADVANCED_SEEDS")?.toIntOrNull() ?: 2
        val requestedLevel = System.getenv("GAUGUIN_ADVANCED_LEVEL")?.takeIf { it.isNotBlank() }
        val requestedSeed = System.getenv("GAUGUIN_ADVANCED_SEED")?.toIntOrNull()
        require(requestedLevel == null || requestedLevel in setOf("MEDIUM", "HARD", "EXTREME"))
        require(requestedSeed == null || requestedSeed >= 0)
        require(size in setOf(9, 10, 11))
        require(seedCount in 1..20)

        test("soundness after each strategy at Medium Hard and Extreme on ${size}x$size") {
            startKoin {
                modules(module {
                    single { GameDifficultyRatingService() }
                })
            }
            try {
                val rater = GameDifficultyRater()
                for (level in listOf(
                    DifficultySetting.MEDIUM,
                    DifficultySetting.HARD,
                    DifficultySetting.EXTREME,
                ).filter { requestedLevel == null || it.name == requestedLevel }) {
                    var solvedWithoutReveal = 0
                    var unsolvedWithoutReveal = 0
                    var attempts = 0
                    var successfulSteps = 0
                    val strategyCounts = mutableMapOf<String, Int>()

                    for (seed in (0 until seedCount).filter { requestedSeed == null || it == requestedSeed }) {
                        val variant = GameVariant(
                            GridSize(size, size),
                            GameOptionsVariant.createClassic().copy(
                                difficultiesSetting = setOf(level),
                            ),
                        )
                        val randomizer = SeedRandomizerMock(seed)
                        val puzzle = RandomCageGridCalculator(
                            variant,
                            randomizer,
                            RandomPossibleDigitsShuffler(randomizer.random),
                        ).calculate()

                        withClue("generated rating size=$size level=$level seed=$seed") {
                            rater.difficulty(puzzle) shouldBe level
                        }
                        val grid: Grid = puzzle.copyWithEmptyUserValues()
                        grid.cells.forEach {
                            it.possibles = variant.possibleDigits
                            it.userValue = null
                        }
                        FillSingleCages().fillCells(grid)
                        val cache = HumanSolverCacheImpl(grid)
                        cache.initialize()
                        cache.validateAllEntries()

                        val maxSteps = grid.cells.size * 20
                        var steps = 0
                        while (!grid.isSolved() && steps < maxSteps) {
                            var progress = false
                            for (strategy in HumanSolverStrategies.entries) {
                                attempts++
                                val before = grid.cells.map { it.userValue to it.possibles.toSet() }

                                val result = withClue(
                                    "size=$size level=$level seed=$seed step=$steps strategy=${strategy.name}",
                                ) {
                                    strategy.solver.fillCells(grid, cache)
                                }

                                val changedIndices = grid.cells.indices.filter { index ->
                                    before[index] !=
                                        (grid.cells[index].userValue to grid.cells[index].possibles)
                                }

                                withClue(
                                    "wrong placement size=$size level=$level seed=$seed " +
                                        "strategy=${strategy.name} step=$steps",
                                ) {
                                    grid.cells.count { it.isUserValueSet && it.userValue != it.value } shouldBe 0
                                }

                                withClue(
                                    "true solution candidate was erased size=$size level=$level " +
                                        "seed=$seed strategy=${strategy.name} step=$steps",
                                ) {
                                    grid.cells.count {
                                        !it.isUserValueSet && it.value !in it.possibles
                                    } shouldBe 0
                                }

                                when (result) {
                                    is HumanSolverStrategyResult.Success -> {
                                        val reportedIndices = result.changedCells.map { it.cellNumber }.toSet()
                                        withClue(
                                            "false success (no mutation) size=$size level=$level seed=$seed " +
                                                "strategy=${strategy.name} step=$steps",
                                        ) {
                                            changedIndices.isNotEmpty() shouldBe true
                                        }
                                        withClue(
                                            "unreported candidate/value changes size=$size level=$level " +
                                                "seed=$seed strategy=${strategy.name} step=$steps",
                                        ) {
                                            reportedIndices.containsAll(changedIndices) shouldBe true
                                        }

                                        strategyCounts[strategy.name] =
                                            (strategyCounts[strategy.name] ?: 0) + 1
                                        successfulSteps++
                                        steps++
                                        cache.validateEntries(result.changedCells)
                                        progress = true
                                    }
                                    is HumanSolverStrategyResult.NothingChanged -> {
                                        withClue(
                                            "silent mutation size=$size level=$level seed=$seed " +
                                                "strategy=${strategy.name} step=$steps",
                                        ) {
                                            changedIndices.isEmpty() shouldBe true
                                        }
                                    }
                                }

                                if (progress) break
                            }
                            if (!progress) break
                        }

                        withClue("step limit size=$size level=$level seed=$seed") {
                            (steps < maxSteps || grid.isSolved()) shouldBe true
                        }

                        if (grid.isSolved()) solvedWithoutReveal++ else unsolvedWithoutReveal++
                        println(
                            "ADVANCED_CASE size=$size level=$level seed=$seed " +
                                "solved=${grid.isSolved()} steps=$steps " +
                                "filled=${grid.cells.count { it.isUserValueSet }}/${grid.cells.size} " +
                                "distinctStrategies=${strategyCounts.size}",
                        )
                    }

                    println(
                        "ADVANCED_SUMMARY size=$size level=$level seeds=$seedCount " +
                            "solved=$solvedWithoutReveal unsolved=$unsolvedWithoutReveal " +
                            "invocations=$attempts successfulSteps=$successfulSteps " +
                            "strategyCounts=${strategyCounts.toSortedMap()}",
                    )
                }
            } finally {
                stopKoin()
            }
        }
    })
