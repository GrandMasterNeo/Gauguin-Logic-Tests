package org.piepmeyer.gauguin.difficulty.human

import io.kotest.core.spec.style.FunSpec
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.piepmeyer.gauguin.difficulty.GameDifficultyRatingService
import org.piepmeyer.gauguin.creation.GridCreator
import org.piepmeyer.gauguin.creation.RandomPossibleDigitsShuffler
import org.piepmeyer.gauguin.creation.SeedRandomizerMock
import org.piepmeyer.gauguin.creation.dlx.DLX
import org.piepmeyer.gauguin.creation.dlx.MathDokuDLX
import org.piepmeyer.gauguin.difficulty.GameDifficultyRater
import org.piepmeyer.gauguin.difficulty.ensureDifficultyCalculated
import org.piepmeyer.gauguin.grid.GridSize
import org.piepmeyer.gauguin.options.DifficultySetting
import org.piepmeyer.gauguin.options.GameOptionsVariant
import org.piepmeyer.gauguin.options.GameVariant

/**
 * Bounded, read-only empirical calibration. Candidate levels are classified by
 * existing thresholds; they are NOT generated with an unbounded level filter.
 * No success or difficulty claim is made for candidates that fail uniqueness.
 */
class HumanDifficultyCalibrationSampleTest : FunSpec({
    val size = System.getenv("GAUGUIN_CAL_SIZE")?.toIntOrNull() ?: 10
    val seed = System.getenv("GAUGUIN_CAL_SEED")?.toIntOrNull() ?: 3
    val limit = System.getenv("GAUGUIN_CAL_LIMIT")?.toIntOrNull() ?: 600
    val budgetMs = System.getenv("GAUGUIN_CAL_BUDGET_MS")?.toLongOrNull() ?: 90000L
    require(size in 9..11 && seed >= 0 && limit in 1..3000 && budgetMs in 1000L..180000L)
    test("bounded empirical classical versus human rating size=$size seed=$seed") {
        startKoin { modules(module { single { GameDifficultyRatingService() } }) }
        try {
        val variant = GameVariant(GridSize(size, size), GameOptionsVariant.createClassic().copy(
            difficultiesSetting = DifficultySetting.all(),
        ))
        val random = SeedRandomizerMock(seed)
        val creator = GridCreator(variant, random, RandomPossibleDigitsShuffler(random.random))
        val rater = GameDifficultyRater()
        val thresholds = rater.byVariant(variant) ?: error("No rating configuration")
        val levels = DifficultySetting.entries
        val sampled = mutableMapOf<DifficultySetting, Int>()
        val checked = mutableMapOf<DifficultySetting, Int>()
        val unique = mutableMapOf<DifficultySetting, Int>()
        val nonunique = mutableMapOf<DifficultySetting, Int>()
        val quotaSkipped = mutableMapOf<DifficultySetting, Int>()
        val solved = mutableMapOf<DifficultySetting, Int>()
        val progress = mutableMapOf<DifficultySetting, MutableList<Double>>()
        val humanScores = mutableMapOf<DifficultySetting, MutableList<Int>>()
        val start = System.nanoTime()
        var attempted = 0
        var generationNs = 0L
        var uniquenessNs = 0L
        var humanNs = 0L
        var uniquenessChecks = 0
        var humanChecks = 0
        println("CAL_THRESHOLDS size=$size seed=$seed easy=${thresholds.thresholdEasy} medium=${thresholds.thresholdMedium} hard=${thresholds.thresholdHard} extreme=${thresholds.thresholdExtreme}")
        while (attempted < limit && (System.nanoTime() - start) / 1_000_000 < budgetMs) {
            val generationStart = System.nanoTime()
            val grid = creator.createRandomizedGridWithCages()
            generationNs += System.nanoTime() - generationStart
            attempted++
            val level = rater.difficulty(grid) ?: error("Unrated generated grid")
            sampled[level] = (sampled[level] ?: 0) + 1
            // Seek up to three UNIQUE puzzles per band; nonunique attempts do not exhaust\n            // the unique-puzzle quota. Bound DLX attempts at 12 per band to avoid\n            // disproportionate cost in bands with poor uniqueness acceptance.
            if ((unique[level] ?: 0) >= 3 || (checked[level] ?: 0) >= 12) {
                quotaSkipped[level] = (quotaSkipped[level] ?: 0) + 1
                continue
            }
            checked[level] = (checked[level] ?: 0) + 1
            val uniquenessStart = System.nanoTime()
            val solutions = MathDokuDLX(grid).solve(DLX.SolveType.MULTIPLE)
            uniquenessNs += System.nanoTime() - uniquenessStart
            uniquenessChecks++
            if (solutions != 1) {
                nonunique[level] = (nonunique[level] ?: 0) + 1
                println("CAL_CASE size=$size seed=$seed index=$attempted classical=$level score=${grid.ensureDifficultyCalculated()} solutions=$solutions human=NOT_TESTED")
                continue
            }
            unique[level] = (unique[level] ?: 0) + 1
            val humanStart = System.nanoTime()
            val work = grid.copyWithEmptyUserValues()
            val solver = HumanSolver(work, validate = true, avoidNishio = true)
            solver.prepareGrid()
            val result = solver.solveAndCalculateDifficulty(avoidReveal = true)
            humanNs += System.nanoTime() - humanStart
            humanChecks++
            val filled = work.cells.count { it.isUserValueSet }
            val fraction = filled.toDouble() / work.cells.size
            progress.getOrPut(level) { mutableListOf() }.add(fraction)
            humanScores.getOrPut(level) { mutableListOf() }.add(result.difficulty)
            val compliant = result.success && filled == work.cells.size && work.cells.none { it.userValue != it.value }
            if (compliant) solved[level] = (solved[level] ?: 0) + 1
            println("CAL_CASE size=$size seed=$seed index=$attempted classical=$level score=${grid.ensureDifficultyCalculated()} solutions=1 filled=$filled total=${work.cells.size} progress=$fraction humanScore=${result.difficulty} deductionOnly=$compliant")
        }
        val elapsedMs = (System.nanoTime() - start) / 1_000_000
        println("CAL_STAGE_TIMES size=$size seed=$seed attempted=$attempted uniquenessChecks=$uniquenessChecks humanChecks=$humanChecks generationMs=${generationNs / 1_000_000} uniquenessMs=${uniquenessNs / 1_000_000} humanMs=${humanNs / 1_000_000} totalMs=$elapsedMs")
        for (level in levels) {
            val values = progress[level].orEmpty()
            val scores = humanScores[level].orEmpty()
            println("CAL_SUMMARY size=$size seed=$seed level=$level sampled=${sampled[level] ?: 0} checked=${checked[level] ?: 0} unique=${unique[level] ?: 0} nonunique=${nonunique[level] ?: 0} quotaSkipped=${quotaSkipped[level] ?: 0} deductionSolved=${solved[level] ?: 0} meanProgress=${if (values.isEmpty()) "NA" else values.average()} meanHumanScore=${if (scores.isEmpty()) "NA" else scores.average()} attempted=$attempted elapsedMs=$elapsedMs")
        }
        check(attempted > 0)
        check(sampled.values.sum() == attempted)
        check(levels.all { (sampled[it] ?: 0) == (checked[it] ?: 0) + (quotaSkipped[it] ?: 0) })
        check(levels.all { (checked[it] ?: 0) == (unique[it] ?: 0) + (nonunique[it] ?: 0) })
        } finally {
            stopKoin()
        }
    }
})
