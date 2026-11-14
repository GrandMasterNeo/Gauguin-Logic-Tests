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
 * A stale pencil candidate must not cause a hidden-single inference to
 * duplicate a number that is already entered in the same line.
 *
 * Other cells' candidates can be stale after loading or undo; placements
 * remain authoritative. This regression does not change user pencil marks.
 */
class SinglePossibleInLineStaleCandidateTest :
    FunSpec({
        test("do not duplicate a digit already placed in a complete row") {
            val variant = GameVariant(GridSize(4, 4), GameOptionsVariant.createClassic())
            val grid = Grid(variant)
            grid.cells.forEach { cell ->
                cell.value = (cell.row + cell.column) % 4 + 1
                cell.possibles = variant.possibleDigits
            }
            val alreadyPlaced = grid.getValidCellAt(0, 0)
            alreadyPlaced.userValue = 1
            alreadyPlaced.possibles = emptySet()

            val stale = grid.getValidCellAt(0, 1)
            stale.possibles = setOf(1, 2)
            for (col in 2 until 4) {
                grid.getValidCellAt(0, col).possibles = setOf(2, 3, 4)
            }

            // Digit 1 is already present in the row: position (0,1) must not
            // be filled with 1 merely because no *unfilled* peer offers it.
            val result = SinglePossibleInLine().fillCells(grid, HumanSolverCacheImpl(grid))
            (result is HumanSolverStrategyResult.NothingChanged) shouldBe true
            stale.userValue shouldBe null
            alreadyPlaced.userValue shouldBe 1
            grid.cells.count { it.isUserValueSet && it.userValue != it.value } shouldBe 0
        }
    })
