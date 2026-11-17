package org.piepmeyer.gauguin.difficulty.human.strategy.nishio

import io.github.oshai.kotlinlogging.KotlinLogging
import org.piepmeyer.gauguin.difficulty.human.GridLinesProvider
import org.piepmeyer.gauguin.grid.Grid
import org.piepmeyer.gauguin.grid.GridCageAction
import org.piepmeyer.gauguin.grid.GridCell

private val logger = KotlinLogging.logger {}

class NishioCore(
    private val grid: Grid,
    private val possiblesCache: PossiblesCacheByCageNumber,
    private val cell: GridCell,
    private val possible: Int,
) {
    fun tryWithNishio(): NishioResult {
        val tryGrid = copyGrid()

        tryGrid.setUserValueAndRemovePossibles(tryGrid.getCell(cell.cellNumber), possible)

        logger.trace { tryGrid }

        do {
            // Trial failure must not be accepted as a valid solved branch or as a deduction.
            if (!isConsistentTrial(tryGrid)) return NishioResult.Contradictions()

            val cellWithSinglePossible = tryGrid.cells.firstOrNull { it.possibles.size == 1 }

            if (cellWithSinglePossible == null) {
                if (tryGrid.cells.all { it.isUserValueSet }) {
                    return NishioResult.Solved(tryGrid)
                }

                if (!detectPossibles(tryGrid)) {
                    // A branch can be impossible even when no individual cell
                    // is empty and no cage has yet been completely filled.
                    // Check these global/local constraints only at a dead end
                    // to avoid slowing every speculative propagation step.
                    if (hasUnfillableRequiredDigit(tryGrid) || hasUnfillableCage(tryGrid)) {
                        return NishioResult.Contradictions()
                    }
                    return NishioResult.NothingFound()
                }
            } else {
                tryGrid.setUserValueAndRemovePossibles(
                    cell = cellWithSinglePossible,
                    value = cellWithSinglePossible.possibles.first(),
                )

                if (tryGrid.cells.any { !it.isUserValueSet && it.possibles.isEmpty() } ||
                    !cellWithSinglePossible.cage().isUserMathCorrect()
                ) {
                    return NishioResult.Contradictions()
                }
            }
        } while (true)
    }

    private fun detectPossibles(tryGrid: Grid): Boolean {
        if (tryToDeletePossibles(tryGrid, possiblesCache)) {
            return true
        }

        return tryToDetectNakedPairs(tryGrid)
    }

    private fun copyGrid(): Grid {
        val tryGrid = grid.copyWithEmptyUserValues()

        grid.cells.forEach {
            val tryCell = tryGrid.getCell(it.cellNumber)

            if (it.userValue != null) {
                tryCell.userValue = it.userValue
            } else {
                tryCell.possibles = it.possibles
            }
        }
        return tryGrid
    }

    private fun tryToDetectNakedPairs(tryGrid: Grid): Boolean {
        GridLinesProvider(tryGrid).allLines().forEach { line ->
            val cellsWithTwoPossibles = line.cells().filter { !it.isUserValueSet && it.possibles.size == 2 }

            cellsWithTwoPossibles.forEach { firstCell ->
                cellsWithTwoPossibles.forEach { secondCell ->
                    if (firstCell != secondCell && firstCell.possibles == secondCell.possibles) {
                        val cellWithPossiblesToBeDeleted =
                            line
                                .cells()
                                .filter {
                                    it != firstCell &&
                                        it != secondCell &&
                                        !it.isUserValueSet &&
                                        (
                                            it.possibles.contains(firstCell.possibles.toList()[0]) ||
                                                it.possibles.contains(firstCell.possibles.toList()[1])
                                        )
                                }

                        if (cellWithPossiblesToBeDeleted.isNotEmpty()) {
                            cellWithPossiblesToBeDeleted.forEach { otherCell ->
                                firstCell.possibles.forEach {
                                    otherCell.removePossible(it)
                                }
                            }

                            return true
                        }
                    }
                }
            }
        }

        return false
    }

    private fun tryToDeletePossibles(
        grid: Grid,
        possiblesCache: PossiblesCacheByCageNumber,
    ): Boolean {
        val cageWithEmptyCells = grid.cages.filter { it.cells.any { cell -> !cell.isUserValueSet } }

        var deletedPossibles = false

        cageWithEmptyCells.forEach { cage ->
            val combinations = possiblesCache.possibles(cage.id)

            val newPossibles =
                combinations
                    .filter {
                        cage.cells.withIndex().all { cell ->
                            if (cell.value.isUserValueSet) {
                                cell.value.userValue == it[cell.index]
                            } else {
                                cell.value.possibles.contains(it[cell.index])
                            }
                        }
                    }.toSet()

            // An inconsistent combination cache must never erase every candidate.
            if (newPossibles.isEmpty()) return@forEach

            cage.cells.forEachIndexed { cellIndex, cell ->
                cell.possibles.forEach { possible ->
                    if (newPossibles.none { it[cellIndex] == possible }) {
                        cell.removePossible(possible)
                        deletedPossibles = true
                    }
                }
            }
        }
        return deletedPossibles
    }

    /**
     * A row/column containing all digits must leave a place for each digit.
     * A missing placement is a contradiction even if every remaining cell
     * still has two or more candidate digits.
     */
    private fun hasUnfillableRequiredDigit(tryGrid: Grid): Boolean =
        GridLinesProvider(tryGrid).linesWithEachPossibleValue().any { line ->
            val cells = line.cells()
            tryGrid.variant.possibleDigits.any { digit ->
                cells.none { cell ->
                    cell.userValue == digit ||
                        (!cell.isUserValueSet && digit in cell.possibles)
                }
            }
        }

    /**
     * The original cage cache contains every combination compatible with
     * the parent state. A speculative branch must retain at least one such
     * combination in each cage, including cages not yet completely filled.
     * This uses clue arithmetic, never the puzzle's stored hidden answer.
     */
    private fun hasUnfillableCage(tryGrid: Grid): Boolean =
        tryGrid.cages.any { cage ->
            possiblesCache.possibles(cage.id).none { combination ->
                cage.cells.withIndex().all { (index, cell) ->
                    if (cell.isUserValueSet) {
                        cell.userValue == combination[index]
                    } else {
                        combination[index] in cell.possibles
                    }
                }
            }
        }

    private fun isConsistentTrial(tryGrid: Grid): Boolean {
        if (tryGrid.cells.any { !it.isUserValueSet && it.possibles.isEmpty() }) return false

        val placedCells = tryGrid.cells.filter { it.isUserValueSet }
        if (placedCells.groupBy { it.row }.values.any { row ->
                row.map { it.userValue }.size != row.map { it.userValue }.toSet().size
            }) return false
        if (placedCells.groupBy { it.column }.values.any { column ->
                column.map { it.userValue }.size != column.map { it.userValue }.toSet().size
            }) return false

        if (tryGrid.cages.any { cage ->
                if (!cage.cells.all { it.isUserValueSet }) {
                    false
                } else if (cage.cells.size == 1 && cage.action == GridCageAction.ACTION_NONE) {
                    cage.cells.single().userValue != cage.result
                } else {
                    !cage.isUserMathCorrect()
                }
            }) return false
        return true
    }

    fun applyFindings(result: NishioResult): List<GridCell> {
        if (result is NishioResult.Contradictions) {
            return if (cell.possibles.size == 2) {
                val changedCells = grid.setUserValueAndRemovePossibles(cell, cell.possibles.first { it != possible })

                changedCells
            } else {
                cell.removePossible(possible)

                listOf(cell)
            }
        } else if (result is NishioResult.Solved) {
            val cellsWithoutUserValue = grid.cells.filter { !it.isUserValueSet }
            cellsWithoutUserValue.forEach { cell ->
                grid.setUserValueAndRemovePossibles(
                    grid.getCell(cell.cellNumber),
                    result.solvedGrid.getCell(cell.cellNumber).userValue,
                )
            }

            return cellsWithoutUserValue
        }

        return emptyList()
    }
}
