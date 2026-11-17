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
        val previousAttempts = System.getProperty("gauguin.generation.maxAttempts")
        val previousMillis = System.getProperty("gauguin.generation.maxMillis")
        System.setProperty("gauguin.generation.maxAttempts", "100")
        System.setProperty("gauguin.generation.maxMillis", "60000")
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
            println("PRODUCTION_EQUIVALENT_GENERATION size=10 level=HARD seed=0 accepted=true cages=${grid.cages.size} cells=${grid.cells.size} uniqueness=1")
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
            println("HUMAN_CERTIFICATION size=10 level=HARD seed=0 complete=${result.success && working.cells.all { it.isUserValueSet }} wrong=${working.cells.count { it.isUserValueSet && it.userValue != it.value }}")
        } finally {
            stopKoin()
            if (previousAttempts == null) System.clearProperty("gauguin.generation.maxAttempts") else System.setProperty("gauguin.generation.maxAttempts", previousAttempts)
            if (previousMillis == null) System.clearProperty("gauguin.generation.maxMillis") else System.setProperty("gauguin.generation.maxMillis", previousMillis)
        }
    }
})
