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
 * On square boards the default GridCalculatorFactory path is the older
 * RandomCageGridCalculator, not the newer merging calculator. Unlike the
 * merging-algorithm sample, this path filters for the requested classical
 * difficulty and checks uniqueness before returning its generated puzzle.
 */
class HumanLogicDefaultGeneratorMatrixTest :
    FunSpec({
        val size = System.getenv("GAUGUIN_AUDIT_SIZE")?.toIntOrNull() ?: 9
        require(size in setOf(4, 9, 10, 11)) { "Unsupported audit size: $size" }

        test("default square generator Very Easy safety and no-reveal solving on ${size}x$size") {
            val seed = 0
            val options = GameOptionsVariant.createClassic().copy(
                difficultiesSetting = setOf(DifficultySetting.VERY_EASY),
            )
            val variant = GameVariant(GridSize(size, size), options)
            val randomizer = SeedRandomizerMock(seed)

            // Exactly the dependency injected into DifficultyAwareGridCreator.
            startKoin {
                modules(module {
                    single { GameDifficultyRatingService() }
                })
            }
            try {
                val grid =
                    RandomCageGridCalculator(
                        variant,
                        randomizer,
                        RandomPossibleDigitsShuffler(randomizer.random),
                    ).calculate()

                val assignedDifficulty = GameDifficultyRater().difficulty(grid)
                withClue("default generator should honor a supported Very Easy choice") {
                    assignedDifficulty shouldBe DifficultySetting.VERY_EASY
                }
                withClue("generated board has a complete, valid solution") {
                    grid.cells.all { it.value in variant.possibleDigits } shouldBe true
                    grid.cages.flatMap { it.cells }.map { it.cellNumber }.sorted() shouldBe
                        grid.cells.map { it.cellNumber }.sorted()
                }

                for (avoidNishio in listOf(true, false)) {
                    val work = grid.copyWithEmptyUserValues()
                    val humanSolver = HumanSolver(work, validate = true, avoidNishio = avoidNishio)
                    humanSolver.prepareGrid()

                    val result = humanSolver.solveAndCalculateDifficulty(avoidReveal = true)
                    val incorrectPlacements =
                        work.cells.filter { it.isUserValueSet && it.userValue != it.value }
                    val lostTrueCandidates =
                        work.cells.filter { !it.isUserValueSet && it.value !in it.possibles }

                    println(
                        "DEFAULT_MATRIX_RESULT size=$size seed=$seed requested=VERY_EASY " +
                            "classicalAssigned=$assignedDifficulty avoidNishio=$avoidNishio " +
                            "humanSolved=${result.success} usedNishio=${result.usedNishio} " +
                            "humanScore=${result.difficulty} " +
                            "filled=${work.cells.count { it.isUserValueSet }}/${work.cells.size} " +
                            "incorrect=${incorrectPlacements.size} " +
                            "lostTrueCandidates=${lostTrueCandidates.size} cages=${grid.cages.size}",
                    )

                    withClue("wrong placement, size $size, avoidNishio=$avoidNishio") {
                        incorrectPlacements.size shouldBe 0
                    }
                    withClue("valid candidate erased, size $size, avoidNishio=$avoidNishio") {
                        lostTrueCandidates.size shouldBe 0
                    }
                    withClue("reported success must match the filled solution") {
                        result.success shouldBe work.isSolved()
                    }
                }
            } finally {
                stopKoin()
            }
        }
    })
