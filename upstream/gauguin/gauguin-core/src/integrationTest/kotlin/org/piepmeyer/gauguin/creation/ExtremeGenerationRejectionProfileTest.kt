package org.piepmeyer.gauguin.creation

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import org.piepmeyer.gauguin.creation.dlx.DLX
import org.piepmeyer.gauguin.creation.dlx.MathDokuDLX
import org.piepmeyer.gauguin.difficulty.GameDifficultyRater
import org.piepmeyer.gauguin.difficulty.ensureDifficultyCalculated
import org.piepmeyer.gauguin.grid.GridSize
import org.piepmeyer.gauguin.options.DifficultySetting
import org.piepmeyer.gauguin.options.GameOptionsVariant
import org.piepmeyer.gauguin.options.GameVariant

/**
 * Bounded diagnostic that separates candidate construction, classical rating
 * rejection, and DLX uniqueness. Does not invoke the production accept-until-
 * Extreme generator loop and does not change any production behavior.
 *
 * Its candidate loop mirrors the inner GridCreator + rating stages of
 * DifficultyAwareGridCreator. It intentionally does not claim that its sample
 * is a full end-to-end production generation time benchmark.
 */
class ExtremeGenerationRejectionProfileTest : FunSpec({
    val size = System.getenv("GAUGUIN_PROFILE_SIZE")?.toIntOrNull() ?: 10
    val seed = System.getenv("GAUGUIN_PROFILE_SEED")?.toIntOrNull() ?: 2
    val maxCandidates = System.getenv("GAUGUIN_PROFILE_MAX_CANDIDATES")?.toIntOrNull() ?: 2000
    val budgetMs = System.getenv("GAUGUIN_PROFILE_BUDGET_MS")?.toLongOrNull() ?: 30000L
    require(size in 9..11)
    require(seed >= 0)
    require(maxCandidates in 1..20000)
    require(budgetMs in 1000L..120000L)

    test("bounded classical Extreme acceptance profile size=$size seed=$seed") {
        val variant = GameVariant(
            GridSize(size, size),
            GameOptionsVariant.createClassic().copy(
                difficultiesSetting = setOf(DifficultySetting.EXTREME),
            ),
        )
        val randomizer = SeedRandomizerMock(seed)
        val creator = GridCreator(
            variant, randomizer, RandomPossibleDigitsShuffler(randomizer.random),
        )
        val rater = GameDifficultyRater()
        val threshold = rater.byVariant(variant)?.thresholdExtreme
            ?: error("Missing Extreme rating threshold for $size x $size")
        val frequencies = mutableMapOf<DifficultySetting, Int>()
        val observedScores = mutableListOf<Double>()
        var examined = 0
        var matches = 0
        var uniqueMatches = 0
        var nonUniqueMatches = 0
        var relaxedProbed = 0
        var relaxedUnique = 0
        var relaxedNonUnique = 0
        var generationNs = 0L
        var ratingNs = 0L
        var uniquenessNs = 0L
        var maxGenerationNs = 0L
        var maxRatingNs = 0L
        var scoreSum = 0.0
        var maxScore = Double.NEGATIVE_INFINITY
        val started = System.nanoTime()
        val budgetNs = budgetMs * 1_000_000L
        println("PROFILE_START size=$size seed=$seed maxCandidates=$maxCandidates budgetMs=$budgetMs extremeThreshold=$threshold")
        System.out.flush()

        try {
            while (examined < maxCandidates && System.nanoTime() - started < budgetNs) {
                val makeStart = System.nanoTime()
                val grid = creator.createRandomizedGridWithCages()
                val makeNs = System.nanoTime() - makeStart
                generationNs += makeNs
                if (makeNs > maxGenerationNs) maxGenerationNs = makeNs

                val rateStart = System.nanoTime()
                val level = rater.difficulty(grid)
                val score = grid.ensureDifficultyCalculated()
                val rateNs = System.nanoTime() - rateStart
                ratingNs += rateNs
                if (rateNs > maxRatingNs) maxRatingNs = rateNs

                examined++
                scoreSum += score
                observedScores.add(score)
                if (score > maxScore) maxScore = score
                if (level != null) {
                    frequencies[level] = (frequencies[level] ?: 0) + 1
                }
                if (score >= threshold) {
                    // Unlike a simple rating match, uniqueness is necessary
                    // for production acceptance; record that cost separately.
                    matches++
                    println("PROFILE_EXTREME_MATCH size=$size seed=$seed candidate=$examined score=$score")
                    System.out.flush()
                    val uniqueStart = System.nanoTime()
                    val solutions = MathDokuDLX(grid).solve(DLX.SolveType.MULTIPLE)
                    uniquenessNs += System.nanoTime() - uniqueStart
                    if (solutions == 1) uniqueMatches++ else nonUniqueMatches++
                    println("PROFILE_DLX size=$size seed=$seed candidate=$examined solutions=$solutions")
                    System.out.flush()
                }
                // Hypothetical 10% lower threshold: probe at most three candidates
                // for uniqueness, without modifying production acceptance.
                if (score >= threshold * 0.90 && score < threshold && relaxedProbed < 3) {
                    val uniqueStart = System.nanoTime()
                    val solutions = MathDokuDLX(grid).solve(DLX.SolveType.MULTIPLE)
                    uniquenessNs += System.nanoTime() - uniqueStart
                    relaxedProbed++
                    if (solutions == 1) relaxedUnique++ else relaxedNonUnique++
                    println("PROFILE_RELAXED_UNIQUENESS size=$size seed=$seed candidate=$examined score=$score solutions=$solutions")
                    System.out.flush()
                }
                if (examined % 250 == 0) {
                    println("PROFILE_CHECKPOINT size=$size seed=$seed candidates=$examined elapsedMs=${(System.nanoTime() - started) / 1_000_000L} matches=$matches")
                    System.out.flush()
                }
            }
            (examined > 0) shouldBe true
            frequencies.values.sum() shouldBe examined
        } finally {
            val elapsedMs = (System.nanoTime() - started) / 1_000_000L
            if (observedScores.isNotEmpty()) {
                val sorted = observedScores.sorted()
                fun percentile(p: Double): Double = sorted[((sorted.size - 1) * p).toInt()]
                println(
                    "PROFILE_CALIBRATION size=$size seed=$seed candidates=${sorted.size} " +
                        "scorePerCellMean=${scoreSum / sorted.size / (size * size)} " +
                        "p50=${percentile(0.50)} p90=${percentile(0.90)} " +
                        "p95=${percentile(0.95)} p99=${percentile(0.99)} " +
                        "aboveCurrent=${sorted.count { it >= threshold }} " +
                        "aboveMinus5Percent=${sorted.count { it >= threshold * 0.95 }} " +
                        "aboveMinus10Percent=${sorted.count { it >= threshold * 0.90 }} " +
                        "note=classical-only-no-uniqueness-or-human-certification",
                )
            }
            println(
                "PROFILE_SUMMARY size=$size seed=$seed attempted=$examined " +
                    "elapsedMs=$elapsedMs budgetMs=$budgetMs " +
                    "generationMs=${generationNs / 1_000_000L} ratingMs=${ratingNs / 1_000_000L} " +
                    "uniquenessMs=${uniquenessNs / 1_000_000L} " +
                    "maxGenerationMs=${maxGenerationNs / 1_000_000L} maxRatingMs=${maxRatingNs / 1_000_000L} " +
                    "meanScore=${if (examined == 0) 0.0 else scoreSum / examined} " +
                    "maxScore=$maxScore matches=$matches uniqueMatches=$uniqueMatches " +
                    "nonUniqueMatches=$nonUniqueMatches relaxedProbed=$relaxedProbed " +
                    "relaxedUnique=$relaxedUnique relaxedNonUnique=$relaxedNonUnique ratingCounts=${frequencies.toSortedMap(compareBy { it.ordinal })}",
            )
            System.out.flush()
        }
    }
})
