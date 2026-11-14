package org.piepmeyer.gauguin.difficulty.human.strategy

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import org.piepmeyer.gauguin.difficulty.human.HumanSolverCacheImpl
import org.piepmeyer.gauguin.difficulty.human.HumanSolverStrategyResult
import org.piepmeyer.gauguin.grid.Grid
import org.piepmeyer.gauguin.grid.GridSize
import org.piepmeyer.gauguin.options.GameOptionsVariant
import org.piepmeyer.gauguin.options.GameVariant

/**
 * Explicitly exercises both rectangle patterns of the strategy that was not
 * triggered by the random candidate-oracle test. The test grid is a complete
 * valid 4x4 Latin-square solution with candidate supersets containing its
 * true numbers. Cages are not needed: XWing uses only row/column constraints.
 *
 * This is a narrow soundness regression, not proof for arbitrary states.
 */
class XWingOracleRegressionTest :
    FunSpec({
        for (samePairOnRows in listOf(true, false)) {
            test("XWing preserves a valid solution, row-pair mode=$samePairOnRows") {
                val variant = GameVariant(GridSize(4, 4), GameOptionsVariant.createClassic())
                val grid = Grid(variant)
                grid.cells.forEach { cell ->
                    cell.value = (cell.row + cell.column) % 4 + 1
                    cell.possibles = variant.possibleDigits
                }

                // top-left=1, top-right=2, bottom-left=2, bottom-right=3.
                val topLeft = grid.getValidCellAt(0, 0)
                val topRight = grid.getValidCellAt(0, 1)
                val bottomLeft = grid.getValidCellAt(1, 0)
                val bottomRight = grid.getValidCellAt(1, 1)

                topLeft.possibles = setOf(1, 2)
                bottomRight.possibles = setOf(2, 3)
                if (samePairOnRows) {
                    topRight.possibles = setOf(1, 2)
                    bottomLeft.possibles = setOf(2, 3)
                } else {
                    topRight.possibles = setOf(2, 3)
                    bottomLeft.possibles = setOf(1, 2)
                }

                grid.cells.all { it.value in it.possibles } shouldBe true

                val before = grid.cells.map { it.possibles.toSet() }
                // The targeted strategy does not use the cage combination cache.
                val result = XWing().fillCells(grid, HumanSolverCacheImpl(grid))

                (result is HumanSolverStrategyResult.Success) shouldBe true
                val success = result as HumanSolverStrategyResult.Success
                val changed = grid.cells.filterIndexed { index, cell ->
                    before[index] != cell.possibles
                }

                changed.isNotEmpty() shouldBe true
                success.changedCells.map { it.cellNumber }.toSet()
                    .containsAll(changed.map { it.cellNumber }) shouldBe true

                // All 16 cells still allow their true value; every removal
                // is therefore consistent with the known complete solution.
                grid.cells.all { it.value in it.possibles } shouldBe true
                grid.cells.none { it.isUserValueSet && it.userValue != it.value } shouldBe true
            }
        }
    })
