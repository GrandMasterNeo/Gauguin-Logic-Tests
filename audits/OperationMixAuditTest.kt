package org.piepmeyer.gauguin.difficulty.human

import io.kotest.core.spec.style.FunSpec
import kotlinx.coroutines.runBlocking
import org.piepmeyer.gauguin.creation.MergingCageGridCalculator
import org.piepmeyer.gauguin.creation.RandomCageGridCalculator
import org.piepmeyer.gauguin.creation.RandomPossibleDigitsShuffler
import org.piepmeyer.gauguin.creation.SeedRandomizerMock
import org.piepmeyer.gauguin.creation.cage.GridSingleCageCreator
import org.piepmeyer.gauguin.difficulty.GameDifficultyRater
import org.piepmeyer.gauguin.difficulty.ensureDifficultyCalculated
import org.piepmeyer.gauguin.grid.GridCageAction
import org.piepmeyer.gauguin.grid.GridSize
import org.piepmeyer.gauguin.options.DifficultySetting
import org.piepmeyer.gauguin.options.GameOptionsVariant
import org.piepmeyer.gauguin.options.GameVariant
import java.io.File
import kotlin.math.ln

/** Audit full uniquely-solvable boards; leave production code unchanged. */
class OperationMixAuditTest : FunSpec({
    test("measure operation mix on generated boards") {
        runBlocking {
            val size = System.getenv("GAUGUIN_AUDIT_SIZE")?.toIntOrNull() ?: 9
            val samples = System.getenv("GAUGUIN_AUDIT_SAMPLES")?.toIntOrNull() ?: 4
            require(size in 3..11)
            require(samples in 1..100)

            val rating = GameDifficultyRater()
            val rows = mutableListOf(
                "size,algorithm,requested_level,actual_classical_level,seed,classical_score," +
                    "total_cages,single_cages,two_cell_cages,three_plus_cell_cages," +
                    "add_cages,subtract_cages,multiply_cages,divide_cages," +
                    "add_cells,subtract_cells,multiply_cells,divide_cells," +
                    "add_avg_candidates,subtract_avg_candidates,multiply_avg_candidates," +
                    "divide_avg_candidates,log_product_candidates",
            )

            for (algorithm in listOf("classic", "merging")) {
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
                        val shuffler = RandomPossibleDigitsShuffler(rng.random)
                        val grid = if (algorithm == "classic") {
                            RandomCageGridCalculator(variant, rng, shuffler).calculate()
                        } else {
                            MergingCageGridCalculator(variant, rng, shuffler).calculate()
                        }
                        val cages = grid.cages.toList()
                        val counts = cages.groupingBy { it.action }.eachCount()
                        val candidates = cages.associateWith {
                            GridSingleCageCreator(variant, it).possibleCombinations.size
                        }
                        check(candidates.values.all { it > 0 })
                        val add = GridCageAction.ACTION_ADD
                        val sub = GridCageAction.ACTION_SUBTRACT
                        val mul = GridCageAction.ACTION_MULTIPLY
                        val div = GridCageAction.ACTION_DIVIDE
                        val none = GridCageAction.ACTION_NONE
                        fun count(op: GridCageAction) = counts[op] ?: 0
                        fun cells(op: GridCageAction) =
                            cages.filter { it.action == op }.sumOf { it.numberOfCells }
                        fun avg(op: GridCageAction): String {
                            val selected = cages.filter { it.action == op }
                            return if (selected.isEmpty()) "" else
                                selected.map { candidates.getValue(it).toDouble() }.average().toString()
                        }
                        check(cages.size == count(add) + count(sub) + count(mul) + count(div) + count(none))
                        check(cages.filter { it.action == sub || it.action == div }
                            .all { it.numberOfCells == 2 })
                        check(cages.sumOf { it.numberOfCells } == size * size)
                        val classical = grid.ensureDifficultyCalculated()
                        val actualLevel = rating.difficulty(grid)
                        if (algorithm == "classic" && rating.isSupported(variant)) {
                            check(actualLevel == level) {
                                "Classic generator mismatched requested difficulty: " +
                                    size + " " + level + " " + actualLevel + " " + seed
                            }
                        }
                        val fields = listOf(
                            size, algorithm, level.name, actualLevel?.name ?: "UNRATED",
                            seed, classical, cages.size, count(none),
                            cages.count { it.numberOfCells == 2 },
                            cages.count { it.numberOfCells >= 3 },
                            count(add), count(sub), count(mul), count(div),
                            cells(add), cells(sub), cells(mul), cells(div),
                            avg(add), avg(sub), avg(mul), avg(div),
                            candidates.values.sumOf { ln(it.toDouble()) },
                        )
                        val line = fields.joinToString(",")
                        rows += line
                        println("AUDIT " + line)
                    }
                }
            }

            val file = File("build/operation-mix/operation-mix-" + size + ".csv")
            file.parentFile.mkdirs()
            file.writeText(rows.joinToString("\n", postfix = "\n"))
            println("Audit saved: " + file.absolutePath + " with " + (rows.size - 1) + " boards")
        }
    }
})
