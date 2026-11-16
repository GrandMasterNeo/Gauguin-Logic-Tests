package org.piepmeyer.gauguin.difficulty.human

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import org.piepmeyer.gauguin.creation.GridBuilder
import org.piepmeyer.gauguin.creation.cage.GridCageType
import org.piepmeyer.gauguin.difficulty.human.strategy.nishio.NishioWithPairs

class LogicSafetyRegressionTest :
    FunSpec({
        test("empty combinations preserve candidates") {
            val grid = ambiguousTwoByTwo()
            val before = grid.cells.map { it.possibles }

            PossiblesReducer(grid.cages.first()).reduceToPossibleCombinations(emptyList()) shouldBe false

            grid.cells.map { it.possibles } shouldBe before
        }

        test("human solver rejects incompatible cage candidates without erasing them") {
            val grid = ambiguousTwoByTwo()
            grid.cages.first().cells.forEach { it.possibles = setOf(1) }
            val before = grid.cells.map { it.possibles }

            HumanSolver(grid).solveAndCalculateDifficulty(avoidReveal = true).success shouldBe false

            grid.cells.map { it.possibles } shouldBe before
            grid.cells.all { !it.isUserValueSet } shouldBe true
        }

        test("Nishio does not commit a speculative solution of an ambiguous puzzle") {
            val grid = ambiguousTwoByTwo()
            val before = grid.cells.map { it.possibles }
            val cache = HumanSolverCacheImpl.createValidatedCache(grid)

            (NishioWithPairs().fillCells(grid, cache) is HumanSolverStrategyResult.NothingChanged) shouldBe true

            grid.cells.map { it.possibles } shouldBe before
            grid.cells.all { !it.isUserValueSet } shouldBe true
        }
    })

private fun ambiguousTwoByTwo() =
    GridBuilder(2, 2)
        .addCageAdd(3, GridCageType.DOUBLE_HORIZONTAL)
        .addCageAdd(3, GridCageType.DOUBLE_HORIZONTAL)
        .createGrid()
        .also { grid -> grid.cells.forEach { it.possibles = setOf(1, 2) } }
