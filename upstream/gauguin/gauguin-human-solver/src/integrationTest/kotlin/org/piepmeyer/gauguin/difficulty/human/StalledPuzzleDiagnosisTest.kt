package org.piepmeyer.gauguin.difficulty.human

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.piepmeyer.gauguin.creation.RandomCageGridCalculator
import org.piepmeyer.gauguin.creation.RandomPossibleDigitsShuffler
import org.piepmeyer.gauguin.creation.SeedRandomizerMock
import org.piepmeyer.gauguin.difficulty.GameDifficultyRater
import org.piepmeyer.gauguin.difficulty.GameDifficultyRatingService
import org.piepmeyer.gauguin.grid.Grid
import org.piepmeyer.gauguin.grid.GridSize
import org.piepmeyer.gauguin.options.DifficultySetting
import org.piepmeyer.gauguin.options.GameOptionsVariant
import org.piepmeyer.gauguin.options.GameVariant
import kotlin.system.measureNanoTime

/**
 * Reproduces the exact four cases reported by AdvancedDifficultyStrategySafetyTest.
 *
 * Unlike that hand-driven strategy tracer, this uses the real production
 * HumanSolver API to distinguish a genuine solver stall from a discrepancy in
 * the tracer. No hints, hidden answers, or speculative solutions are copied
 * into the grid. The stored solution is used for assertions only.
 *
 * The next two uniquely generated, classically rated boards are also checked
 * for no-reveal human solvability to estimate the costs/benefits of a possible
 * generation-time human-solvability gate. This is measurement only; production
 * generation remains unchanged.
 */
class StalledPuzzleDiagnosisTest :
    FunSpec({
        data class Case(val size: Int, val level: DifficultySetting, val seed: Int)
        val cases = listOf(
            Case(9, DifficultySetting.EXTREME, 1),
            Case(10, DifficultySetting.HARD, 0),
            Case(10, DifficultySetting.EXTREME, 1),
            Case(11, DifficultySetting.EXTREME, 0),
        )
        val requestedSize = System.getenv("GAUGUIN_STALL_SIZE")?.toIntOrNull() ?: 9
        val retries = System.getenv("GAUGUIN_STALL_RETRIES")?.toIntOrNull() ?: 2
        require(requestedSize in setOf(9, 10, 11))
        require(retries in 0..4)

        for (case in cases.filter { it.size == requestedSize }) {
            test("reproduce and examine ${case.size}x${case.size} ${case.level} seed ${case.seed}") {
                startKoin {
                    modules(module {
                        single { GameDifficultyRatingService() }
                    })
                }
                try {
                    val variant = GameVariant(
                        GridSize(case.size, case.size),
                        GameOptionsVariant.createClassic().copy(
                            difficultiesSetting = setOf(case.level),
                        ),
                    )
                    val randomizer = SeedRandomizerMock(case.seed)
                    val generator = RandomCageGridCalculator(
                        variant, randomizer, RandomPossibleDigitsShuffler(randomizer.random),
                    )
                    val rater = GameDifficultyRater()

                    fun evaluate(grid: Grid, tag: String): Boolean {
                        withClue("rating for $tag") {
                            rater.difficulty(grid) shouldBe case.level
                        }

                        val noTrialGrid = grid.copyWithEmptyUserValues()
                        val noTrialSolver = HumanSolver(noTrialGrid, validate = true, avoidNishio = true)
                        noTrialSolver.prepareGrid()
                        val noTrialTime = measureNanoTime {
                            // run below separately so both results can be logged
                        }
                        val withoutTrial = noTrialSolver.solveAndCalculateDifficulty(avoidReveal = true)

                        val fullGrid = grid.copyWithEmptyUserValues()
                        val solver = HumanSolver(fullGrid, validate = true)
                        solver.prepareGrid()
                        var withTrial: HumanSolverResult? = null
                        val solverNs = measureNanoTime {
                            withTrial = solver.solveAndCalculateDifficulty(avoidReveal = true)
                        }
                        val result = withTrial!!

                        for (work in listOf(noTrialGrid, fullGrid)) {
                            withClue("no incorrect placements for $tag") {
                                work.cells.count { it.isUserValueSet && it.userValue != it.value } shouldBe 0
                            }
                            withClue("preserve every true candidate for $tag") {
                                work.cells.count { !it.isUserValueSet && it.value !in it.possibles } shouldBe 0
                            }
                        }

                        val unsolvedCells = fullGrid.cells.filter { !it.isUserValueSet }
                        val candidateHistogram = unsolvedCells
                            .groupingBy { it.possibles.size }.eachCount().toSortedMap()
                        val cache = HumanSolverCacheImpl.createValidatedCache(fullGrid)
                        val emptyCages = fullGrid.cages.count { cache.possibles(it).isEmpty() }
                        val cageForcedCells = unsolvedCells.count { cell ->
                            val cage = cell.cage()
                            val index = cage.cells.indexOf(cell)
                            cache.possibles(cage).map { it[index] }.distinct().size == 1
                        }
                        val rowColSingles = fullGrid.variant.possibleDigits.sumOf { digit ->
                            val rowSingles = (0 until case.size).count { row ->
                                fullGrid.cells.none { it.row == row && it.userValue == digit } &&
                                    fullGrid.cells.count {
                                        it.row == row && !it.isUserValueSet && digit in it.possibles
                                    } == 1
                            }
                            val colSingles = (0 until case.size).count { col ->
                                fullGrid.cells.none { it.column == col && it.userValue == digit } &&
                                    fullGrid.cells.count {
                                        it.column == col && !it.isUserValueSet && digit in it.possibles
                                    } == 1
                            }
                            rowSingles + colSingles
                        }
                        val candidatesNotInCageCombinations = unsolvedCells.sumOf { cell ->
                            val cage = cell.cage()
                            val index = cage.cells.indexOf(cell)
                            val allowed = cache.possibles(cage).map { it[index] }.toSet()
                            cell.possibles.count { it !in allowed }
                        }
                        val minCageOptions = fullGrid.cages.minOfOrNull { cache.possibles(it).size } ?: 0

                        println(
                            "STALL_REPRO tag=$tag board=${case.size} level=${case.level} seed=${case.seed} " +
                                "solvedWithoutTrial=${withoutTrial.success} " +
                                "filledWithoutTrial=${noTrialGrid.cells.count { it.isUserValueSet }} " +
                                "solvedWithTrial=${result.success} filled=${fullGrid.cells.count { it.isUserValueSet }} " +
                                "usedTrial=${result.usedNishio} difficulty=${result.difficulty} " +
                                "solverMs=${solverNs / 1_000_000} " +
                                "candidatesTotal=${unsolvedCells.sumOf { it.possibles.size }} " +
                                "histogram=$candidateHistogram emptyCages=$emptyCages " +
                                "forcedByCage=$cageForcedCells rowColumnSingles=$rowColSingles " +
                                "unsupportedCandidates=$candidatesNotInCageCombinations " +
                                "minCageCombinations=$minCageOptions",
                        )
                        // A contradictory state would invalidate the usefulness
                        // of any subsequent "no progress" analysis.
                        withClue("no empty cage combinations in $tag") {
                            emptyCages shouldBe 0
                        }
                        withClue("no empty cell candidate sets in $tag") {
                            unsolvedCells.count { it.possibles.isEmpty() } shouldBe 0
                        }
                        return result.success
                    }

                    var firstGrid: Grid? = null
                    val initialGenerateNs = measureNanoTime {
                        firstGrid = generator.calculate()
                    }
                    val initialSolved = evaluate(firstGrid!!, "original")
                    println(
                        "STALL_GENERATION_INITIAL board=${case.size} level=${case.level} " +
                            "seed=${case.seed} generateMs=${initialGenerateNs / 1_000_000} " +
                            "solved=$initialSolved",
                    )

                    if (!initialSolved) {
                        var foundAt: Int? = null
                        for (retry in 1..retries) {
                            var nextGrid: Grid? = null
                            val nextGenerateNs = measureNanoTime {
                                nextGrid = generator.calculate()
                            }
                            val solved = evaluate(nextGrid!!, "retry$retry")
                            println(
                                "STALL_GATE_TRIAL board=${case.size} level=${case.level} " +
                                    "seed=${case.seed} retry=$retry " +
                                    "generateMs=${nextGenerateNs / 1_000_000} solved=$solved",
                            )
                            if (solved) {
                                foundAt = retry
                                break
                            }
                        }
                        println(
                            "STALL_GATE_SUMMARY board=${case.size} level=${case.level} " +
                                "seed=${case.seed} firstSolvableRetry=${foundAt ?: "none"} " +
                                "retriesAllowed=$retries",
                        )
                    }
                } finally {
                    stopKoin()
                }
            }
        }
    })
