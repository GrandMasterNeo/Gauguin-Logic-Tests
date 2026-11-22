package org.piepmeyer.gauguin.creation.cage

import io.github.oshai.kotlinlogging.KotlinLogging
import org.piepmeyer.gauguin.Randomizer
import org.piepmeyer.gauguin.grid.Grid
import org.piepmeyer.gauguin.grid.GridCage
import org.piepmeyer.gauguin.grid.GridCageAction
import org.piepmeyer.gauguin.grid.GridCell
import org.piepmeyer.gauguin.options.GridCageOperation
import org.piepmeyer.gauguin.options.SingleCageUsage
import kotlin.math.sqrt

private val logger = KotlinLogging.logger {}

class GridCageCreator(
    private val randomizer: Randomizer,
    private val grid: Grid,
) {
    fun createCages() {
        // A fixed-single-cage layout can leave an uncovered cell with no legal
        // multi-cell cage. Restarting the same grid indefinitely is not progress.
        val maxRestarts = Integer.getInteger("gauguin.generation.maxCageRestarts", 32).coerceAtLeast(0)
        var restarts = 0
        while (true) {
            val restart = createOneCage()
            if (!restart) return
            if (restarts >= maxRestarts) {
                grid.clearAllCages()
                throw CageConstructionExhaustedException(
                    "No valid cage partition after ${restarts + 1} layouts (maxRestarts=$maxRestarts)"
                )
            }
            restarts++
        }
    }

    private fun createOneCage(): Boolean {
        var cageId = 0
        if (grid.options.singleCageUsage == SingleCageUsage.FIXED_NUMBER) {
            cageId = createSingleCages()
        }

        for (cell in grid.cells) {
            if (cell.cellInAnyCage()) {
                continue
            }

            logger.trace { "Calculating cage type..." }
            var cageType: GridCageType? = calculateCageType(cell)
            logger.trace { "Calculating cage type finished." }

            if (cageType == null) {
                // Only possible cage is a single
                if (grid.options.singleCageUsage == SingleCageUsage.DYNAMIC) {
                    cageType = GridCageType.SINGLE
                } else {
                    grid.clearAllCages()
                    logger.trace { "Clearing cages." }
                    return true
                }
            }

            logger.trace { "Calculating cage arithmetic..." }
            val cage = calculateCageArithmetic(cageId++, cell, cageType, grid.options.cageOperation)
            grid.addCage(cage)
            logger.trace { "Calculated cage arithmetic." }
        }

        return false
    }

    private fun calculateCageType(cell: GridCell): GridCageType? {
        val cagesToTry = mutableListOf<GridCageType>()
        cagesToTry += GridCageType.classicCageTypes()
        cagesToTry -= GridCageType.SINGLE

        // Experimental 9x9 Very Hard geometry bias: favor larger cages to
        // counter the observed surplus of low-scoring, high-cage-count grids.
        // Every chosen shape must still satisfy the original geometry checks.
        // All other sizes and difficulty settings retain their prior selection.
        val preferLarge = grid.gridSize.width == 9 &&
            grid.gridSize.height == 9 &&
            grid.options.difficultiesSetting ==
                setOf(org.piepmeyer.gauguin.options.DifficultySetting.VERY_HARD)
        if (preferLarge && randomizer.nextInt(4) != 0) {
            val larger = cagesToTry.filter { it.coordinates.size == 4 }
                .filter { isValidCageType(it, cell, grid) && !wouldStrandUncoveredCell(it, cell) }
            if (larger.isNotEmpty()) {
                return larger.random(randomizer.random())
            }
        }

        while (cagesToTry.isNotEmpty()) {
            val cageType = cagesToTry.random(randomizer.random())

            if (isValidCageType(cageType, cell, grid) && !wouldStrandUncoveredCell(cageType, cell)) {
                return cageType
            }

            cagesToTry -= cageType
        }

        return null
    }

    // With a fixed number of single-cell cages, every other cell must belong
    // to a multi-cell cage. Do not place a cage that isolates an uncovered cell.
    // This is a necessary (not sufficient) feasibility check: it preserves all
    // existing legal cage shapes and arithmetic rules while pruning dead ends.
    private fun wouldStrandUncoveredCell(type: GridCageType, origin: GridCell): Boolean {
        if (grid.options.singleCageUsage != SingleCageUsage.FIXED_NUMBER) return false

        val selected = type.coordinates.map { (dx, dy) ->
            (origin.row + dy) to (origin.column + dx)
        }.toSet()

        return grid.cells.any { cell ->
            val position = cell.row to cell.column
            if (cell.cellInAnyCage() || position in selected) return@any false

            val row = cell.row
            val col = cell.column
            val adjacent = arrayOf(
                (row - 1) to col,
                (row + 1) to col,
                row to (col - 1),
                row to (col + 1),
            )
            adjacent.none { (r, c) ->
                val neighbor = grid.getCellAt(r, c)
                neighbor != null && !neighbor.cellInAnyCage() && (r to c) !in selected
            }
        }
    }

    private fun createSingleCages(): Int {
        val singles = (sqrt(grid.gridSize.surfaceArea.toDouble()) / 2).toInt()
        val rowUsed = BooleanArray(grid.gridSize.height)
        val colUsed = BooleanArray(grid.gridSize.width)
        val valUsed = BooleanArray(grid.gridSize.amountOfNumbers)

        for (cageId in 0 until singles) {
            // Select from eligible cells rather than retrying indefinitely when
            // a randomizer repeatedly chooses an occupied row/column/value.
            val eligible = grid.cells.filter { cell ->
                val digitIndex = grid.options.digitSetting.indexOf(cell.value)
                digitIndex >= 0 &&
                    !rowUsed[cell.row] &&
                    !colUsed[cell.column] &&
                    !valUsed[digitIndex] &&
                    !cell.cellInAnyCage()
            }
            check(eligible.isNotEmpty()) {
                "No eligible cell for fixed single cage $cageId of $singles"
            }
            val cell = eligible[randomizer.nextInt(eligible.size)]
            val cellIndex = grid.options.digitSetting.indexOf(cell.value)

            colUsed[cell.column] = true
            rowUsed[cell.row] = true
            valUsed[cellIndex] = true
            val cage = GridCage.createWithSingleCellArithmetic(cageId, grid, cell)
            grid.addCage(cage)
        }
        return singles
    }

    private fun isValidCageType(
        cageType: GridCageType,
        origin: GridCell,
        grid: Grid,
    ) = cageType.coordinates
        .any {
            val col = origin.column + it.first
            val row = origin.row + it.second
            val c = grid.getCellAt(row, col)

            c == null || c.cellInAnyCage()
        }.not()

    private fun cellsFromCoordinates(
        origin: GridCell,
        cageType: GridCageType,
    ): List<GridCell> =
        cageType.coordinates.toList().map {
            val col = origin.column + it.first
            val row = origin.row + it.second

            grid.getValidCellAt(row, col)
        }

    private fun calculateCageArithmetic(
        id: Int,
        origin: GridCell,
        cageType: GridCageType,
        operationSet: GridCageOperation,
    ): GridCage {
        val cells = cellsFromCoordinates(origin, cageType)

        val decider = GridCageOperationDecider(randomizer, cells, operationSet)
        val operation = decider.decideOperation()

        return if (operation == GridCageAction.ACTION_NONE) {
            GridCage.createWithSingleCellArithmetic(id, grid, origin)
        } else {
            val cage = GridCage.createWithCells(id, grid, operation, origin, cageType)

            cage.result = GridCageResultCalculator(cage).calculateResultFromAction()

            cage
        }
    }
}
