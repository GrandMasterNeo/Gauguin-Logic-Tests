package org.piepmeyer.gauguin.difficulty.human.strategy.nishio
import io.kotest.core.spec.style.FunSpec
import org.piepmeyer.gauguin.creation.GridBuilder
import org.piepmeyer.gauguin.creation.cage.GridCageType
import org.piepmeyer.gauguin.difficulty.human.HumanSolverCacheImpl
import org.piepmeyer.gauguin.grid.Grid
class NishioFixtureDiagnostic : FunSpec({
    test("inspect upstream fixture before and after single-cage value metadata repair") {
        val grid = diagnosticGrid()
        grid.cages.forEach { cage ->
            println("cage=${cage.id} result=${cage.result} cells=" + cage.cells.map { "${it.cellNumber}:value=${it.value},user=${it.userValue}" } + " correct=${cage.isUserMathCorrect()}")
        }
        fun trial(): NishioResult {
            val cache = HumanSolverCacheImpl.createValidatedCache(grid)
            return NishioCore(grid, PossiblesCacheByCageNumber(grid, cache), grid.cells[0], 1).tryWithNishio()
        }
        println("original trial=${trial()::class.simpleName}")
        grid.cages.filter { it.cells.size == 1 }.forEach { it.cells.first().value = it.result }
        println("metadata-repaired trial=${trial()::class.simpleName}")
        val result = NishioWithPairs().fillCellsWithNewCacheReturningDetails(grid)
        println("metadata-repaired strategy=${result::class.simpleName} placed=" + grid.cells.filter { it.isUserValueSet }.map { it.cellNumber })
    }
})
private fun diagnosticGrid(): Grid {

    val grid =
        GridBuilder(4, 3)
            .addCageSubtract(1, GridCageType.DOUBLE_HORIZONTAL)
            .addCageAdd(9, GridCageType.ANGLE_LEFT_BOTTOM)
            .addCageSubtract(1, GridCageType.DOUBLE_VERTICAL)
            .addCageMultiply(8, GridCageType.L_HORIZONTAL_SHORT_LEFT_TOP)
            .addCageSingle(3)
            .createGrid()

    grid.cells[0].possibles = setOf(1, 2)
    grid.cells[1].possibles = setOf(2, 3)
    grid.cells[2].userValue = 4
    grid.cells[3].possibles = setOf(1, 3)
    grid.cells[4].possibles = setOf(2, 4)
    grid.cells[5].userValue = 1
    grid.cells[6].userValue = 3
    grid.cells[7].possibles = setOf(2, 4)
    grid.cells[8].userValue = 3
    grid.cells[9].possibles = setOf(2, 4)
    grid.cells[10].possibles = setOf(1, 2)
    grid.cells[11].possibles = setOf(1, 2, 4)
    return grid
}
