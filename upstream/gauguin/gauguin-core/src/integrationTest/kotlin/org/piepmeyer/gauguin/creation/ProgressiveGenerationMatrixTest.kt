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
import org.piepmeyer.gauguin.options.NumeralSystem
import org.piepmeyer.gauguin.options.SingleCageUsage

/**
 * Exercise the production calculator, including its difficulty and uniqueness
 * rejection gates. A passing result is NOT human deduction-only certification.
 */
class ProgressiveGenerationMatrixTest : FunSpec({
    val size = System.getenv("GAUGUIN_MATRIX_SIZE")?.toIntOrNull() ?: 3
    val difficulty = DifficultySetting.valueOf(System.getenv("GAUGUIN_MATRIX_DIFFICULTY") ?: "VERY_EASY")
    val digitSetting = DigitSetting.valueOf(System.getenv("GAUGUIN_MATRIX_DIGIT_SETTING") ?: "FIRST_DIGIT_ONE")
    val numeralSystem = NumeralSystem.valueOf(System.getenv("GAUGUIN_MATRIX_NUMERAL_SYSTEM") ?: "Decimal")
    val singleCageUsage = SingleCageUsage.valueOf(System.getenv("GAUGUIN_MATRIX_SINGLE_CAGE") ?: "FIXED_NUMBER")
    val cageOperation = org.piepmeyer.gauguin.options.GridCageOperation.valueOf(System.getenv("GAUGUIN_MATRIX_CAGE_OPERATION") ?: "OPERATIONS_ALL")
    val showOperators = (System.getenv("GAUGUIN_MATRIX_SHOW_OPERATORS") ?: "true").toBooleanStrict()
    val seed = System.getenv("GAUGUIN_MATRIX_SEED")?.toIntOrNull() ?: 0
    val maxCandidates = System.getenv("GAUGUIN_MATRIX_MAX_CANDIDATES")?.toIntOrNull() ?: 1000
    val budgetMs = System.getenv("GAUGUIN_MATRIX_BUDGET_MS")?.toLongOrNull() ?: 60000L
    // Opt-in experimental generator parameters; unset means unmodified baseline.
    val experimentalLargeCagePercent = System.getenv("GAUGUIN_MATRIX_LARGE_CAGE_PERCENT")?.toIntOrNull()
    val experimentalZeroMultiplicationWeight = System.getenv("GAUGUIN_MATRIX_ZERO_MULTIPLICATION_WEIGHT")?.toIntOrNull()
    require(experimentalLargeCagePercent == null || experimentalLargeCagePercent in 0..100)
    require(experimentalZeroMultiplicationWeight == null || experimentalZeroMultiplicationWeight in 0..100)
    require(size in 3..11 && seed >= 0 && maxCandidates in 1..100000 && budgetMs in 1000L..60000L)

    test("production generation size=$size difficulty=$difficulty seed=$seed") {
        val variant = GameVariant(
            GridSize(size, size),
            GameOptionsVariant.createClassic(digitSetting).copy(
                difficultiesSetting = setOf(difficulty),
                numeralSystem = numeralSystem,
                singleCageUsage = singleCageUsage,
                cageOperation = cageOperation,
                showOperators = showOperators,
            ),
        )
        // Properties are read by the production calculator when calculate() starts.
        System.setProperty("gauguin.generation.maxAttempts", maxCandidates.toString())
        System.setProperty("gauguin.generation.maxMillis", budgetMs.toString())
        experimentalLargeCagePercent?.let {
            System.setProperty("gauguin.generation.experimentalLargeCagePercent", it.toString())
        }
        experimentalZeroMultiplicationWeight?.let {
            System.setProperty("gauguin.generation.experimentalZeroMultiplicationWeight", it.toString())
        }
        System.setProperty("gauguin.generation.diagnosticStartMs", (System.getenv("GAUGUIN_DEBUG_START_MS")?.toLongOrNull() ?: 30_000L).toString())
        System.setProperty("gauguin.generation.deepStartMs", (System.getenv("GAUGUIN_DEBUG_DEEP_MS")?.toLongOrNull() ?: 50_000L).toString())
        System.setProperty("gauguin.generation.diagnosticRejectThreshold", (System.getenv("GAUGUIN_DEBUG_REJECTIONS")?.toIntOrNull() ?: 5_000).toString())
        startKoin { modules(module { single { GameDifficultyRatingService() } }) }
        val started = System.nanoTime()
        println("MATRIX_START size=$size difficulty=$difficulty seed=$seed production=true digitSetting=$digitSetting numeralSystem=$numeralSystem singleCage=$singleCageUsage cageOperation=$cageOperation showOperators=$showOperators budgetMs=$budgetMs maxCandidates=$maxCandidates largeCagePercent=${experimentalLargeCagePercent ?: 0} zeroMultiplicationWeight=${experimentalZeroMultiplicationWeight ?: 65}")
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
            println("MATRIX_RESULT size=$size difficulty=$difficulty seed=$seed production=true digitSetting=$digitSetting numeralSystem=$numeralSystem singleCage=$singleCageUsage cageOperation=$cageOperation showOperators=$showOperators actual=$actual solutions=$solutions elapsedMs=$elapsedMs humanCertified=false")
            System.out.flush()
            actual shouldBe difficulty
            solutions shouldBe 1
        } finally {
            stopKoin()
            System.clearProperty("gauguin.generation.maxAttempts")
            System.clearProperty("gauguin.generation.maxMillis")
            System.clearProperty("gauguin.generation.experimentalLargeCagePercent")
            System.clearProperty("gauguin.generation.experimentalZeroMultiplicationWeight")
            System.clearProperty("gauguin.generation.diagnosticStartMs")
            System.clearProperty("gauguin.generation.deepStartMs")
            System.clearProperty("gauguin.generation.diagnosticRejectThreshold")
        }
    }
})
