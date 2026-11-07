package org.piepmeyer.gauguin.difficulty.human

import io.kotest.core.spec.style.FunSpec
import kotlinx.coroutines.runBlocking
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.piepmeyer.gauguin.creation.RandomCageGridCalculator
import org.piepmeyer.gauguin.creation.RandomPossibleDigitsShuffler
import org.piepmeyer.gauguin.creation.SeedRandomizerMock
import org.piepmeyer.gauguin.creation.cage.GridSingleCageCreator
import org.piepmeyer.gauguin.difficulty.GameDifficultyRater
import org.piepmeyer.gauguin.difficulty.GameDifficultyRatingService
import org.piepmeyer.gauguin.grid.GridCageAction
import org.piepmeyer.gauguin.grid.GridSize
import org.piepmeyer.gauguin.options.DifficultySetting
import org.piepmeyer.gauguin.options.GameOptionsVariant
import org.piepmeyer.gauguin.options.GameVariant
import java.io.File

/** Real, uniquely solvable 11x11 boards and the actual 37-strategy human solver. */
class CandidateDistributionIntegrationTest : FunSpec({
    test("medium and hard candidate distribution and human deduction audit") {
        startKoin { modules(module { single { GameDifficultyRatingService() } }) }
        try {
            runBlocking {
                val verifier = HumanGridAcceptanceVerifier()
                val rater = GameDifficultyRater()
                val csv = mutableListOf(
                    "level,seed,add,subtract,multiply,divide,singles,arithmetic,large12,large20,humanAccepted",
                )
                for (level in DifficultySetting.entries) {
                    for (index in 0..3) {
                        val seed = 229000 + level.ordinal * 100 + index
                        val rng = SeedRandomizerMock(seed)
                        val variant = GameVariant(
                            GridSize(11, 11),
                            GameOptionsVariant.createClassic().copy(
                                difficultiesSetting = setOf(level),
                            ),
                        )
                        // Original generator already enforces mathematical uniqueness.
                        val grid = RandomCageGridCalculator(
                            variant, rng, RandomPossibleDigitsShuffler(rng.random),
                        ).calculate()
                        check(rater.difficulty(grid) == level)
                        check(grid.cages.sumOf { it.numberOfCells } == 121)
                        val counts = grid.cages.groupingBy { it.action }.eachCount()
                        val combinations = grid.cages
                            .filter { it.action != GridCageAction.ACTION_NONE }
                            .map { cage ->
                                GridSingleCageCreator(variant, cage)
                                    .possibleCombinations.map { it.sorted() }.toSet().size
                            }
                        val large12 = combinations.count { it >= 12 }
                        val large20 = combinations.count { it >= 20 }
                        val accepted = verifier.accepts(grid)
                        val row = listOf(
                            level.name, seed,
                            counts[GridCageAction.ACTION_ADD] ?: 0,
                            counts[GridCageAction.ACTION_SUBTRACT] ?: 0,
                            counts[GridCageAction.ACTION_MULTIPLY] ?: 0,
                            counts[GridCageAction.ACTION_DIVIDE] ?: 0,
                            counts[GridCageAction.ACTION_NONE] ?: 0,
                            combinations.size, large12, large20, accepted,
                        ).joinToString(",")
                        csv.add(row)
                        println("HUMAN_DEDUCTION_AUDIT " + row)
                    }
                }
                val file = File("build/operation-balance/human-deduction-11x11.csv")
                file.parentFile.mkdirs()
                file.writeText(csv.joinToString("\n", postfix = "\n"))
                check(csv.size == 21)
                println("HUMAN_DEDUCTION_AUDIT_SUCCESS grids=20")
            }
        } finally {
            stopKoin()
        }
    }
})
