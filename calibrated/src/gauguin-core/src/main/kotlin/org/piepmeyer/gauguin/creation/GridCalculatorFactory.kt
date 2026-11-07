package org.piepmeyer.gauguin.creation

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.piepmeyer.gauguin.RandomSingleton
import org.piepmeyer.gauguin.Randomizer
import org.piepmeyer.gauguin.options.DifficultySetting
import org.piepmeyer.gauguin.options.GameVariant

private val logger = KotlinLogging.logger {}

/**
 * Rejection sampling only AFTER upstream has generated a uniquely solvable
 * puzzle. The verifier is optional and is provided by gauguin-human-solver,
 * avoiding a circular module dependency.
 *
 * Retry limits are deliberate: no indefinite UI freeze on difficult boards.
 * On exhaustion the original uniquely-solvable puzzle is returned (logged as
 * a fallback), rather than crashing or returning an invalid board.
 */
class GridCalculatorFactory : KoinComponent {
    private val mergingCageGridCalculatorFactory: MergingCageGridCalculatorFactory by inject()

    fun createCalculator(
        variant: GameVariant,
        randomizer: Randomizer = RandomSingleton.instance,
        shuffler: PossibleDigitsShuffler = RandomPossibleDigitsShuffler(),
    ): GridCalculator {
        val original: GridCalculator =
            if (variant.gridSize.isSquare && !alwaysUseNewAlgorithm) {
                RandomCageGridCalculator(variant, randomizer, shuffler)
            } else {
                mergingCageGridCalculatorFactory.create(variant, randomizer, shuffler)
            }

        if (!variant.gridSize.isSquare ||
            variant.gridSize.width !in 9..11 ||
            !variant.options.showOperators
        ) {
            return original
        }

        // The core library also runs without a Koin context in upstream tests.
        val verifier =
            runCatching { getKoin().getOrNull<GridAcceptanceVerifier>() }
                .getOrNull() ?: return original

        val selected = variant.options.difficultiesSetting
        val maxAttempts =
            when {
                selected == setOf(DifficultySetting.EXTREME) -> 12
                selected == setOf(DifficultySetting.VERY_EASY) -> 8
                selected == setOf(DifficultySetting.EASY) -> 8
                else -> 8
            }

        return GridCalculator {
            var last = original.calculate()
            var attempt = 1

            while (true) {
                currentCoroutineContext().ensureActive()

                if (verifier.accepts(last)) {
                    if (attempt > 1) {
                        logger.info { "Accepted human-constrained grid on attempt $attempt" }
                    }
                    break
                }
                if (attempt >= maxAttempts) {
                    logger.warn {
                        "Human calibration exhausted $maxAttempts attempts; " +
                            "returning uniquely-solvable classical fallback"
                    }
                    break
                }

                attempt++
                last = original.calculate()
            }
            last
        }
    }

    companion object {
        var alwaysUseNewAlgorithm = false
    }
}
