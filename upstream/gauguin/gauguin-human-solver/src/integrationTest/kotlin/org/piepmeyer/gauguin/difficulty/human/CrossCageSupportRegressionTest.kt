package org.piepmeyer.gauguin.difficulty.human

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.piepmeyer.gauguin.creation.RandomCageGridCalculator
import org.piepmeyer.gauguin.creation.RandomPossibleDigitsShuffler
import org.piepmeyer.gauguin.creation.SeedRandomizerMock
import org.piepmeyer.gauguin.difficulty.GameDifficultyRatingService
import org.piepmeyer.gauguin.grid.GridSize
import org.piepmeyer.gauguin.options.DifficultySetting
import org.piepmeyer.gauguin.options.GameOptionsVariant
import org.piepmeyer.gauguin.options.GameVariant

class CrossCageSupportRegressionTest : FunSpec({
    test("10x10 Hard seed zero cross-cage deductions preserve solution") {
        startKoin { modules(module { single { GameDifficultyRatingService() } }) }
        try {
            val randomizer = SeedRandomizerMock(0)
            val variant = GameVariant(
                GridSize(10, 10),
                GameOptionsVariant.createClassic().copy(
                    difficultiesSetting = setOf(DifficultySetting.HARD),
                ),
            )
            val grid = RandomCageGridCalculator(
                variant, randomizer, RandomPossibleDigitsShuffler(randomizer.random),
            ).calculate()
            val working = grid.copyWithEmptyUserValues()
            val solver = HumanSolver(working, validate = true, avoidNishio = true)
            solver.prepareGrid()
            val result = solver.solveAndCalculateDifficulty(avoidReveal = true)
            working.cells.count { it.isUserValueSet && it.userValue != it.value } shouldBe 0
            working.cells.count { !it.isUserValueSet && it.value !in it.possibles } shouldBe 0
            println(
                "CROSS_CAGE_REGRESSION size=10 level=HARD seed=0 " +
                    "filled=${working.cells.count { it.isUserValueSet }}/100 " +
                    "solvedWithoutTrial=${result.success} usedNishio=${result.usedNishio}"
            )
            result.usedNishio shouldBe false
        } finally {
            stopKoin()
        }
    }
})
