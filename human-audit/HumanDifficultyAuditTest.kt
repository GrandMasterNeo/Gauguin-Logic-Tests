package org.piepmeyer.gauguin.difficulty.human

import io.kotest.core.spec.style.FunSpec
import kotlinx.coroutines.runBlocking
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.piepmeyer.gauguin.creation.RandomCageGridCalculator
import org.piepmeyer.gauguin.creation.RandomPossibleDigitsShuffler
import org.piepmeyer.gauguin.creation.SeedRandomizerMock
import org.piepmeyer.gauguin.difficulty.GameDifficultyRater
import org.piepmeyer.gauguin.difficulty.GameDifficultyRatingService
import org.piepmeyer.gauguin.difficulty.ensureDifficultyCalculated
import org.piepmeyer.gauguin.grid.GridSize
import org.piepmeyer.gauguin.options.DifficultySetting
import org.piepmeyer.gauguin.options.GameOptionsVariant
import org.piepmeyer.gauguin.options.GameVariant
import java.io.File

/**
 * Reproduce one or more 9x9 / 10x10 / 11x11 puzzles per classical level
 * using exactly the same seeds as OperationMixAuditTest.
 * Judge human solver reachability both WITHOUT Nishio and WITH Nishio;
 * neither pass permits a revealed answer.
 *
 * IMPORTANT: success in this specific automated solver is not a formal
 * proof that a novice human can solve the puzzle.
 */
class HumanDifficultyAuditTest : FunSpec({
    test("sample classical levels and audit human solver reachability") {
        startKoin {
            modules(module { single { GameDifficultyRatingService() } })
        }
        try {
            runBlocking {
                val size = System.getenv("GAUGUIN_AUDIT_SIZE")?.toIntOrNull() ?: 9
                val samples = System.getenv("GAUGUIN_HUMAN_SAMPLES")?.toIntOrNull() ?: 1
                require(size in 9..11)
                require(samples in 1..10)

                val filename = File("build/human-audit/human-audit-" + size + ".csv")
                filename.parentFile.mkdirs()
                filename.writeText(
                    "size,algorithm,requested_level,classical_level,seed,classical_score," +
                        "solved_without_nishio,score_without_nishio," +
                        "solved_with_nishio,used_nishio,score_with_nishio\n",
                )
                val rating = GameDifficultyRater()

                for (level in DifficultySetting.entries) {
                    repeat(samples) { index ->
                        val seed = size * 100000 + level.ordinal * 1000 + index
                        val rng = SeedRandomizerMock(seed)
                        val variant = GameVariant(
                            GridSize(size, size),
                            GameOptionsVariant.createClassic().copy(
                                difficultiesSetting = setOf(level),
                            ),
                        )
                        val grid = RandomCageGridCalculator(
                            variant,
                            rng,
                            RandomPossibleDigitsShuffler(rng.random),
                        ).calculate()

                        val oldScore = grid.ensureDifficultyCalculated()
                        val actualLevel = rating.difficulty(grid)
                        check(actualLevel == level) {
                            "Classical rating mismatch for " + size + ", " + level + ", seed " + seed
                        }

                        val without = HumanSolver(
                            grid.copyWithEmptyUserValues(),
                            validate = true,
                            avoidNishio = true,
                        )
                        without.prepareGrid()
                        val withoutResult = without.solveAndCalculateDifficulty(avoidReveal = true)

                        val with = HumanSolver(
                            grid.copyWithEmptyUserValues(),
                            validate = true,
                            avoidNishio = false,
                        )
                        with.prepareGrid()
                        val withResult = with.solveAndCalculateDifficulty(avoidReveal = true)

                        check(!withoutResult.success || withResult.success) {
                            "Allowing more strategies made a formerly solved grid unsolved"
                        }
                        val line = listOf(
                            size, "classic", level.name, actualLevel?.name ?: "UNRATED",
                            seed, oldScore,
                            withoutResult.success, withoutResult.difficulty,
                            withResult.success, withResult.usedNishio, withResult.difficulty,
                        ).joinToString(",")
                        filename.appendText(line + "\n")
                        println("HUMAN_AUDIT " + line)
                    }
                }
                println("Human difficulty audit saved " + filename.absolutePath)
            }
        } finally {
            stopKoin()
        }
    }
})
