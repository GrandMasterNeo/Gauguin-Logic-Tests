package org.piepmeyer.gauguin.creation

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import org.piepmeyer.gauguin.creation.dlx.DLX
import org.piepmeyer.gauguin.creation.dlx.MathDokuDLX
import org.piepmeyer.gauguin.difficulty.GameDifficultyRater
import org.piepmeyer.gauguin.grid.GridSize
import org.piepmeyer.gauguin.options.DifficultySetting
import org.piepmeyer.gauguin.options.GameOptionsVariant
import org.piepmeyer.gauguin.options.GameVariant

/**
 * One bounded diagnostic cell, not a certificate of human deduction-only solvability.
 * A rating match is never treated as an accepted puzzle unless DLX uniqueness passes.
 */
class ProgressiveGenerationMatrixTest : FunSpec({
    val size = System.getenv("GAUGUIN_MATRIX_SIZE")?.toIntOrNull() ?: 3
    val difficulty = DifficultySetting.valueOf(System.getenv("GAUGUIN_MATRIX_DIFFICULTY") ?: "VERY_EASY")
    val seed = System.getenv("GAUGUIN_MATRIX_SEED")?.toIntOrNull() ?: 0
    val maxCandidates = System.getenv("GAUGUIN_MATRIX_MAX_CANDIDATES")?.toIntOrNull() ?: 1000
    val budgetMs = System.getenv("GAUGUIN_MATRIX_BUDGET_MS")?.toLongOrNull() ?: 60000L
    require(size in 3..11 && seed >= 0 && maxCandidates in 1..1000 && budgetMs in 1000L..60000L)

    test("bounded generation size=$size difficulty=$difficulty seed=$seed") {
        val variant = GameVariant(
            GridSize(size, size),
            GameOptionsVariant.createClassic().copy(difficultiesSetting = setOf(difficulty)),
        )
        val randomizer = SeedRandomizerMock(seed)
        val creator = GridCreator(variant, randomizer, RandomPossibleDigitsShuffler(randomizer.random))
        val rater = GameDifficultyRater()
        var examined = 0
        var ratingMatches = 0
        var uniqueMatches = 0
        var nonUniqueMatches = 0
        var ratingMs = 0L
        var generationMs = 0L
        var uniquenessMs = 0L
        val started = System.nanoTime()
        println("MATRIX_START size=$size difficulty=$difficulty seed=$seed budgetMs=$budgetMs maxCandidates=$maxCandidates")
        try {
            while (examined < maxCandidates && (System.nanoTime() - started) / 1_000_000 < budgetMs && uniqueMatches == 0) {
                val makeStart = System.nanoTime()
                val grid = creator.createRandomizedGridWithCages()
                generationMs += (System.nanoTime() - makeStart) / 1_000_000
                examined++
                val rateStart = System.nanoTime()
                val actual = rater.difficulty(grid)
                ratingMs += (System.nanoTime() - rateStart) / 1_000_000
                if (actual == difficulty) {
                    ratingMatches++
                    val dlxStart = System.nanoTime()
                    val solutions = MathDokuDLX(grid).solve(DLX.SolveType.MULTIPLE)
                    uniquenessMs += (System.nanoTime() - dlxStart) / 1_000_000
                    if (solutions == 1) uniqueMatches++ else nonUniqueMatches++
                }
            }
        } finally {
            val elapsedMs = (System.nanoTime() - started) / 1_000_000
            println("MATRIX_RESULT size=$size difficulty=$difficulty seed=$seed examined=$examined ratingMatches=$ratingMatches uniqueMatches=$uniqueMatches nonUniqueMatches=$nonUniqueMatches elapsedMs=$elapsedMs generationMs=$generationMs ratingMs=$ratingMs uniquenessMs=$uniquenessMs humanCertified=false")
            System.out.flush()
        }
        // A diagnostic can succeed at collecting evidence even if no valid candidate was found.
        (examined > 0) shouldBe true
    }
})
