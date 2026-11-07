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
import org.piepmeyer.gauguin.grid.GridCageAction
import org.piepmeyer.gauguin.grid.GridSize
import org.piepmeyer.gauguin.options.DifficultySetting
import org.piepmeyer.gauguin.options.GameOptionsVariant
import org.piepmeyer.gauguin.options.GameVariant
import java.io.File

/** Measures actual uniquely-solvable grids after operation bias changes. */
class OperationBalanceIntegrationTest : FunSpec({
    test("large-grid arithmetic mix and classical difficulty audit") {
        startKoin { modules(module { single { GameDifficultyRatingService() } }) }
        try {
            runBlocking {
                val rater = GameDifficultyRater()
                val csv = mutableListOf(
                    "size,level,seed,add,subtract,multiply,divide,singles,all_cages," +
                        "arithmetic_cages,add_share,multiply_share,difficulty",
                )
                for (size in listOf(9, 10, 11)) {
                    val sizes = mutableMapOf<GridCageAction, Int>()
                    for (level in DifficultySetting.entries) {
                        // Two seeds per level: 30 unique boards total.
                        for (index in 0..1) {
                            val seed = 128000 + size * 1000 + level.ordinal * 10 + index
                            val rng = SeedRandomizerMock(seed)
                            val variant = GameVariant(
                                GridSize(size, size),
                                GameOptionsVariant.createClassic().copy(
                                    difficultiesSetting = setOf(level),
                                ),
                            )
                            val grid = RandomCageGridCalculator(
                                variant, rng, RandomPossibleDigitsShuffler(rng.random),
                            ).calculate()
                            check(rater.difficulty(grid) == level) {
                                "Classical level mismatch: size=$size level=$level seed=$seed"
                            }
                            check(grid.cages.sumOf { it.numberOfCells } == size * size)
                            val counts = grid.cages.groupingBy { it.action }.eachCount()
                            val add = counts[GridCageAction.ACTION_ADD] ?: 0
                            val sub = counts[GridCageAction.ACTION_SUBTRACT] ?: 0
                            val mul = counts[GridCageAction.ACTION_MULTIPLY] ?: 0
                            val div = counts[GridCageAction.ACTION_DIVIDE] ?: 0
                            val single = counts[GridCageAction.ACTION_NONE] ?: 0
                            check(add + sub + mul + div + single == grid.cages.size)
                            check(grid.cages.filter {
                                it.action == GridCageAction.ACTION_SUBTRACT || it.action == GridCageAction.ACTION_DIVIDE
                            }.all { it.numberOfCells == 2 })
                            val arith = add + sub + mul + div
                            check(arith > 0)
                            sizes[GridCageAction.ACTION_ADD] =
                                (sizes[GridCageAction.ACTION_ADD] ?: 0) + add
                            sizes[GridCageAction.ACTION_MULTIPLY] =
                                (sizes[GridCageAction.ACTION_MULTIPLY] ?: 0) + mul
                            val record = listOf(
                                size, level.name, seed, add, sub, mul, div, single,
                                grid.cages.size, arith, add.toDouble() / arith,
                                mul.toDouble() / arith, grid.difficulty.classicalRating ?: 0.0,
                            ).joinToString(",")
                            csv.add(record)
                            println("BALANCE_AUDIT " + record)
                        }
                    }
                    val aggregateAdd = sizes[GridCageAction.ACTION_ADD] ?: 0
                    val aggregateMultiply = sizes[GridCageAction.ACTION_MULTIPLY] ?: 0
                    check(aggregateMultiply > aggregateAdd) {
                        "Expected multiplication to exceed addition for $size grid: " +
                            "mult=$aggregateMultiply add=$aggregateAdd"
                    }
                }
                val file = File("build/operation-balance/large-grid-balance.csv")
                file.parentFile.mkdirs()
                file.writeText(csv.joinToString("\n", postfix = "\n"))
                check(csv.size == 31)
                println("OPERATION_BALANCE_SUCCESS boards=" + (csv.size - 1))
            }
        } finally {
            stopKoin()
        }
    }
})
