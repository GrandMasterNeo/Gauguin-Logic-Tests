package org.piepmeyer.gauguin.creation

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import org.piepmeyer.gauguin.RandomSingleton
import org.piepmeyer.gauguin.Randomizer
import org.piepmeyer.gauguin.creation.cage.CageConstructionExhaustedException
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
        var cageConstructionRejects = 0
        val rejectedDifficultyCounts = mutableMapOf<String, Int>()
        val uniquenessSolutionCounts = mutableMapOf<Int, Int>()
        var maxSingleDlxMs = 0L
        val cageShapeCounts = mutableMapOf<String, Int>()
        val cageOperationCounts = mutableMapOf<String, Int>()
        var invalidCageTargets = 0
        val startedAt = System.nanoTime()
        val creator = GridCreator(variant, randomizer, shuffler)
        val difficultyGate = DifficultyAwareGridCreator(variant, randomizer, shuffler)
        // Load calibration once; diagnostics must not reload it for every rejected candidate.
        val diagnosticDifficultyRater = org.piepmeyer.gauguin.difficulty.GameDifficultyRater()

        // A failed search must terminate explicitly instead of looping indefinitely.
        // Override in bounded diagnostic tests without changing puzzle acceptance rules.
        val maxAttempts = Integer.getInteger("gauguin.generation.maxAttempts", 100_000).coerceAtLeast(1)
        val maxMillis = java.lang.Long.getLong("gauguin.generation.maxMillis", 300_000L).coerceAtLeast(1L)
        // Checkpoints are observational only; never change puzzle acceptance.
        val diagnosticStartMs = java.lang.Long.getLong("gauguin.generation.diagnosticStartMs", 30_000L)
        val deepStartMs = java.lang.Long.getLong("gauguin.generation.deepStartMs", 50_000L)
        val diagnosticRejectThreshold = Integer.getInteger("gauguin.generation.diagnosticRejectThreshold", 5_000)
        var diagnosticReported = false
        var deepReported = false
        lateinit var grid: Grid

        try {
        do {
            currentCoroutineContext().ensureActive()
            val elapsedMs = (System.nanoTime() - startedAt) / 1_000_000
            if (numAttempts >= maxAttempts || elapsedMs >= maxMillis) {
                error("Generation budget exhausted: attempts=$numAttempts difficultyRejected=$difficultyRejects uniquenessRejected=$uniquenessRejects cageConstructionRejected=$cageConstructionRejects dlxMs=$sumDLXDuration elapsedMs=$elapsedMs maxAttempts=$maxAttempts maxMillis=$maxMillis difficultyRatings=${rejectedDifficultyCounts.toSortedMap()} dlxSolutionCounts=${uniquenessSolutionCounts.toSortedMap()} maxSingleDlxMs=$maxSingleDlxMs invalidCageTargets=$invalidCageTargets cageShapes=${cageShapeCounts.toSortedMap()} cageOperations=${cageOperationCounts.toSortedMap()}")
            }

            if (!diagnosticReported && (elapsedMs >= diagnosticStartMs || difficultyRejects >= diagnosticRejectThreshold)) {
                diagnosticReported = true
                println("GENERATION_CHECKPOINT level=diagnostic candidates=$numAttempts elapsedMs=$elapsedMs difficultyRejected=$difficultyRejects uniquenessRejected=$uniquenessRejects")
                System.out.flush()
            }
            if (!deepReported && elapsedMs >= deepStartMs) {
                deepReported = true
                println("GENERATION_CHECKPOINT level=deep candidates=$numAttempts elapsedMs=$elapsedMs difficultyRatings=${rejectedDifficultyCounts.toSortedMap()} dlxSolutionCounts=${uniquenessSolutionCounts.toSortedMap()} maxSingleDlxMs=$maxSingleDlxMs")
                System.out.flush()
            }
            // An impossible partial cage partition is a rejected candidate, not
            // a reason to abort the entire generation search.
            numAttempts++
            grid = try {
                creator.createRandomizedGridWithCages()
            } catch (ex: CageConstructionExhaustedException) {
                cageConstructionRejects++
                dlxNumber = 0
                continue
            }
            // Record the actual construction distribution before difficulty filtering.
            // No gameplay, acceptance, or randomization behavior is changed.
            for (cage in grid.cages) {
                val shape = cage.cageType.name
                val operation = cage.action.name
                cageShapeCounts[shape] = (cageShapeCounts[shape] ?: 0) + 1
                cageOperationCounts[operation] = (cageOperationCounts[operation] ?: 0) + 1
                if (cage.cells.size > 1) {
                    val values = cage.cells.map { it.value }
                    val expected = when (cage.action) {
                        org.piepmeyer.gauguin.grid.GridCageAction.ACTION_ADD -> values.sum()
                        org.piepmeyer.gauguin.grid.GridCageAction.ACTION_MULTIPLY -> values.fold(1) { acc, value -> acc * value }
                        org.piepmeyer.gauguin.grid.GridCageAction.ACTION_SUBTRACT -> kotlin.math.abs(values[0] - values[1])
                        org.piepmeyer.gauguin.grid.GridCageAction.ACTION_DIVIDE -> if (values.minOrNull() == 0) 0 else values.maxOrNull()!! / values.minOrNull()!!
                        else -> null
                    }
                    if (expected != cage.result) invalidCageTargets++
                } else if (cage.cells.single().value != cage.result) {
                    invalidCageTargets++
                }
            }
            if (!difficultyGate.isWantedDifficulty(grid)) {
                difficultyRejects++
                // Report the distribution without changing acceptance criteria.
                val rated = (diagnosticDifficultyRater.difficulty(grid)?.name ?: "UNRATED")
                rejectedDifficultyCounts[rated] = (rejectedDifficultyCounts[rated] ?: 0) + 1
                // Never reuse a previous candidate's successful DLX result.
                dlxNumber = 0
                continue
            }
            val dlxMillis = System.currentTimeMillis()
            val mdd = MathDokuDLX(grid)

            logger.debug { grid }
            // Stop solving as soon as we find multiple solutions
            dlxNumber = mdd.solve(DLX.SolveType.MULTIPLE)
            val dlxDuration = System.currentTimeMillis() - dlxMillis
            sumDLXDuration += dlxDuration
            maxSingleDlxMs = maxOf(maxSingleDlxMs, dlxDuration)
            uniquenessSolutionCounts[dlxNumber] = (uniquenessSolutionCounts[dlxNumber] ?: 0) + 1
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
            val summary = "GENERATION_SUMMARY candidates=$numAttempts difficultyRejected=$difficultyRejects uniquenessRejected=$uniquenessRejects cageConstructionRejected=$cageConstructionRejects dlxMs=$sumDLXDuration elapsedMs=$elapsedMs"
            logger.warn { summary }
            println(summary)
            println("GENERATION_REJECTION_DIAGNOSTIC difficultyRatings=${rejectedDifficultyCounts.toSortedMap()} dlxSolutionCounts=${uniquenessSolutionCounts.toSortedMap()} maxSingleDlxMs=$maxSingleDlxMs")
            println("CAGE_CONSTRUCTION_DIAGNOSTIC invalidTargets=$invalidCageTargets shapes=${cageShapeCounts.toSortedMap()} operations=${cageOperationCounts.toSortedMap()}")
            System.out.flush()
        }
    }
}
