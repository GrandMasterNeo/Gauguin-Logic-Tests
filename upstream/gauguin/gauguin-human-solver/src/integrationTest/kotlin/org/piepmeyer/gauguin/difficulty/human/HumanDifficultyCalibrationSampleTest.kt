package org.piepmeyer.gauguin.difficulty.human

import io.kotest.core.spec.style.FunSpec
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
        val solved = mutableMapOf<DifficultySetting, Int>()
        val progress = mutableMapOf<DifficultySetting, MutableList<Double>>()
        val humanScores = mutableMapOf<DifficultySetting, MutableList<Int>>()
        val start = System.nanoTime()
        var attempted = 0
        println("CAL_THRESHOLDS size=$size seed=$seed easy=${thresholds.thresholdEasy} medium=${thresholds.thresholdMedium} hard=${thresholds.thresholdHard} extreme=${thresholds.thresholdExtreme}")
        while (attempted < limit && (System.nanoTime() - start) / 1_000_000 < budgetMs) {
            val grid = creator.createRandomizedGridWithCages()
            attempted++
            val level = rater.difficulty(grid) ?: error("Unrated generated grid")
            sampled[level] = (sampled[level] ?: 0) + 1
            // Cap expensive uniqueness and human-solving work independently per band.
            if ((checked[level] ?: 0) >= 3) continue
            checked[level] = (checked[level] ?: 0) + 1
            val solutions = MathDokuDLX(grid).solve(DLX.SolveType.MULTIPLE)
            if (solutions != 1) {
                println("CAL_CASE size=$size seed=$seed index=$attempted classical=$level score=${grid.ensureDifficultyCalculated()} solutions=$solutions human=NOT_TESTED")
                continue
            }
            unique[level] = (unique[level] ?: 0) + 1
            val work = grid.copyWithEmptyUserValues()
            val solver = HumanSolver(work, validate = true, avoidNishio = true)
            solver.prepareGrid()
            val result = solver.solveAndCalculateDifficulty(avoidReveal = true)
            val filled = work.cells.count { it.isUserValueSet }
            val fraction = filled.toDouble() / work.cells.size
            progress.getOrPut(level) { mutableListOf() }.add(fraction)
            humanScores.getOrPut(level) { mutableListOf() }.add(result.difficulty)
            val compliant = result.success && filled == work.cells.size && work.cells.none { it.userValue != it.value }
            if (compliant) solved[level] = (solved[level] ?: 0) + 1
            println("CAL_CASE size=$size seed=$seed index=$attempted classical=$level score=${grid.ensureDifficultyCalculated()} solutions=1 filled=$filled total=${work.cells.size} progress=$fraction humanScore=${result.difficulty} deductionOnly=$compliant")
        }
        val elapsedMs = (System.nanoTime() - start) / 1_000_000
        for (level in levels) {
            val values = progress[level].orEmpty()
            val scores = humanScores[level].orEmpty()
            println("CAL_SUMMARY size=$size seed=$seed level=$level sampled=${sampled[level] ?: 0} checked=${checked[level] ?: 0} unique=${unique[level] ?: 0} deductionSolved=${solved[level] ?: 0} meanProgress=${if (values.isEmpty()) "NA" else values.average()} meanHumanScore=${if (scores.isEmpty()) "NA" else scores.average()} attempted=$attempted elapsedMs=$elapsedMs")
        }
        check(attempted > 0)
        check(sampled.values.sum() == attempted)
    }
})
