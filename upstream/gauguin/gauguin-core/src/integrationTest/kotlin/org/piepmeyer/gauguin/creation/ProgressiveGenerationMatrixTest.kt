package org.piepmeyer.gauguin.creation

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.piepmeyer.gauguin.creation.dlx.DLX
import org.piepmeyer.gauguin.creation.dlx.MathDokuDLX
import org.piepmeyer.gauguin.difficulty.GameDifficultyRater
import org.piepmeyer.gauguin.difficulty.GameDifficultyRatingService
import org.piepmeyer.gauguin.grid.GridSize
import org.piepmeyer.gauguin.options.DigitSetting
import org.piepmeyer.gauguin.options.DifficultySetting
import org.piepmeyer.gauguin.options.GameOptionsVariant
import org.piepmeyer.gauguin.options.GameVariant

/**
 * Exercise the production calculator, including its difficulty and uniqueness
 * rejection gates. A passing result is NOT human deduction-only certification.
 */
class ProgressiveGenerationMatrixTest : FunSpec({
    val size = System.getenv("GAUGUIN_MATRIX_SIZE")?.toIntOrNull() ?: 3
    val difficulty = DifficultySetting.valueOf(System.getenv("GAUGUIN_MATRIX_DIFFICULTY") ?: "VERY_EASY")
    val digitSetting = DigitSetting.valueOf(System.getenv("GAUGUIN_MATRIX_DIGIT_SETTING") ?: "FIRST_DIGIT_ONE")
    val seed = System.getenv("GAUGUIN_MATRIX_SEED")?.toIntOrNull() ?: 0
    val maxCandidates = System.getenv("GAUGUIN_MATRIX_MAX_CANDIDATES")?.toIntOrNull() ?: 1000
    val budgetMs = System.getenv("GAUGUIN_MATRIX_BUDGET_MS")?.toLongOrNull() ?: 60000L
    require(size in 3..11 && seed >= 0 && maxCandidates in 1..100000 && budgetMs in 1000L..60000L)

    test("production generation size=$size difficulty=$difficulty seed=$seed") {
        val variant = GameVariant(
            GridSize(size, size),
            GameOptionsVariant.createClassic(digitSetting).copy(difficultiesSetting = setOf(difficulty)),
        )
        // Properties are read by the production calculator when calculate() starts.
        System.setProperty("gauguin.generation.maxAttempts", maxCandidates.toString())
        System.setProperty("gauguin.generation.maxMillis", budgetMs.toString())
        startKoin { modules(module { single { GameDifficultyRatingService() } }) }
        val started = System.nanoTime()
        println("MATRIX_START size=$size difficulty=$difficulty seed=$seed production=true digitSetting=$digitSetting budgetMs=$budgetMs maxCandidates=$maxCandidates")
        try {
            val randomizer = SeedRandomizerMock(seed)
            val grid = runBlocking {
                RandomCageGridCalculator(
                    variant, randomizer, RandomPossibleDigitsShuffler(randomizer.random),
                ).calculate()
            }
            val actual = GameDifficultyRater().difficulty(grid)
            val solutions = MathDokuDLX(grid).solve(DLX.SolveType.MULTIPLE)
            val elapsedMs = (System.nanoTime() - started) / 1_000_000
            println("MATRIX_RESULT size=$size difficulty=$difficulty seed=$seed production=true digitSetting=$digitSetting actual=$actual solutions=$solutions elapsedMs=$elapsedMs humanCertified=false")
            System.out.flush()
            actual shouldBe difficulty
            solutions shouldBe 1
        } finally {
            stopKoin()
            System.clearProperty("gauguin.generation.maxAttempts")
            System.clearProperty("gauguin.generation.maxMillis")
        }
    }
})
