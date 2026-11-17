package org.piepmeyer.gauguin.difficulty.human

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import org.piepmeyer.gauguin.creation.GridCreator
import org.piepmeyer.gauguin.creation.RandomPossibleDigitsShuffler
import org.piepmeyer.gauguin.creation.SeedRandomizerMock
import org.piepmeyer.gauguin.grid.GridSize
import org.piepmeyer.gauguin.options.GameOptionsVariant
import org.piepmeyer.gauguin.options.GameVariant
import kotlin.random.Random

/**
 * Tests strategies that had zero successful deductions in the 18-puzzle
 * advanced matrix. Every seeded candidate arrangement preserves at least
 * one mathematically valid *complete* grid assignment.
 *
 * A strategy that removes that assignment's value is unsound even if it
 * finishes on other puzzles. The candidate arrangements need not be reachable
 * by the current strategy order; this is intentional safety fuzzing and does
 * not measure practical frequency or difficulty.
 *
 * Untriggered strategies are reported, not silently counted as verified.
 */
class RareStrategyCandidateOracleTest :
    FunSpec({
        val size = System.getenv("GAUGUIN_AUDIT_SIZE")?.toIntOrNull() ?: 9
        val variations = System.getenv("GAUGUIN_ORACLE_VARIATIONS")?.toIntOrNull() ?: 30
        require(size in setOf(9, 11))
        require(variations in 1..200)

        val targeted = setOf(
            HumanSolverStrategies.ATwoCagesTakeAllPossiblesThreeLines,
            HumanSolverStrategies.ATwoCellsPossiblesSumSingleLine,
            HumanSolverStrategies.ATwoCellsPossiblesSumTwoLines,
            HumanSolverStrategies.ATwoCellsPossiblesSumThreeLines,
            HumanSolverStrategies.AGridNumberOfCagesWithPossibleForcesPossibleInCage,
            HumanSolverStrategies.ADetectPossiblesBreakingOtherCagesPossiblesDualLines,
            HumanSolverStrategies.ADetectPossibleUsedInLinesByOtherCagesDualLines,
            HumanSolverStrategies.AGridEachCageWithPossibleMustIncludePossibleOnce,
            HumanSolverStrategies.AXWing,
            HumanSolverStrategies.AXWingSameCage,
            HumanSolverStrategies.AYWing,
            HumanSolverStrategies.AGridSumEnforcesCageSum,
            HumanSolverStrategies.AGridSumOddEvenCheck,
        )
        require(targeted.size == 13)

        test("13 rare strategies preserve real solutions on randomized candidate states ${size}x$size") {
            val variant = GameVariant(GridSize(size, size), GameOptionsVariant.createClassic())
            val fired = mutableMapOf<String, Int>()
            var invocations = 0

            for (puzzleSeed in 0..1) {
                val randomizer = SeedRandomizerMock(puzzleSeed)
                val puzzle = GridCreator(
                    variant,
                    randomizer,
                    RandomPossibleDigitsShuffler(randomizer.random),
                ).createRandomizedGridWithCages()

                for (variation in 0 until variations) {
                    for (strategy in targeted) {
                        // Fresh independent grid: no previous strategy can
                        // contaminate the state or create a false positive.
                        val work = puzzle.copyWithEmptyUserValues()
                        work.cells.forEach {
                            it.userValue = null
                            it.possibles = variant.possibleDigits
                        }
                        FillSingleCages().fillCells(work)

                        val random = Random(9973 * puzzleSeed + 101 * variation + size)
                        for (cell in work.cells.filter { !it.isUserValueSet }) {
                            val trueValue = cell.value
                            val otherCandidates =
                                cell.possibles.filter { it != trueValue }.shuffled(random)
                            val maxExtras = when (variation % 6) {
                                0 -> 0
                                1 -> 1
                                2 -> 2
                                3 -> 3
                                4 -> 5
                                else -> size - 1
                            }
                            val allowedExtras = minOf(maxExtras, otherCandidates.size)
                            val extrasToKeep = if (allowedExtras == 0) 0 else
                                random.nextInt(allowedExtras + 1)
                            cell.possibles = (listOf(trueValue) +
                                otherCandidates.take(extrasToKeep)).toSet()
                        }

                        withClue(
                            "input itself must preserve solution size=$size " +
                                "seed=$puzzleSeed variation=$variation strategy=${strategy.name}",
                        ) {
                            work.cells.all {
                                it.isUserValueSet && it.userValue == it.value ||
                                    !it.isUserValueSet && it.value in it.possibles
                            } shouldBe true
                        }

                        val cache = HumanSolverCacheImpl.createValidatedCache(work)
                        val before = work.cells.map { it.userValue to it.possibles.toSet() }
                        invocations++

                        val result = withClue(
                            "invocation size=$size seed=$puzzleSeed variation=$variation " +
                                "strategy=${strategy.name}",
                        ) {
                            strategy.solver.fillCells(work, cache)
                        }

                        val actuallyChanged = work.cells.indices.filter {
                            before[it] != (work.cells[it].userValue to work.cells[it].possibles)
                        }

                        withClue(
                            "invalid number size=$size seed=$puzzleSeed variation=$variation " +
                                "strategy=${strategy.name}",
                        ) {
                            work.cells.count { it.isUserValueSet && it.userValue != it.value } shouldBe 0
                        }
                        withClue(
                            "true value removed size=$size seed=$puzzleSeed variation=$variation " +
                                "strategy=${strategy.name}",
                        ) {
                            work.cells.count { !it.isUserValueSet && it.value !in it.possibles } shouldBe 0
                        }

                        when (result) {
                            is HumanSolverStrategyResult.Success -> {
                                fired[strategy.name] = (fired[strategy.name] ?: 0) + 1
                                withClue("false progress ${strategy.name} seed=$puzzleSeed variation=$variation") {
                                    actuallyChanged.isNotEmpty() shouldBe true
                                }
                                withClue("unreported change ${strategy.name} seed=$puzzleSeed variation=$variation") {
                                    result.changedCells.map { it.cellNumber }.toSet()
                                        .containsAll(actuallyChanged) shouldBe true
                                }
                            }
                            is HumanSolverStrategyResult.NothingChanged -> {
                                withClue("silent mutation ${strategy.name} seed=$puzzleSeed variation=$variation") {
                                    actuallyChanged.isEmpty() shouldBe true
                                }
                            }
                        }
                    }
                }
            }

            println(
                "RARE_ORACLE_SUMMARY size=$size seeds=2 variations=$variations " +
                    "invocations=$invocations triggeredDistinct=${fired.size}/${targeted.size} " +
                    "triggerCounts=${fired.toSortedMap()} " +
                    "untriggered=${targeted.map { it.name }.filterNot { it in fired }.sorted()}",
            )
        }
    })
