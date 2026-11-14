package org.piepmeyer.gauguin.difficulty.human

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import org.piepmeyer.gauguin.creation.MergingCageGridCalculator
import org.piepmeyer.gauguin.creation.RandomPossibleDigitsShuffler
import org.piepmeyer.gauguin.creation.SeedRandomizerMock
import org.piepmeyer.gauguin.difficulty.GameDifficultyRater
import org.piepmeyer.gauguin.grid.Grid
import org.piepmeyer.gauguin.grid.GridSize
import org.piepmeyer.gauguin.options.DifficultySetting
import org.piepmeyer.gauguin.options.GameOptionsVariant
import org.piepmeyer.gauguin.options.GameVariant

/**
 * Diagnostic checks against genuine generated puzzles rather than hand-built
 * strategy fixtures. The chosen difficulty changes cage generation; the
 * independently assigned classical difficulty may be different.
 *
 * This is a limited seeded smoke test, not certification of every strategy
 * or proof that the labels match a beginner's experience.
 */
class HumanLogicDifficultyMatrixTest :
    FunSpec({
        val size = System.getenv("GAUGUIN_AUDIT_SIZE")?.toIntOrNull() ?: 9
        require(size in setOf(4, 9, 10, 11)) { "Unsupported audit size: $size" }

        test("generated Very Easy target: no incorrect human placements or candidate erasures on ${size}x$size") {
            val seed = 0
            val options =
                GameOptionsVariant.createClassic().copy(
                    difficultiesSetting = setOf(DifficultySetting.VERY_EASY),
                )
            val variant = GameVariant(GridSize(size, size), options)
            val randomizer = SeedRandomizerMock(seed)
            val grid =
                MergingCageGridCalculator(
                    variant,
                    randomizer,
                    RandomPossibleDigitsShuffler(randomizer.random),
                ).calculate()

            withClue("generated grid has valid stored solutions, size $size") {
                grid.cells.all { it.value in variant.possibleDigits } shouldBe true
                grid.cages.flatMap { it.cells }.map { it.cellNumber }.sorted() shouldBe
                    grid.cells.map { it.cellNumber }.sorted()
            }

            val assignedDifficulty = GameDifficultyRater().difficulty(grid)

            for (avoidNishio in listOf(true, false)) {
                val work = grid.copyWithEmptyUserValues()
                val humanSolver = HumanSolver(work, validate = true, avoidNishio = avoidNishio)
                humanSolver.prepareGrid()

                // No reveals: we want a genuine human-logic solve, not the hidden answer.
                val result = humanSolver.solveAndCalculateDifficulty(avoidReveal = true)

                val incorrectPlacements =
                    work.cells.filter { it.isUserValueSet && it.userValue != it.value }
                val lostSolutionCandidates =
                    work.cells.filter { !it.isUserValueSet && it.value !in it.possibles }

                println(
                    "LOGIC_MATRIX_RESULT size=$size seed=$seed requested=VERY_EASY " +
                        "classicalAssigned=$assignedDifficulty avoidNishio=$avoidNishio " +
                        "humanSolved=${result.success} usedNishio=${result.usedNishio} " +
                        "humanScore=${result.difficulty} " +
                        "filled=${work.cells.count { it.isUserValueSet }}/${work.cells.size} " +
                        "incorrect=${incorrectPlacements.size} " +
                        "lostTrueCandidates=${lostSolutionCandidates.size} " +
                        "cages=${grid.cages.size}",
                )

                withClue("wrong value, size $size, avoidNishio=$avoidNishio") {
                    incorrectPlacements.size shouldBe 0
                }
                withClue("true value removed as candidate, size $size, avoidNishio=$avoidNishio") {
                    lostSolutionCandidates.size shouldBe 0
                }
                withClue("success flag agrees with solved state, size $size, avoidNishio=$avoidNishio") {
                    result.success shouldBe work.isSolved()
                }
            }
        }
    })
