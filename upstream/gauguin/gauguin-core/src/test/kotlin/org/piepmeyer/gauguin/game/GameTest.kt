package org.piepmeyer.gauguin.game

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.piepmeyer.gauguin.creation.GridBuilder
import org.piepmeyer.gauguin.creation.cage.GridCageType
import org.piepmeyer.gauguin.preferences.ApplicationPreferences
import org.piepmeyer.gauguin.preferences.StatisticsManagerWriting

class GameTest :
    FunSpec(
        {
            test("restart game clears all values and all possible values") {
                val preferences =
                    mockk<ApplicationPreferences> {
                        every { showDupedDigits() } returns true
                    }

                val game = gameWithSmallGrid(preferences)

                game.restartGame()

                game.grid.cells.forEach { it.userValue shouldBe null }
                game.grid.cells.forEach { it.possibles.shouldBeEmpty() }

                stopKoin()
            }

            test("restart game clears last modified flags of all cells") {
                startKoin { }

                val preferences =
                    mockk<ApplicationPreferences> {
                        every { showDupedDigits() } returns true
                    }

                val game = gameWithSmallGrid(preferences)

                game.restartGame()

                game.grid.cells.forEach { it.isLastModified shouldBe false }
            }

            test("revealing a cell sets the correct user value and clears possible numbers") {
                val preferences =
                    mockk<ApplicationPreferences> {
                        every { showDupedDigits() } returns true
                        every { removePencils() } returns true
                    }
                val statisticsManager =
                    mockk<StatisticsManagerWriting> {
                        every { puzzleStartedToBePlayed() } just runs
                        every { storeStreak(false) } just runs
                    }

                val smallGrid =
                    GridBuilder(2)
                        .addCageMultiply(2, GridCageType.ANGLE_RIGHT_BOTTOM)
                        .addCageSingle(2)
                        .createGrid()
                smallGrid.isActive = true

                val cellToReveal = smallGrid.cells[0]

                cellToReveal.addPossible(1)
                cellToReveal.addPossible(2)
                cellToReveal.value = 2
                cellToReveal.userValue = 1
                smallGrid.selectedCell = cellToReveal

                val game =
                    Game(
                        initalGrid = smallGrid,
                        mockk(relaxed = true),
                        statisticsManager,
                        preferences,
                    )

                game.revealCell(cellToReveal)

                cellToReveal.possibles.shouldBeEmpty()
                cellToReveal.userValue shouldBe 2
            }

            test("revealing a different cell replaces its incorrect value without changing selection") {
                val preferences = mockk<ApplicationPreferences> {
                    every { showDupedDigits() } returns true
                    every { removePencils() } returns false
                }
                val grid = GridBuilder(2)
                    .addCageMultiply(2, GridCageType.ANGLE_RIGHT_BOTTOM)
                    .addCageSingle(2)
                    .createGrid()
                grid.isActive = true
                val target = grid.cells[0]
                target.value = 2
                target.userValue = 1
                grid.selectedCell = grid.cells[1]
                val game = Game(grid, mockk(relaxed = true), mockk(relaxed = true), preferences)

                game.revealCell(target)

                target.userValue shouldBe 2
                target.isCheated shouldBe true
                grid.selectedCell shouldBe grid.cells[1]
            }

            test("revealing an already correct cell marks it revealed and refreshes the grid") {
                val preferences = mockk<ApplicationPreferences> {
                    every { showDupedDigits() } returns true
                }
                val grid = GridBuilder(2)
                    .addCageMultiply(2, GridCageType.ANGLE_RIGHT_BOTTOM)
                    .addCageSingle(2)
                    .createGrid()
                grid.isActive = true
                val target = grid.cells[0]
                target.value = 2
                target.userValue = 2
                val gridUI = mockk<org.piepmeyer.gauguin.grid.GridView>(relaxed = true)
                val game = Game(grid, gridUI, mockk(relaxed = true), preferences)

                game.revealCell(target)

                target.userValue shouldBe 2
                target.isCheated shouldBe true
                io.mockk.verify(exactly = 1) { gridUI.invalidate() }
            }

            test("fillSingleCagesInNewGrid fills userValues of single cages") {
                val preferences =
                    mockk<ApplicationPreferences> {
                        every { removePencils() } returns false
                    }

                val grid =
                    GridBuilder(3)
                        .addCageMultiply(1, GridCageType.TRIPLE_HORIZONTAL)
                        .addCageSingle(2)
                        .addCageSingle(3)
                        .addCageSingle(4)
                        .addCageMultiply(1, GridCageType.TRIPLE_HORIZONTAL)
                        .createGrid()

                grid.getCell(3).value = 2
                grid.getCell(4).value = 3
                grid.getCell(5).value = 4

                val game =
                    Game(
                        initalGrid = grid,
                        mockk(relaxed = true),
                        mockk(relaxed = true),
                        preferences,
                    )

                game.fillSingleCagesInNewGrid()

                grid.getCell(0).userValue shouldBe null
                grid.getCell(1).userValue shouldBe null
                grid.getCell(2).userValue shouldBe null
                grid.getCell(3).userValue shouldBe 2
                grid.getCell(4).userValue shouldBe 3
                grid.getCell(5).userValue shouldBe 4
                grid.getCell(6).userValue shouldBe null
                grid.getCell(7).userValue shouldBe null
                grid.getCell(8).userValue shouldBe null
            }

            test("fillSingleCagesInNewGrid with remove pencils deletes pencils marks") {
                val preferences =
                    mockk<ApplicationPreferences> {
                        every { removePencils() } returns true
                    }

                val grid =
                    GridBuilder(3)
                        .addCageMultiply(1, GridCageType.TRIPLE_HORIZONTAL)
                        .addCageSingle(2)
                        .addCageSingle(3)
                        .addCageSingle(4)
                        .addCageMultiply(1, GridCageType.TRIPLE_HORIZONTAL)
                        .createGrid()

                grid.addPossiblesAtNewGame()

                grid.getCell(3).value = 1
                grid.getCell(4).value = 2
                grid.getCell(5).value = 3

                val game =
                    Game(
                        initalGrid = grid,
                        mockk(relaxed = true),
                        mockk(relaxed = true),
                        preferences,
                    )

                game.fillSingleCagesInNewGrid()

                grid.getCell(0).possibles shouldBe setOf(2, 3)
                grid.getCell(1).possibles shouldBe setOf(1, 3)
                grid.getCell(2).possibles shouldBe setOf(1, 2)
                grid.getCell(3).possibles shouldBe emptySet()
                grid.getCell(4).possibles shouldBe emptySet()
                grid.getCell(5).possibles shouldBe emptySet()
                grid.getCell(6).possibles shouldBe setOf(2, 3)
                grid.getCell(7).possibles shouldBe setOf(1, 3)
                grid.getCell(8).possibles shouldBe setOf(1, 2)
            }
        },
    )

private fun gameWithSmallGrid(preferences: ApplicationPreferences): Game {
    val smallGrid =
        GridBuilder(2)
            .addCageMultiply(2, GridCageType.ANGLE_RIGHT_BOTTOM)
            .addCageSingle(2)
            .createGrid()

    smallGrid.cells[0].userValue = 2
    smallGrid.cells[1].addPossible(1)
    smallGrid.cells[1].addPossible(2)

    return Game(
        initalGrid = smallGrid,
        gridUI = mockk(relaxed = true),
        statisticsManager = mockk(relaxed = true),
        applicationPreferences = preferences,
    )
}
