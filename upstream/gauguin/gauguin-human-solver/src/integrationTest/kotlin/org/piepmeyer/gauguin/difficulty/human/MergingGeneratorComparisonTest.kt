package org.piepmeyer.gauguin.difficulty.human

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import org.piepmeyer.gauguin.creation.MergingCageGridCalculator
import org.piepmeyer.gauguin.creation.RandomPossibleDigitsShuffler
import org.piepmeyer.gauguin.creation.SeedRandomizerMock
import org.piepmeyer.gauguin.creation.dlx.MathDokuDLXSolver
import org.piepmeyer.gauguin.difficulty.GameDifficultyRater
import org.piepmeyer.gauguin.grid.Grid
import org.piepmeyer.gauguin.grid.GridSize
import org.piepmeyer.gauguin.options.DifficultySetting
import org.piepmeyer.gauguin.options.GameOptionsVariant
import org.piepmeyer.gauguin.options.GameVariant
import kotlin.system.measureNanoTime

/**
 * Alternative-generator feasibility experiment, not a production change.
 *
 * Square boards normally use RandomCageGridCalculator. This experiment instead
 * uses the optional merging algorithm, which starts with many small cages and
 * merges while retaining mathematical uniqueness. It does not guarantee that
 * the *classical rating* equals the requested cage-size target, so report the
 * actual classification instead of asserting it equals EXTREME.
 *
 * Use one deterministic seed per size to avoid claiming statistical
 * calibration from an expensive proof-of-concept.
 */
class MergingGeneratorComparisonTest :
    FunSpec({
        val size = System.getenv("GAUGUIN_MERGE_SIZE")?.toIntOrNull() ?: 9
        require(size in setOf(9, 10, 11))

        test("compare merged Master-target puzzle on ${size}x$size") {
            val variant = GameVariant(
                GridSize(size, size),
                GameOptionsVariant.createClassic().copy(
                    difficultiesSetting = setOf(DifficultySetting.EXTREME),
                ),
            )
            val randomizer = SeedRandomizerMock(0)
            val generator = MergingCageGridCalculator(
                variant, randomizer, RandomPossibleDigitsShuffler(randomizer.random),
            )

            var puzzle: Grid? = null
            val genNs = measureNanoTime {
                puzzle = generator.calculate()
            }
            val grid = puzzle!!

            withClue("grid cages cover exactly the full board") {
                grid.cages.flatMap { it.cells }.map { it.cellNumber }.sorted() shouldBe
                    grid.cells.map { it.cellNumber }.sorted()
            }
            withClue("hidden solution is in the permitted digits") {
                grid.cells.all { it.value in variant.possibleDigits } shouldBe true
            }
            val classification = GameDifficultyRater().difficulty(grid)

            var uniqueness = -1
            val uniqueNs = measureNanoTime {
                uniqueness = MathDokuDLXSolver().solve(grid)
            }
            withClue("merged grid must have precisely one mathematical solution") {
                uniqueness shouldBe 1
            }

            val working = grid.copyWithEmptyUserValues()
            val solver = HumanSolver(working, validate = true)
            solver.prepareGrid()
            var result: HumanSolverResult? = null
            val solveNs = measureNanoTime {
                result = solver.solveAndCalculateDifficulty(avoidReveal = true)
            }
            val solved = result!!
            withClue("no incorrect placement in merged puzzle") {
                working.cells.count { it.isUserValueSet && it.userValue != it.value } shouldBe 0
            }
            withClue("no actual solution candidate erased in merged puzzle") {
                working.cells.count { !it.isUserValueSet && it.value !in it.possibles } shouldBe 0
            }

            println(
                "MERGE_COMPARISON size=$size seed=0 cageTarget=EXTREME " +
                    "actualRating=$classification uniqueSolutions=$uniqueness " +
                    "generateMs=${genNs / 1_000_000} uniquenessMs=${uniqueNs / 1_000_000} " +
                    "humanCheckMs=${solveNs / 1_000_000} humanSolved=${solved.success} " +
                    "usedNishio=${solved.usedNishio} " +
                    "filled=${working.cells.count { it.isUserValueSet }}/${working.cells.size} " +
                    "cageCount=${grid.cages.size} score=${solved.difficulty}",
            )
        }
    })
