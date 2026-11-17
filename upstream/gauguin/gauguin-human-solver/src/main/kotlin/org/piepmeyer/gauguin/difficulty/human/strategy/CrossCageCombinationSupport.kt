package org.piepmeyer.gauguin.difficulty.human.strategy

import org.piepmeyer.gauguin.difficulty.human.HumanSolverCache
import org.piepmeyer.gauguin.difficulty.human.HumanSolverStrategy
import org.piepmeyer.gauguin.difficulty.human.HumanSolverStrategyResult
import org.piepmeyer.gauguin.grid.Grid
import org.piepmeyer.gauguin.grid.GridCage

/**
 * Non-speculative pairwise consistency: a cage assignment is unsupported when
 * every assignment of another cage conflicts with it in a shared row/column.
 *
 * Only remove a cell candidate if no supported assignment of its own cage
 * contains it. This is sound even when pairwise consistency is incomplete.
 */
class CrossCageCombinationSupport : HumanSolverStrategy {
    override fun fillCells(
        grid: Grid,
        cache: HumanSolverCache,
    ): HumanSolverStrategyResult {
        val cages = grid.cages.toList()
        for (firstIndex in cages.indices) {
            val first = cages[firstIndex]
            if (first.cells.all { it.isUserValueSet }) continue
            val firstOptions = cache.possibles(first).toList()
            if (firstOptions.isEmpty()) continue

            for (secondIndex in cages.indices) {
                if (firstIndex == secondIndex) continue
                val second = cages[secondIndex]
                val secondOptions = cache.possibles(second).toList()
                if (secondOptions.isEmpty()) continue

                val conflicts = sharedLineConflicts(first, second)
                if (conflicts.isEmpty()) continue

                // Keep this deduction bounded on large cages.
                if (firstOptions.size.toLong() * secondOptions.size > 10_000L) continue

                val supported = firstOptions.filter { firstOption ->
                    secondOptions.any { secondOption ->
                        conflicts.none { (a, b) -> firstOption[a] == secondOption[b] }
                    }
                }
                if (supported.isEmpty()) continue // contradiction; do not infer from empty support

                for (cellIndex in first.cells.indices) {
                    val cell = first.cells[cellIndex]
                    if (cell.isUserValueSet) continue
                    val allowed = supported.map { it[cellIndex] }.toSet()
                    for (digit in cell.possibles) {
                        if (digit !in allowed) {
                            cell.removePossible(digit)
                            return HumanSolverStrategyResult.Success(listOf(cell))
                        }
                    }
                }
            }
        }
        return HumanSolverStrategyResult.NothingChanged()
    }

    private fun sharedLineConflicts(first: GridCage, second: GridCage): List<Pair<Int, Int>> {
        val conflicts = mutableListOf<Pair<Int, Int>>()
        first.cells.forEachIndexed { a, cellA ->
            second.cells.forEachIndexed { b, cellB ->
                if (cellA.row == cellB.row || cellA.column == cellB.column) {
                    conflicts.add(a to b)
                }
            }
        }
        return conflicts
    }
}
