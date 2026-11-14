package org.piepmeyer.gauguin.difficulty.human

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
import org.piepmeyer.gauguin.grid.GridSize
import org.piepmeyer.gauguin.options.DifficultySetting
import org.piepmeyer.gauguin.options.GameOptionsVariant
import org.piepmeyer.gauguin.options.GameVariant
import kotlin.system.measureNanoTime

/**
 * Diagnostic only: measures the total generation + non-speculative human-solver
 * cost of accepting a beginner puzzle, with at most two retries per seed.
 * This is not a production generation filter.
 */
class BeginnerGateBenchmarkTest : FunSpec({
    val seeds = System.getenv("GAUGUIN_GATE_SEEDS")?.toIntOrNull() ?: 50
    val retries = System.getenv("GAUGUIN_GATE_RETRIES")?.toIntOrNull() ?: 2
    require(seeds in 1..100)
    require(retries in 0..4)
    test("10x10 Easy human gate bounded acceptance benchmark") {
        startKoin { modules(module { single { GameDifficultyRatingService() } }) }
        try {
            val variant = GameVariant(
                GridSize(10, 10),
                GameOptionsVariant.createClassic().copy(
                    difficultiesSetting = setOf(DifficultySetting.EASY),
                ),
            )
            val rater = GameDifficultyRater()
            var accepted = 0
            var initialAccepted = 0
            var attempts = 0
            var generationNs = 0L
            var solverNs = 0L
            var rejectedAttempts = 0
            for (seed in 0 until seeds) {
                val randomizer = SeedRandomizerMock(seed)
                val generator = RandomCageGridCalculator(
                    variant, randomizer, RandomPossibleDigitsShuffler(randomizer.random),
                )
                var acceptedAt = -1
                var seedGenerationNs = 0L
                var seedSolverNs = 0L
                for (attempt in 0..retries) {
                    var grid: org.piepmeyer.gauguin.grid.Grid? = null
                    val generateNs = measureNanoTime { grid = generator.calculate() }
                    seedGenerationNs += generateNs
                    generationNs += generateNs
                    attempts++
                    rater.difficulty(grid!!) shouldBe DifficultySetting.EASY
                    val working = grid!!.copyWithEmptyUserValues()
                    val solver = HumanSolver(working, validate = true, avoidNishio = true)
                    solver.prepareGrid()
                    var solved: HumanSolverResult? = null
                    val solveNs = measureNanoTime {
                        solved = solver.solveAndCalculateDifficulty(avoidReveal = true)
                    }
                    solverNs += solveNs
                    seedSolverNs += solveNs
                    working.cells.count {
                        it.isUserValueSet && it.userValue != it.value
                    } shouldBe 0
                    working.cells.count {
                        !it.isUserValueSet && it.value !in it.possibles
                    } shouldBe 0
                    if (solved!!.success) {
                        working.isSolved() shouldBe true
                        acceptedAt = attempt
                        accepted++
                        if (attempt == 0) initialAccepted++
                        break
                    }
                    rejectedAttempts++
                }
                println(
                    "GATE_CASE size=10 level=EASY seed=$seed accepted=${acceptedAt >= 0} " +
                        "acceptedAt=$acceptedAt generationMs=${seedGenerationNs / 1_000_000} " +
                        "solverMs=${seedSolverNs / 1_000_000}"
                )
            }
            println(
                "GATE_SUMMARY size=10 level=EASY seeds=$seeds retries=$retries " +
                    "initialAccepted=$initialAccepted accepted=$accepted " +
                    "attempts=$attempts rejectedAttempts=$rejectedAttempts " +
                    "generationMs=${generationNs / 1_000_000} solverMs=${solverNs / 1_000_000} " +
                    "totalMs=${(generationNs + solverNs) / 1_000_000}"
            )
        } finally {
            stopKoin()
        }
    }
})
