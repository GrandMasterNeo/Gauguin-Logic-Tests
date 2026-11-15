package org.piepmeyer.gauguin.creation

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import org.piepmeyer.gauguin.RandomSingleton
import org.piepmeyer.gauguin.Randomizer
import org.piepmeyer.gauguin.creation.dlx.DLX
import org.piepmeyer.gauguin.creation.dlx.MathDokuDLX
import org.piepmeyer.gauguin.grid.Grid
import org.piepmeyer.gauguin.options.GameVariant

private val logger = KotlinLogging.logger {}

class RandomCageGridCalculator(
    private val variant: GameVariant,
    private val randomizer: Randomizer = RandomSingleton.instance,
    private val shuffler: PossibleDigitsShuffler = RandomPossibleDigitsShuffler(randomizer.random()),
) : GridCalculator {
    override suspend fun calculate(): Grid {
        var dlxNumber = 0
        var numAttempts = 0
        var sumDLXDuration: Long = 0
        var difficultyRejects = 0
        var uniquenessRejects = 0
        val startedAt = System.nanoTime()
        val creator = GridCreator(variant, randomizer, shuffler)
        val difficultyGate = DifficultyAwareGridCreator(variant, randomizer, shuffler)

        var grid: Grid

        try {
        do {
            currentCoroutineContext().ensureActive()

            grid = creator.createRandomizedGridWithCages()
            numAttempts++
            if (!difficultyGate.isWantedDifficulty(grid)) {
                difficultyRejects++
                continue
            }
            val dlxMillis = System.currentTimeMillis()
            val mdd = MathDokuDLX(grid)

            logger.debug { grid }
            // Stop solving as soon as we find multiple solutions
            dlxNumber = mdd.solve(DLX.SolveType.MULTIPLE)
            val dlxDuration = System.currentTimeMillis() - dlxMillis
            sumDLXDuration += dlxDuration
            logger.debug { "DLX Num Solns = $dlxNumber in $dlxDuration ms" }

            if (dlxNumber != 1) uniquenessRejects++
            if (dlxNumber == 0) {
                logger.debug { grid.toString() }
            }
        } while (dlxNumber != 1)

        grid.clearUserValues()
        return grid
        } finally {
            // Includes cancellation and timeouts; do not lose rejection evidence on failed jobs.
            val elapsedMs = (System.nanoTime() - startedAt) / 1_000_000
            logger.warn { "Generation summary: candidates=$numAttempts difficultyRejected=$difficultyRejects uniquenessRejected=$uniquenessRejects dlxMs=$sumDLXDuration elapsedMs=$elapsedMs" }
        }
    }
}
