package org.piepmeyer.gauguin.difficulty.human

import io.kotest.core.spec.style.FunSpec
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

/** Production generator, one size/difficulty/seed per CI job. No reveal or Nishio. */
class HumanProductionCompatibilityMatrixTest : FunSpec({
    val size = System.getenv("GAUGUIN_HUMAN_SIZE")?.toIntOrNull() ?: 9
    val level = DifficultySetting.valueOf(System.getenv("GAUGUIN_HUMAN_DIFFICULTY") ?: "VERY_EASY")
    val seed = System.getenv("GAUGUIN_HUMAN_SEED")?.toIntOrNull() ?: 0
    require(size in 3..11)
    require(level in setOf(DifficultySetting.VERY_EASY, DifficultySetting.EASY, DifficultySetting.MEDIUM, DifficultySetting.HARD, DifficultySetting.VERY_HARD))

    test("production human solver compatibility size=$size level=$level seed=$seed") {
        val variant = GameVariant(
            GridSize(size, size),
            GameOptionsVariant.createClassic().copy(difficultiesSetting = setOf(level)),
        )
        val randomizer = SeedRandomizerMock(seed)
        startKoin { modules(module { single { GameDifficultyRatingService() } }) }
        try {
            val grid = RandomCageGridCalculator(
                variant, randomizer, RandomPossibleDigitsShuffler(randomizer.random),
            ).calculate()
            val actual = GameDifficultyRater().difficulty(grid)
            check(actual == level) { "Difficulty mismatch expected=$level actual=$actual" }
            val work = grid.copyWithEmptyUserValues()
            val solver = HumanSolver(work, validate = true, avoidNishio = true)
            solver.prepareGrid()
            val result = solver.solveAndCalculateDifficulty(avoidReveal = true)
            val incorrect = work.cells.count { it.isUserValueSet && it.userValue != it.value }
            val lost = work.cells.count { !it.isUserValueSet && it.value !in it.possibles }
            val solved = work.isSolved()
            println("HUMAN_COMPAT size=$size difficulty=$level seed=$seed solved=$solved success=${result.success} usedNishio=${result.usedNishio} incorrect=$incorrect lostTrueCandidates=$lost filled=${work.cells.count { it.isUserValueSet }}/${work.cells.size} score=${result.difficulty}")
            check(incorrect == 0) { "Incorrect placement: $incorrect" }
            check(lost == 0) { "True candidates removed: $lost" }
            check(result.success == solved) { "Reported success disagrees with solved grid" }
            check(!result.usedNishio) { "Nishio used despite avoidance" }
            check(solved) { "Human solver stalled without guessing/reveal" }
        } finally {
            stopKoin()
        }
    }
})
