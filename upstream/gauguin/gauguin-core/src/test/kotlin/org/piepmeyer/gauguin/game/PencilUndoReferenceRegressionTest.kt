package org.piepmeyer.gauguin.game

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import org.piepmeyer.gauguin.creation.GridBuilder
import org.piepmeyer.gauguin.creation.cage.GridCageType
import org.piepmeyer.gauguin.preferences.ApplicationPreferences
import org.piepmeyer.gauguin.undo.UndoManagerImpl

/**
 * Regression coverage for numeric references and pencil-mark restoration.
 * Keep authoritative solution values unchanged throughout gameplay undo.
 */
class PencilUndoReferenceRegressionTest : FunSpec({
    test("pencil edit then game undo restores exact marks and original answer") {
        val grid = GridBuilder(2)
            .addCageMultiply(2, GridCageType.ANGLE_RIGHT_BOTTOM)
            .addCageSingle(2)
            .createGrid()
        val preferences = mockk<ApplicationPreferences> {
            every { showDupedDigits() } returns true
        }
        val game = Game(grid, mockk(relaxed = true), mockk(relaxed = true), preferences)
        val cell = grid.cells[0]
        cell.value = 2
        cell.possibles = setOf(1)
        val beforeAnswers = grid.cells.map { it.value }

        game.enterPossibleNumberCore(cell, 2)
        cell.possibles shouldBe setOf(1, 2)

        game.undoOneStep()
        cell.possibles shouldBe setOf(1)
        cell.userValue shouldBe null
        grid.cells.map { it.value } shouldBe beforeAnswers
    }

    test("undo restores converted entered value and original pencil marks") {
        val grid = GridBuilder(2)
            .addCageMultiply(2, GridCageType.ANGLE_RIGHT_BOTTOM)
            .addCageSingle(2)
            .createGrid()
        val preferences = mockk<ApplicationPreferences> {
            every { showDupedDigits() } returns true
        }
        val game = Game(grid, mockk(relaxed = true), mockk(relaxed = true), preferences)
        val cell = grid.cells[0]
        cell.value = 2
        cell.userValue = 1
        cell.possibles = setOf(2)
        val beforeAnswers = grid.cells.map { it.value }

        game.enterPossibleNumberCore(cell, 2)
        cell.userValue shouldBe null
        cell.possibles shouldBe setOf(1)

        game.undoOneStep()
        cell.userValue shouldBe 1
        cell.possibles shouldBe setOf(2)
        grid.cells.map { it.value } shouldBe beforeAnswers
    }

    test("batch undo restores neighbor marks and entered number as one action") {
        val grid = GridBuilder(2)
            .addCageMultiply(2, GridCageType.ANGLE_RIGHT_BOTTOM)
            .addCageSingle(2)
            .createGrid()
        val undo = UndoManagerImpl { grid }
        val primary = grid.cells[0]
        val neighbor = grid.cells[1]
        primary.value = 2
        neighbor.value = 1
        neighbor.possibles = setOf(1, 2)
        val beforeAnswers = grid.cells.map { it.value }

        undo.saveUndo(primary, false)
        primary.userValue = 2
        undo.saveUndo(neighbor, true)
        neighbor.removePossible(2)
        neighbor.possibles shouldBe setOf(1)

        undo.restoreUndo()
        primary.userValue shouldBe null
        neighbor.possibles shouldBe setOf(1, 2)
        grid.cells.map { it.value } shouldBe beforeAnswers
        grid.undoSteps.size shouldBe 0
    }
})
