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
 * Mathematical soundness stress test for the entire 37-strategy registry.
 *
 * Every input is a 4x4 board partitioned into four horizontal addition cages
 * (sum 10 each). All 4x4 Latin squares satisfy every cage. We construct two
 * distinct Latin squares A and B, then provide candidate masks that include
 * both values at every position. Both A and B are therefore legal complete
 * solutions in *every* starting candidate state.
 *
 * No deduction may remove A OR B, regardless of which solution happens to be
 * stored in GridCell.value. Earlier tests protected only that stored value.
 *
 * These tests expose unsound deductions, but passing does not prove general
 * mathematical correctness or measure strategy usefulness.
 */
class AllStrategyTwoSolutionsOracleTest :
    FunSpec({
        val variations = System.getenv("GAUGUIN_TWO_SOLUTIONS_VARIATIONS")?.toIntOrNull() ?: 12
        require(variations in 1..100)

        fun a(row: Int, col: Int): Int = (row + col) % 4 + 1

        fun alternative(mode: Int, row: Int, col: Int): Int {
            val initial = a(row, col)
            return when (mode) {
                0 -> when (initial) { 1 -> 2; 2 -> 1; else -> initial }
                1 -> when (initial) { 1 -> 3; 3 -> 1; else -> initial }
                2 -> (initial % 4) + 1
                3 -> a(when (row) { 0 -> 1; 1 -> 0; else -> row }, col)
                4 -> a(row, when (col) { 0 -> 1; 1 -> 0; else -> col })
                else -> 5 - initial
            }
        }

        fun buildGrid(): Grid {
            val variant = GameVariant(GridSize(4, 4), GameOptionsVariant.createClassic())
            val grid = Grid(variant)
            grid.cells.forEach { it.value = a(it.row, it.column) }
            for (row in 0 until 4) {
                val cage = GridCage.createWithCells(
                    row,
                    grid,
                    GridCageAction.ACTION_ADD,
                    grid.getValidCellAt(row, 0),
                    GridCageType.FOUR_HORIZONTAL,
                )
                cage.result = 10
                grid.addCage(cage)
            }
            return grid
        }

        test("all registered strategies preserve TWO distinct legal solutions") {
            HumanSolverStrategies.entries.size shouldBe 37

            val triggered = mutableMapOf<String, Int>()
            var checks = 0

            for (variation in 0 until variations) {
                val mode = variation % 6
                val aSolution = IntArray(16) { i -> a(i / 4, i % 4) }
                val bSolution = IntArray(16) { i ->
                    alternative(mode, i / 4, i % 4)
                }
                (aSolution.toList() != bSolution.toList()) shouldBe true

                // Both are Latin squares; every row/column contains 1..4,
                // and all four addition cages total 10.
                for (solution in listOf(aSolution, bSolution)) {
                    for (line in 0 until 4) {
                        (0 until 4).map { solution[line * 4 + it] }.toSet() shouldBe
                            setOf(1, 2, 3, 4)
                        (0 until 4).map { solution[it * 4 + line] }.toSet() shouldBe
                            setOf(1, 2, 3, 4)
                    }
                }

                for (strategy in HumanSolverStrategies.entries) {
                    val grid = buildGrid()
                    val random = Random(4231 + variation * 101)
                    for (cell in grid.cells) {
                        val index = cell.cellNumber
                        val mustKeep = setOf(aSolution[index], bSolution[index])
                        val extraCandidates = grid.variant.possibleDigits
                            .filterNot { it in mustKeep }
                            .shuffled(random)
                        val extras = when (variation % 4) {
                            0 -> emptyList()
                            1 -> extraCandidates.take(1)
                            2 -> extraCandidates.take(random.nextInt(extraCandidates.size + 1))
                            else -> extraCandidates
                        }
                        cell.possibles = mustKeep + extras
                    }

                    val cache = HumanSolverCacheImpl.createValidatedCache(grid)
                    val before = grid.cells.map { it.userValue to it.possibles.toSet() }
                    checks++
                    val result = withClue(
                        "variation=$variation strategy=${strategy.name}",
                    ) {
                        strategy.solver.fillCells(grid, cache)
                    }

                    for (cell in grid.cells) {
                        val index = cell.cellNumber
                        withClue(
                            "BOTH solutions must survive: variation=$variation " +
                                "strategy=${strategy.name} cell=$index, " +
                                "A=${aSolution[index]} B=${bSolution[index]}",
                        ) {
                            if (cell.isUserValueSet) {
                                cell.userValue shouldBe aSolution[index]
                                cell.userValue shouldBe bSolution[index]
                            } else {
                                (aSolution[index] in cell.possibles) shouldBe true
                                (bSolution[index] in cell.possibles) shouldBe true
                            }
                        }
                    }

                    val actuallyChanged = grid.cells.indices.filter { i ->
                        before[i] != (grid.cells[i].userValue to grid.cells[i].possibles)
                    }
                    when (result) {
                        is HumanSolverStrategyResult.Success -> {
                            triggered[strategy.name] = (triggered[strategy.name] ?: 0) + 1
                            withClue("success requires a genuine mutation for ${strategy.name}") {
                                actuallyChanged.isNotEmpty() shouldBe true
                            }
                            withClue("all mutations reported by ${strategy.name}") {
                                result.changedCells.map { it.cellNumber }.toSet()
                                    .containsAll(actuallyChanged) shouldBe true
                            }
                        }
                        is HumanSolverStrategyResult.NothingChanged -> {
                            withClue("NothingChanged must not mutate ${strategy.name}") {
                                actuallyChanged.isEmpty() shouldBe true
                            }
                        }
                    }
                }
            }
            println(
                "TWO_SOLUTION_ORACLE variations=$variations checked=$checks " +
                    "registered=${HumanSolverStrategies.entries.size} " +
                    "triggered=${triggered.size} " +
                    "triggerCounts=${triggered.toSortedMap()}",
            )
        }
    })
