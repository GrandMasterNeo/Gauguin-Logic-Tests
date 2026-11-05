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
import org.piepmeyer.gauguin.grid.GridSize
import org.piepmeyer.gauguin.options.DifficultySetting
import org.piepmeyer.gauguin.options.GameOptionsVariant
import org.piepmeyer.gauguin.options.GameVariant

/**
 * A reproducible sample sweep of the *actual default* square-board generator.
 * Every puzzle has passed the built-in classical difficulty filter and DLX
 * uniqueness check before the human solver examines it.
 *
 * A pass means no unsound placement/candidate elimination was found on these
 * seeds. A low solved count is diagnostic: do not change difficulty thresholds
 * or label puzzles from these few samples alone.
 */
class DefaultDifficultySweepTest :
    FunSpec({
        val size = System.getenv("GAUGUIN_AUDIT_SIZE")?.toIntOrNull() ?: 9
        val seedCount = System.getenv("GAUGUIN_SWEEP_SEEDS")?.toIntOrNull() ?: 10
        require(size in setOf(9, 10, 11))
        require(seedCount in 1..100)

        test("default generator Very Easy and Easy, $seedCount seeds, ${size}x$size") {
            startKoin {
                modules(module {
                    single { GameDifficultyRatingService() }
                })
            }

            try {
                val rater = GameDifficultyRater()
                for (level in listOf(DifficultySetting.VERY_EASY, DifficultySetting.EASY)) {
                    var noTrialSolved = 0
                    var withTrialSolved = 0
                    var trialNeeded = 0
                    val scores = mutableListOf<Int>()

                    for (seed in 0 until seedCount) {
                        val variant = GameVariant(
                            GridSize(size, size),
                            GameOptionsVariant.createClassic().copy(
                                difficultiesSetting = setOf(level),
                            ),
                        )
                        val randomizer = SeedRandomizerMock(seed)
                        val grid = RandomCageGridCalculator(
                            variant,
                            randomizer,
                            RandomPossibleDigitsShuffler(randomizer.random),
                        ).calculate()

                        withClue("classical filter size=$size level=$level seed=$seed") {
                            rater.difficulty(grid) shouldBe level
                        }

                        for (avoidNishio in listOf(true, false)) {
                            val working = grid.copyWithEmptyUserValues()
                            val solver = HumanSolver(working, validate = true, avoidNishio = avoidNishio)
                            solver.prepareGrid()

                            // Never reveal stored solution values as a way to finish.
                            val solved = solver.solveAndCalculateDifficulty(avoidReveal = true)
                            val incorrect = working.cells.count {
                                it.isUserValueSet && it.userValue != it.value
                            }
                            val trueCandidateLost = working.cells.count {
                                !it.isUserValueSet && it.value !in it.possibles
                            }

                            withClue("incorrect placement size=$size level=$level seed=$seed avoidNishio=$avoidNishio") {
                                incorrect shouldBe 0
                            }
                            withClue("true candidate removed size=$size level=$level seed=$seed avoidNishio=$avoidNishio") {
                                trueCandidateLost shouldBe 0
                            }
                            withClue("success flag size=$size level=$level seed=$seed avoidNishio=$avoidNishio") {
                                solved.success shouldBe working.isSolved()
                            }

                            if (avoidNishio) {
                                if (solved.success) noTrialSolved++
                                scores.add(solved.difficulty)
                            } else {
                                if (solved.success) withTrialSolved++
                                if (solved.usedNishio) trialNeeded++
                            }
                        }

                        println("SWEEP_CASE size=$size level=$level seed=$seed checked=true")
                    }

                    println(
                        "SWEEP_SUMMARY size=$size level=$level seeds=$seedCount " +
                            "solvedWithoutTrial=$noTrialSolved solvedWithTrial=$withTrialSolved " +
                            "usedTrial=$trialNeeded minHumanScore=${scores.min()} " +
                            "averageHumanScore=${scores.average()} maxHumanScore=${scores.max()}",
                    )
                }
            } finally {
                stopKoin()
            }
        }
    })
