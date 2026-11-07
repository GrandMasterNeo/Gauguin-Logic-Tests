package org.piepmeyer.gauguin.difficulty.human

import io.kotest.core.spec.style.FunSpec
import kotlinx.coroutines.runBlocking
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.piepmeyer.gauguin.creation.RandomCageGridCalculator
import org.piepmeyer.gauguin.creation.RandomPossibleDigitsShuffler
import org.piepmeyer.gauguin.creation.SeedRandomizerMock
import org.piepmeyer.gauguin.difficulty.GameDifficultyRatingService
import org.piepmeyer.gauguin.grid.GridSize
import org.piepmeyer.gauguin.options.DifficultySetting
import org.piepmeyer.gauguin.options.GameOptionsVariant
import org.piepmeyer.gauguin.options.GameVariant
import java.io.File

/**
 * Pure audit of beginner-strategy ceilings.
 * The workflow injects a test-only optional maxStrategyDifficulty into the
 * original HumanSolver; production APK/source remains unchanged.
 */
class BeginnerStrategyCeilingAuditTest : FunSpec({
    test("measure minimum available strategy tier on VE and E boards") {
        startKoin { modules(module { single { GameDifficultyRatingService() } }) }
        try {
            runBlocking {
                val size = System.getenv("GAUGUIN_AUDIT_SIZE")?.toIntOrNull() ?: 9
                val samples = System.getenv("GAUGUIN_BEGINNER_SAMPLES")?.toIntOrNull() ?: 4
                require(size in 9..11)
                require(samples in 1..4)
                val caps = listOf(5, 25, 70, 125, 210)
                val file = File("build/beginner-audit/beginner-" + size + ".csv")
                file.parentFile.mkdirs()
                file.writeText("size,requested_level,seed,first_solvable_cap,highest_used_strategy_weight,successful_steps,solver_score\n")

                for (level in listOf(DifficultySetting.VERY_EASY, DifficultySetting.EASY)) {
                    repeat(samples) { index ->
                        val seed = size * 100000 + level.ordinal * 1000 + index
                        val rng = SeedRandomizerMock(seed)
                        val variant = GameVariant(
                            GridSize(size, size),
                            GameOptionsVariant.createClassic().copy(difficultiesSetting = setOf(level)),
                        )
                        val grid = RandomCageGridCalculator(
                            variant, rng, RandomPossibleDigitsShuffler(rng.random),
                        ).calculate()
                        var successfulCap: Int? = null
                        var maximumUsedWeight: Int? = null
                        var steps: Int? = null
                        var solvedScore: Int? = null
                        for (cap in caps) {
                            val solver = HumanSolver(
                                grid.copyWithEmptyUserValues(),
                                validate = true,
                                avoidNishio = true,
                                maxStrategyDifficulty = cap,
                            )
                            solver.prepareGrid()
                            val result = solver.solveAndCalculateDifficulty(avoidReveal = true)
                            if (result.success) {
                                successfulCap = cap
                                maximumUsedWeight = solver.auditMaxStrategyDifficulty
                                steps = solver.auditSuccessfulSteps
                                solvedScore = result.difficulty
                                break
                            }
                        }
                        check(successfulCap != null) {
                            "Previously solvable without Nishio, but tier cap 210 failed: " +
                                size + " " + level + " " + seed
                        }
                        val line = listOf(
                            size, level.name, seed, successfulCap, maximumUsedWeight, steps, solvedScore,
                        ).joinToString(",")
                        file.appendText(line + "\n")
                        println("BEGINNER_AUDIT " + line)
                    }
                }
                println("Beginner strategy tier audit saved at " + file.absolutePath)
            }
        } finally {
            stopKoin()
        }
    }
})
