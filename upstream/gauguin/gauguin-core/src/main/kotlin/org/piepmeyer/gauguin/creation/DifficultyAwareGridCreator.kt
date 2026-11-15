package org.piepmeyer.gauguin.creation

import io.github.oshai.kotlinlogging.KotlinLogging
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.piepmeyer.gauguin.RandomSingleton
import org.piepmeyer.gauguin.Randomizer
import org.piepmeyer.gauguin.difficulty.GameDifficultyRatingService
import org.piepmeyer.gauguin.grid.Grid
import org.piepmeyer.gauguin.options.DifficultySetting
import org.piepmeyer.gauguin.options.GameVariant

private val logger = KotlinLogging.logger {}

class DifficultyAwareGridCreator(
    private val variant: GameVariant,
    private val randomizer: Randomizer = RandomSingleton.instance,
    private val shuffler: PossibleDigitsShuffler = RandomPossibleDigitsShuffler(randomizer.random()),
) : KoinComponent {
    private val difficultyService: GameDifficultyRatingService by inject()

    fun createRandomizedGridWithCages(): Grid {
        val coreCreator = GridCreator(variant, randomizer, shuffler)

        // This entry point can be called independently of RandomCageGridCalculator.
        // Its difficulty-rejection loop must therefore have its own finite budget.
        val maxAttempts = Integer.getInteger("gauguin.generation.maxAttempts", 100_000).coerceAtLeast(1)
        val maxMillis = java.lang.Long.getLong("gauguin.generation.maxMillis", 300_000L).coerceAtLeast(1L)
        val startedAt = System.nanoTime()
        var attempts = 0
        var difficultyRejected = 0

        while (true) {
            val elapsedMs = (System.nanoTime() - startedAt) / 1_000_000
            if (attempts >= maxAttempts || elapsedMs >= maxMillis) {
                error("Difficulty generation budget exhausted: attempts=$attempts difficultyRejected=$difficultyRejected elapsedMs=$elapsedMs maxAttempts=$maxAttempts maxMillis=$maxMillis")
            }
            val newGrid = coreCreator.createRandomizedGridWithCages()
            attempts++
            if (isWantedDifficulty(newGrid)) {
                logger.debug { "Created randomized grid after $attempts attempts." }
                return newGrid
            }
            difficultyRejected++
        }
    }

    internal fun isWantedDifficulty(grid: Grid): Boolean {
        if (variant.options.difficultiesSetting == DifficultySetting.all()) {
            return true
        }
        return if (!difficultyService.isSupported(grid.variant)) {
            true
        } else {
            difficultyService.difficultyOfGrid(grid) in variant.options.difficultiesSetting
        }
    }
}
