package org.piepmeyer.gauguin.difficulty.human.strategy

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import org.piepmeyer.gauguin.difficulty.human.HumanSolverCacheImpl
import org.piepmeyer.gauguin.difficulty.human.HumanSolverStrategies
import org.piepmeyer.gauguin.difficulty.human.HumanSolverStrategyResult
import org.piepmeyer.gauguin.grid.Grid
import org.piepmeyer.gauguin.grid.GridSize
import org.piepmeyer.gauguin.options.GameOptionsVariant
import org.piepmeyer.gauguin.options.GameVariant
import kotlin.random.Random

/** Isolated proof-of-concept. No production strategy registry changes. */
class GeneralizedNakedSubsetTest : FunSpec({
    fun grid(size: Int): Grid =
        Grid(GameVariant(GridSize(size, size), GameOptionsVariant.createClassic())).apply {
            cells.forEach { it.possibles = (1..size).toSet() }
        }

    test("five-cell Hall set eliminates from other row cells, not its members") {
        val board = grid(9)
        val masks = listOf(setOf(1, 2), setOf(2, 3), setOf(3, 4), setOf(4, 5), setOf(1, 5))
        masks.forEachIndexed { column, mask -> board.getValidCellAt(0, column).possibles = mask }
        val result = GeneralizedNakedSubset().fillCells(board, HumanSolverCacheImpl(board))
        (result is HumanSolverStrategyResult.Success) shouldBe true
        for (column in 0 until 5) board.getValidCellAt(0, column).possibles shouldBe masks[column]
        for (column in 5 until 9) board.getValidCellAt(0, column).possibles shouldBe (6..9).toSet()
        board.getValidCellAt(1, 0).possibles shouldBe (1..9).toSet()
    }

    test("six-cell Hall set eliminates from other column cells on 11x11") {
        val board = grid(11)
        val masks = listOf(setOf(1, 2), setOf(2, 3), setOf(3, 4), setOf(4, 5), setOf(5, 6), setOf(1, 6))
        masks.forEachIndexed { row, mask -> board.getValidCellAt(row, 0).possibles = mask }
        val result = GeneralizedNakedSubset().fillCells(board, HumanSolverCacheImpl(board))
        (result is HumanSolverStrategyResult.Success) shouldBe true
        for (row in 0 until 6) board.getValidCellAt(row, 0).possibles shouldBe masks[row]
        for (row in 6 until 11) board.getValidCellAt(row, 0).possibles shouldBe (7..11).toSet()
    }

    test("five cells with six distinct digits do not constitute a Hall set") {
        val board = grid(9)
        val masks = listOf(setOf(1, 2), setOf(2, 3), setOf(3, 4), setOf(4, 5), setOf(5, 6))
        masks.forEachIndexed { column, mask -> board.getValidCellAt(0, column).possibles = mask }
        val result = GeneralizedNakedSubset().fillCells(board, HumanSolverCacheImpl(board))
        (result is HumanSolverStrategyResult.NothingChanged) shouldBe true
        board.getValidCellAt(0, 5).possibles shouldBe (1..9).toSet()
    }

    test("preserves true candidates across random 9x9 10x10 and 11x11 Latin-square masks") {
        val strategy = GeneralizedNakedSubset()
        var deductions = 0
        for (size in 9..11) {
            repeat(30) { variation ->
                val board = grid(size)
                val random = Random(size * 1000 + variation)
                board.cells.forEach { cell ->
                    cell.value = ((cell.row + cell.column + variation) % size) + 1
                    cell.possibles = (1..size).filter { digit ->
                        digit == cell.value || random.nextBoolean()
                    }.toSet()
                }
                val cache = HumanSolverCacheImpl(board)
                repeat(30) {
                    when (val result = strategy.fillCells(board, cache)) {
                        is HumanSolverStrategyResult.Success -> {
                            deductions++
                            result.changedCells.isNotEmpty() shouldBe true
                        }
                        is HumanSolverStrategyResult.NothingChanged -> return@repeat
                    }
                    board.cells.all { it.value in it.possibles } shouldBe true
                }
            }
        }
        println("HALL_SUBSET_ORACLE compatibleLatinSquareMasks=90 deductions=$deductions noTrueCandidateErased=true")
    }

    test("experiment does not change the 38-strategy registered production order") {
        HumanSolverStrategies.entries.size shouldBe 38
        HumanSolverStrategies.entries.none { it.solver is GeneralizedNakedSubset } shouldBe true
    }
})
