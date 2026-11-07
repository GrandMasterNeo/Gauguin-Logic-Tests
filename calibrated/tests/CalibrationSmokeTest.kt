package org.piepmeyer.gauguin.difficulty.human

import HumanSolverModule
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.piepmeyer.gauguin.creation.GridCalculatorFactory
import org.piepmeyer.gauguin.creation.GridCreationViaMergeModule
import org.piepmeyer.gauguin.creation.RandomPossibleDigitsShuffler
import org.piepmeyer.gauguin.creation.SeedRandomizerMock
import org.piepmeyer.gauguin.difficulty.GameDifficultyRater
import org.piepmeyer.gauguin.difficulty.GameDifficultyRatingService
import org.piepmeyer.gauguin.grid.GridSize
import org.piepmeyer.gauguin.options.DifficultySetting
import org.piepmeyer.gauguin.options.GameOptionsVariant
import org.piepmeyer.gauguin.options.GameVariant

/**
 * Real generators + real human acceptance module. Not a mock.
 * Check representative beginner boards before producing installable APK.
 */
class CalibrationSmokeTest : FunSpec({
    test("calibrated 9x9 through 11x11 beginner boards meet their strategy caps") {
        startKoin {
            modules(
                module { single { GameDifficultyRatingService() } },
                GridCreationViaMergeModule().module(),
                HumanSolverModule().module(),
            )
        }
        try {
            runBlocking {
                GridCalculatorFactory.alwaysUseNewAlgorithm = false
                val verifier = HumanGridAcceptanceVerifier()
                val rater = GameDifficultyRater()
                for ((size, level) in listOf(
                    9 to DifficultySetting.VERY_EASY,
                    10 to DifficultySetting.EASY,
                    11 to DifficultySetting.VERY_EASY,
                )) {
                    val seed = size * 100 + level.ordinal
                    val rng = SeedRandomizerMock(seed)
                    val variant = GameVariant(
                        GridSize(size, size),
                        GameOptionsVariant.createClassic().copy(
                            difficultiesSetting = setOf(level),
                        ),
                    )
                    val grid = GridCalculatorFactory().createCalculator(
                        variant, rng, RandomPossibleDigitsShuffler(rng.random),
                    ).calculate()
                    rater.difficulty(grid) shouldBe level
                    verifier.accepts(grid) shouldBe true
                    println("CALIBRATION_SMOKE_OK " + size + "x" + size + " " + level)
                }
            }
        } finally {
            stopKoin()
        }
    }
})
