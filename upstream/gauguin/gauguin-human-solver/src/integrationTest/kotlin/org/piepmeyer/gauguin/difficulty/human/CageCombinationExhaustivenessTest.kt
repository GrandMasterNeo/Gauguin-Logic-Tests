package org.piepmeyer.gauguin.difficulty.human

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import org.piepmeyer.gauguin.creation.cage.GridCageType
import org.piepmeyer.gauguin.creation.cage.GridSingleCageCreator
import org.piepmeyer.gauguin.grid.Grid
import org.piepmeyer.gauguin.grid.GridCage
import org.piepmeyer.gauguin.grid.GridCageAction
import org.piepmeyer.gauguin.grid.GridSize
import org.piepmeyer.gauguin.options.DigitSetting
import org.piepmeyer.gauguin.options.GameOptionsVariant
import org.piepmeyer.gauguin.options.GameVariant

/**
 * Independent exhaustive combination oracle for the underlying cage cache.
 *
 * All 37 strategies rely on this cache containing every legal cage assignment.
 * A missing combination can make an otherwise valid strategy unsound.
 *
 * For each tested geometry, enumerates every possible tuple, independently
 * checks row/column clashes by coordinate (rather than using the code's
 * cageType.satisfiesConstraints function), and groups targets by arithmetic
 * result. Then checks every reachable target group against GridSingleCageCreator.
 *
 * This tests small/large square sizes, 1-based, 0-based and noncontiguous
 * prime digits and includes product-0 cages where applicable.
 */
class CageCombinationExhaustivenessTest :
    FunSpec({
        val cageTypes = listOf(
            GridCageType.DOUBLE_HORIZONTAL,
            GridCageType.TRIPLE_HORIZONTAL,
            GridCageType.ANGLE_RIGHT_BOTTOM,
            GridCageType.SQUARE,
            GridCageType.FOUR_HORIZONTAL,
        )
        val digitSettings = listOf(
            DigitSetting.FIRST_DIGIT_ONE,
            DigitSetting.FIRST_DIGIT_ZERO,
            DigitSetting.PRIME_NUMBERS,
        )

        fun validGeometry(type: GridCageType, tuple: List<Int>): Boolean =
            tuple.indices.all { i ->
                ((i + 1) until tuple.size).all { j ->
                    val a = type.coordinates[i]
                    val b = type.coordinates[j]
                    // Latin row/column uniqueness is the independent oracle.
                    if (a.first == b.first || a.second == b.second) {
                        tuple[i] != tuple[j]
                    } else {
                        true
                    }
                }
            }

        fun allValidTuples(type: GridCageType, digits: List<Int>): List<List<Int>> {
            val answer = mutableListOf<List<Int>>()
            fun build(prefix: List<Int>) {
                if (prefix.size == type.coordinates.size) {
                    if (validGeometry(type, prefix)) answer += prefix
                    return
                }
                for (n in digits) build(prefix + n)
            }
            build(emptyList())
            return answer
        }

        fun arithmeticMatches(action: GridCageAction, tuple: List<Int>, target: Int): Boolean =
            when (action) {
                GridCageAction.ACTION_ADD -> tuple.sum() == target
                GridCageAction.ACTION_MULTIPLY -> tuple.fold(1) { a, b -> a * b } == target
                GridCageAction.ACTION_SUBTRACT ->
                    tuple.size == 2 && kotlin.math.abs(tuple[0] - tuple[1]) == target
                GridCageAction.ACTION_DIVIDE ->
                    tuple.size == 2 && (
                        if (target == 0) (tuple[0] == 0).xor(tuple[1] == 0)
                        else {
                            val larger = maxOf(tuple[0], tuple[1])
                            val smaller = minOf(tuple[0], tuple[1])
                            smaller != 0 && larger == smaller * target
                        }
                    )
                else -> false
            }

        // Kotest applies a 10-minute timeout to an individual test. The
        // old monolithic 4/9/11 x three-digit-sets test completed all 513
        // target comparisons, then exceeded that timeout while finalizing.
        // Splitting the same exhaustive checks into separately reported
        // board-size/digit-set cases preserves coverage and pinpoints failure.
        val sizeFilter = System.getenv("GAUGUIN_CAGE_SIZE")?.toIntOrNull()
        require(sizeFilter == null || sizeFilter in setOf(4, 9, 11))
        val typeFilter = System.getenv("GAUGUIN_CAGE_TYPE")?.takeIf { it.isNotBlank() }
        require(typeFilter == null || cageTypes.any { it.name == typeFilter }) {
            "Unknown cage geometry filter: $typeFilter"
        }
        val selectedTypes = cageTypes.filter { typeFilter == null || it.name == typeFilter }
        val digitFilter = System.getenv("GAUGUIN_CAGE_DIGITS")?.takeIf { it.isNotBlank() }
        require(digitFilter == null || digitSettings.any { it.name == digitFilter }) {
            "Unknown digit setting filter: $digitFilter"
        }

        val shardCount = System.getenv("GAUGUIN_CAGE_SHARD_COUNT")?.toIntOrNull() ?: 1
        val shardIndex = System.getenv("GAUGUIN_CAGE_SHARD_INDEX")?.toIntOrNull() ?: 0
        require(shardCount in 1..16 && shardIndex in 0 until shardCount)

        for (size in listOf(4, 9, 11).filter { sizeFilter == null || it == sizeFilter }) {
            for (digitSetting in digitSettings.filter { digitFilter == null || it.name == digitFilter }) {
                test("independently exhaustive cage tuples size=$size digits=$digitSetting") {
                    var checkedTargets = 0
                    var targetOrdinal = 0
                    var validatedLegalTuples = 0

                    val variant = GameVariant(
                        GridSize(size, size),
                        GameOptionsVariant.createClassic(digitSetting),
                    )
                    val digits = variant.possibleDigits.sorted()
                    val grid = Grid(variant)

                    for (type in selectedTypes) {
                        val valid = allValidTuples(type, digits)
                        validatedLegalTuples += valid.size
                        (valid.isNotEmpty()) shouldBe true

                        val actions = if (type.coordinates.size == 2) {
                            listOf(
                                GridCageAction.ACTION_ADD,
                                GridCageAction.ACTION_MULTIPLY,
                                GridCageAction.ACTION_SUBTRACT,
                                GridCageAction.ACTION_DIVIDE,
                            )
                        } else {
                            listOf(GridCageAction.ACTION_ADD, GridCageAction.ACTION_MULTIPLY)
                        }

                        for (action in actions) {
                            val results = valid.map { tuple ->
                                when (action) {
                                    GridCageAction.ACTION_ADD -> tuple.sum()
                                    GridCageAction.ACTION_MULTIPLY -> tuple.fold(1) { a, b -> a * b }
                                    GridCageAction.ACTION_SUBTRACT -> kotlin.math.abs(tuple[0] - tuple[1])
                                    GridCageAction.ACTION_DIVIDE -> {
                                        val a = tuple[0]
                                        val b = tuple[1]
                                        if (a == 0 || b == 0) 0
                                        else if (maxOf(a, b) % minOf(a, b) == 0)
                                            maxOf(a, b) / minOf(a, b)
                                        else -1
                                    }
                                    else -> -1
                                }
                            }.filter { it >= 0 }.distinct().sorted()

                            for (target in results) {
                                if (targetOrdinal++ % shardCount != shardIndex) continue
                                val cage = GridCage.createWithCells(
                                    0, grid, action,
                                    grid.getValidCellAt(0, 0), type,
                                )
                                cage.result = target
                                val produced = GridSingleCageCreator(variant, cage)
                                    .possibleCombinations.map { it.toList() }.toSet()
                                val expected = valid.filter {
                                    arithmeticMatches(action, it, target)
                                }.toSet()

                                withClue(
                                    "size=$size digits=$digitSetting type=$type " +
                                        "operation=$action target=$target " +
                                        "actual=${produced.size} expected=${expected.size}",
                                ) {
                                    produced shouldBe expected
                                }
                                checkedTargets++
                            }
                        }
                    }

                    withClue("tested all requested operations and targets size=$size digits=$digitSetting") {
                        (checkedTargets > 0) shouldBe true
                    }
                    println(
                        "CAGE_EXHAUSTIVENESS size=$size digits=$digitSetting " +
                            "cageType=${typeFilter ?: "ALL"} digitFilter=${digitFilter ?: "ALL"} shard=$shardIndex/$shardCount checkedTargetGroups=$checkedTargets " +
                            "independentlyEnumeratedLegalTuples=$validatedLegalTuples",
                    )
                }
            }
        }
    })
