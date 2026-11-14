package org.piepmeyer.gauguin.difficulty.human

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import org.piepmeyer.gauguin.creation.cage.GridCageType
import org.piepmeyer.gauguin.grid.Grid
import org.piepmeyer.gauguin.grid.GridCage
import org.piepmeyer.gauguin.grid.GridCageAction
import org.piepmeyer.gauguin.grid.GridSize
import org.piepmeyer.gauguin.options.GameOptionsVariant
import org.piepmeyer.gauguin.options.GameVariant
import kotlin.random.Random

/**
 * Small-state model checking, not just agreement with a stored solution:
 *
 * All 576 4x4 Latin squares are enumerated independently, then a puzzle with
 * four horizontal addition cages (10+) is formed. Every Latin square satisfies
 * every cage. For each sampled partial-candidate board, ALL complete solutions
 * consistent with the input (not just a chosen one or two) are retained.
 *
 * Every registered deduction must preserve this entire feasible-solution set.
 * The oracle does not feed the solver answers: enumeration is only used in
 * assertions after the strategy invocation.
 */
class EnumeratedSolutionSoundnessTest :
    FunSpec({
        val variations = System.getenv("GAUGUIN_ENUMERATED_VARIATIONS")?.toIntOrNull() ?: 32
        require(variations in 1..100)

        fun permutations(input: List<Int>): List<List<Int>> =
            if (input.isEmpty()) {
                listOf(emptyList())
            } else {
                input.flatMap { n ->
                    permutations(input - n).map { listOf(n) + it }
                }
            }

        val rows = permutations(listOf(1, 2, 3, 4))
        val allSolutions = buildList {
            for (r0 in rows) for (r1 in rows) {
                if ((0 until 4).any { r0[it] == r1[it] }) continue
                for (r2 in rows) {
                    if ((0 until 4).any { r2[it] == r0[it] || r2[it] == r1[it] }) continue
                    for (r3 in rows) {
                        if ((0 until 4).any {
                                setOf(r0[it], r1[it], r2[it], r3[it]).size != 4
                            }) continue
                        add((r0 + r1 + r2 + r3).toIntArray())
                    }
                }
            }
        }
        require(allSolutions.size == 576) {
            "Independent Latin square oracle must have exactly 576 solutions"
        }

        fun puzzle(stored: IntArray): Grid {
            val variant = GameVariant(GridSize(4, 4), GameOptionsVariant.createClassic())
            val grid = Grid(variant)
            grid.cells.forEach { cell ->
                cell.value = stored[cell.cellNumber]
                cell.possibles = variant.possibleDigits
                cell.userValue = null
            }
            for (row in 0 until 4) {
                val cage = GridCage.createWithCells(
                    row, grid, GridCageAction.ACTION_ADD,
                    grid.getValidCellAt(row, 0), GridCageType.FOUR_HORIZONTAL,
                )
                cage.result = 10
                grid.addCage(cage)
            }
            return grid
        }

        test("every strategy preserves all feasible solutions in 4x4 enumerated model") {
            var tested = 0
            var validCompletionComparisons = 0
            val fired = mutableMapOf<String, Int>()
            val minCompletions = mutableListOf<Int>()

            for (variation in 0 until variations) {
                val firstIndex = (variation * 13 + 4) % allSolutions.size
                val secondIndex = (firstIndex + (variation * 7 + 1)) % allSolutions.size
                val thirdIndex = (firstIndex + (variation * 11 + 97)) % allSolutions.size
                val required = listOf(
                    allSolutions[firstIndex],
                    allSolutions[secondIndex],
                    allSolutions[thirdIndex],
                )
                val random = Random(1549 + variation * 71)

                // Deliberately retain every selected complete solution, then
                // sometimes add unrelated candidates and safe placements.
                val masks = (0 until 16).map { i ->
                    val mustKeep = required.map { it[i] }.toSet()
                    val extras = (1..4).filterNot { it in mustKeep }.shuffled(random)
                    val extraCount = when (variation % 4) {
                        0 -> 0
                        1 -> minOf(1, extras.size)
                        2 -> if (extras.isEmpty()) 0 else random.nextInt(extras.size + 1)
                        else -> extras.size
                    }
                    mustKeep + extras.take(extraCount)
                }
                val commonPositions = (0 until 16).filter { i ->
                    required.map { it[i] }.distinct().size == 1
                }
                val placedIndices = commonPositions.shuffled(random).take(variation % 4).toSet()

                val feasibleBefore = allSolutions.filter { solution ->
                    (0 until 16).all { i ->
                        solution[i] in masks[i] &&
                            (i !in placedIndices || solution[i] == required[0][i])
                    }
                }
                withClue("at least the three seed Latin squares survive variation=$variation") {
                    feasibleBefore.size shouldBe
                        allSolutions.count { candidate ->
                            (0 until 16).all { i ->
                                candidate[i] in masks[i] &&
                                    (i !in placedIndices || candidate[i] == required[0][i])
                            }
                        }
                    (feasibleBefore.size >= 2) shouldBe true
                }
                minCompletions += feasibleBefore.size

                for (strategy in HumanSolverStrategies.entries) {
                    val grid = puzzle(required[0])
                    grid.cells.forEach { cell ->
                        cell.possibles = masks[cell.cellNumber]
                        if (cell.cellNumber in placedIndices) {
                            cell.userValue = required[0][cell.cellNumber]
                            cell.possibles = emptySet()
                        }
                    }
                    val cache = HumanSolverCacheImpl.createValidatedCache(grid)
                    val before = grid.cells.map { it.userValue to it.possibles.toSet() }
                    tested++

                    val result = withClue("variation=$variation strategy=${strategy.name}") {
                        strategy.solver.fillCells(grid, cache)
                    }

                    val lost = feasibleBefore.filter { solution ->
                        grid.cells.any { cell ->
                            if (cell.isUserValueSet) {
                                cell.userValue != solution[cell.cellNumber]
                            } else {
                                solution[cell.cellNumber] !in cell.possibles
                            }
                        }
                    }
                    withClue(
                        "unsound deduction variation=$variation strategy=${strategy.name} " +
                            "previousSolutions=${feasibleBefore.size} lost=${lost.size}",
                    ) {
                        lost.size shouldBe 0
                    }
                    validCompletionComparisons += feasibleBefore.size

                    val changed = grid.cells.indices.filter { i ->
                        before[i] != (grid.cells[i].userValue to grid.cells[i].possibles)
                    }
                    when (result) {
                        is HumanSolverStrategyResult.Success -> {
                            fired[strategy.name] = (fired[strategy.name] ?: 0) + 1
                            withClue("false success ${strategy.name}") {
                                changed.isNotEmpty() shouldBe true
                            }
                            withClue("unreported changes ${strategy.name}") {
                                result.changedCells.map { it.cellNumber }.toSet()
                                    .containsAll(changed) shouldBe true
                            }
                        }
                        is HumanSolverStrategyResult.NothingChanged -> {
                            withClue("silent changes ${strategy.name}") {
                                changed.isEmpty() shouldBe true
                            }
                        }
                    }
                }
            }

            println(
                "ENUMERATED_ORACLE variations=$variations " +
                    "latinSquares=${allSolutions.size} strategyChecks=$tested " +
                    "solutionPreservationComparisons=$validCompletionComparisons " +
                    "minCompatible=${minCompletions.min()} " +
                    "averageCompatible=${minCompletions.average()} " +
                    "maxCompatible=${minCompletions.max()} " +
                    "triggered=${fired.size}/${HumanSolverStrategies.entries.size} " +
                    "triggerCounts=${fired.toSortedMap()}",
            )
        }
    })
