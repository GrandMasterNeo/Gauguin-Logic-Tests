package org.piepmeyer.gauguin.difficulty.human.strategy.nishio

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import org.piepmeyer.gauguin.creation.cage.GridCageType
import org.piepmeyer.gauguin.difficulty.human.HumanSolverCacheImpl
import org.piepmeyer.gauguin.grid.Grid
import org.piepmeyer.gauguin.grid.GridCage
import org.piepmeyer.gauguin.grid.GridCageAction
import org.piepmeyer.gauguin.grid.GridSize
import org.piepmeyer.gauguin.options.GameOptionsVariant
import org.piepmeyer.gauguin.options.GameVariant

/**
 * Sound hypothetical-candidate states where one trial is mathematically
 * impossible without creating a single-valued cell or filling an entire
 * cage. Both cases previously returned NothingFound, missing the deduction.
 */
class NishioMissingContradictionRegressionTest :
    FunSpec({
        fun gridWithWildcardSingletonCages(size: Int): Grid {
            val variant = GameVariant(GridSize(size, size), GameOptionsVariant.createClassic())
            val grid = Grid(variant)
            grid.cells.forEach { cell ->
                // Latin square: row zero starts with 2, 3, ... and ends in 1.
                cell.value = (cell.row + cell.column + 1) % size + 1
                cell.possibles = variant.possibleDigits
                val cage = GridCage(
                    cell.cellNumber,
                    true,
                    GridCageAction.ACTION_SINGLE_ALL_POSSIBLES,
                    GridCageType.SINGLE,
                )
                cage.addCell(cell)
                grid.addCage(cage)
            }
            return grid
        }

        test("Nishio rejects a partially filled cage with no possible combination") {
            val variant = GameVariant(GridSize(4, 4), GameOptionsVariant.createClassic())
            val grid = Grid(variant)
            grid.cells.forEach { cell ->
                cell.value = (cell.row + cell.column + 1) % 4 + 1
                cell.possibles = variant.possibleDigits
            }

            // A valid 5+ cage can hold (2,3), but not the trial (1,2/3).
            val addition = GridCage.createWithCells(
                0, grid, GridCageAction.ACTION_ADD,
                grid.getValidCellAt(0, 0), GridCageType.DOUBLE_HORIZONTAL,
            )
            addition.result = 5
            grid.addCage(addition)
            for (cell in grid.cells.filter { it.cage == null }) {
                val wildcard = GridCage(
                    cell.cellNumber + 1, true,
                    GridCageAction.ACTION_SINGLE_ALL_POSSIBLES, GridCageType.SINGLE,
                )
                wildcard.addCell(cell)
                grid.addCage(wildcard)
            }
            grid.getValidCellAt(0, 0).possibles = setOf(1, 2)
            grid.getValidCellAt(0, 1).possibles = setOf(2, 3)

            // The known true 2,3 cage values remain possible.
            grid.cells.all { it.value in it.possibles } shouldBe true
            val cache = HumanSolverCacheImpl.createValidatedCache(grid)
            val core = NishioCore(
                grid, PossiblesCacheByCageNumber(grid, cache),
                grid.getValidCellAt(0, 0), 1,
            )

            core.tryWithNishio()::class shouldBe NishioResult.Contradictions::class
            // A trial must never modify the original grid.
            grid.getValidCellAt(0, 0).possibles shouldBe setOf(1, 2)
            grid.getValidCellAt(0, 0).userValue shouldBe null
        }

        test("Nishio rejects a missing digit in a full row without naked pairs") {
            val grid = gridWithWildcardSingletonCages(5)
            grid.getValidCellAt(0, 0).possibles = setOf(1, 2)
            for (col in 1 until 5) {
                grid.getValidCellAt(0, col).possibles = setOf(1, 3, 4, 5)
            }

            // Real Latin square stays feasible before the assumption: 0,0=2.
            grid.cells.all { it.value in it.possibles } shouldBe true
            val cache = HumanSolverCacheImpl.createValidatedCache(grid)
            val core = NishioCore(
                grid, PossiblesCacheByCageNumber(grid, cache),
                grid.getValidCellAt(0, 0), 1,
            )

            // If the first cell were 1, row zero would have nowhere for 2.
            // There are no single candidates, duplicate values, or naked pairs.
            core.tryWithNishio()::class shouldBe NishioResult.Contradictions::class
            grid.getValidCellAt(0, 0).possibles shouldBe setOf(1, 2)
            grid.getValidCellAt(0, 0).userValue shouldBe null
        }
    })
