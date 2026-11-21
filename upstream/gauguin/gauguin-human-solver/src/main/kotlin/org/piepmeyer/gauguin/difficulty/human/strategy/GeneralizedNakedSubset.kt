package org.piepmeyer.gauguin.difficulty.human.strategy

import org.piepmeyer.gauguin.difficulty.human.HumanSolverCache
import org.piepmeyer.gauguin.difficulty.human.HumanSolverStrategy
import org.piepmeyer.gauguin.difficulty.human.HumanSolverStrategyResult
import org.piepmeyer.gauguin.grid.Grid
import org.piepmeyer.gauguin.grid.GridCell

/**
 * Experimental generalized Hall (naked subset) deduction for sizes >= 5.
 *
 * If k unsolved cells in a row/column collectively allow exactly k digits,
 * those digits cannot occur in any other unsolved cell in that line.
 *
 * Intentionally NOT registered in HumanSolverStrategies: it must pass
 * independent soundness/performance and difficulty-calibration audits before
 * changing production solver ordering or scoring. No guessing or reveals.
 */
class GeneralizedNakedSubset(
    private val minimumSize: Int = 5,
    private val maximumSize: Int = 6,
) : HumanSolverStrategy {
    init {
        require(minimumSize >= 2 && maximumSize >= minimumSize)
    }

    override fun fillCells(
        grid: Grid,
        cache: HumanSolverCache,
    ): HumanSolverStrategyResult {
        for (line in cache.allLines()) {
            val lineCells = line.cells()
            val emptyCells = lineCells.filterNot { it.isUserValueSet }
            if (emptyCells.size <= minimumSize || emptyCells.any { it.possibles.isEmpty() }) continue

            // Do not treat candidates already placed in a line as fresh evidence.
            // This also protects against stale candidates in diagnostic masks.
            val placed = lineCells.mapNotNull { it.userValue }.toSet()
            val largest = minOf(maximumSize, emptyCells.size - 1)
            for (size in minimumSize..largest) {
                val eligible = emptyCells.filter { cell ->
                    cell.possibles.size in 1..size && cell.possibles.none { it in placed }
                }
                if (eligible.size < size) continue

                fun search(
                    first: Int,
                    chosen: List<GridCell>,
                    union: Set<Int>,
                ): Pair<Set<Int>, List<GridCell>>? {
                    if (chosen.size == size) {
                        if (union.size != size) return null
                        val chosenCells = chosen.toSet()
                        val changed = emptyCells.filter { it !in chosenCells && it.possibles.any { digit -> digit in union } }
                        return if (changed.isNotEmpty()) union to changed else null
                    }
                    val needed = size - chosen.size
                    if (eligible.size - first < needed) return null

                    for (index in first..eligible.size - needed) {
                        val cell = eligible[index]
                        val nextUnion = union + cell.possibles
                        if (nextUnion.size > size) continue
                        val found = search(index + 1, chosen + cell, nextUnion)
                        if (found != null) return found
                    }
                    return null
                }

                val inference = search(0, emptyList(), emptySet())
                if (inference != null) {
                    val (digits, changed) = inference
                    changed.forEach { it.possibles = it.possibles - digits }
                    return HumanSolverStrategyResult.Success(changed)
                }
            }
        }
        return HumanSolverStrategyResult.NothingChanged()
    }
}
