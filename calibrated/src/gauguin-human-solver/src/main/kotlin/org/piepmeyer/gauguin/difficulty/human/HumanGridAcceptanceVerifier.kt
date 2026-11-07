package org.piepmeyer.gauguin.difficulty.human

import org.piepmeyer.gauguin.creation.GridAcceptanceVerifier
import org.piepmeyer.gauguin.creation.cage.GridSingleCageCreator
import org.piepmeyer.gauguin.difficulty.GameDifficultyRater
import org.piepmeyer.gauguin.grid.Grid
import org.piepmeyer.gauguin.grid.GridCageAction
import org.piepmeyer.gauguin.options.DifficultySetting
import kotlin.math.ceil

/**
 * Experimental difficulty calibration for large square boards only.
 * Rejects boards needing strategies beyond the selected tier, Nishio where
 * not appropriate, reveals, or excessive deduction work per cell.
 *
 * The classical rating remains the app's displayed difficulty category; the
 * human gate refines the selection rather than changing persisted score data.
 */
class HumanGridAcceptanceVerifier : GridAcceptanceVerifier {
    private val rater = GameDifficultyRater()

    override fun accepts(grid: Grid): Boolean {
        val level = rater.difficulty(grid) ?: return true
        val selected = grid.variant.options.difficultiesSetting
        if (selected != DifficultySetting.all() && level !in selected) {
            return false
        }

        val maxWeight = when (level) {
            DifficultySetting.VERY_EASY -> 25
            DifficultySetting.EASY -> 125
            DifficultySetting.MEDIUM -> 210
            DifficultySetting.HARD -> 210
            DifficultySetting.EXTREME -> 350
        }

        // These ceilings are a measured starting calibration. The values are
        // per-cell because an 11x11 grid has 49% more cells than a 9x9 grid.
        val stepsPerCell = when (level) {
            DifficultySetting.VERY_EASY -> 7.5
            DifficultySetting.EASY -> 12.0
            DifficultySetting.MEDIUM -> 16.0
            DifficultySetting.HARD -> 22.0
            DifficultySetting.EXTREME -> 30.0
        }
        val maxSteps = ceil(grid.variant.surfaceArea * stepsPerCell).toInt()

        // High-ambiguity addition-heavy layouts were a known outlier in the
        // operation-mix audit. Limit them at beginner difficulties, without
        // changing the probabilities for all other players.
        if (level == DifficultySetting.VERY_EASY || level == DifficultySetting.EASY) {
            val arithmeticCages = grid.cages.filter { it.action != GridCageAction.ACTION_NONE }
            if (arithmeticCages.isNotEmpty()) {
                val additionShare =
                    arithmeticCages.count { it.action == GridCageAction.ACTION_ADD }.toDouble() /
                        arithmeticCages.size
                val maximumAdditionShare =
                    if (level == DifficultySetting.VERY_EASY) 0.42 else 0.54
                if (additionShare > maximumAdditionShare) {
                    return false
                }
            }
        }

        // Candidate-set distribution is a separate difficulty dimension.
        // Count distinct unordered arithmetic combinations, not permutations.
        // This experimental policy applies only to 11x11 and deliberately
        // permits larger candidate sets in Medium AND Hard.
        if (grid.variant.gridSize.width == 11) {
            val combinationCounts = grid.cages
                .filter { it.action != GridCageAction.ACTION_NONE }
                .map { cage ->
                    GridSingleCageCreator(grid.variant, cage)
                        .possibleCombinations
                        .map { it.sorted() }
                        .toSet()
                        .size
                }
            val twelveOrMore = combinationCounts.count { it >= 12 }
            val twentyOrMore = combinationCounts.count { it >= 20 }
            val acceptable = when (level) {
                DifficultySetting.VERY_EASY -> twelveOrMore <= 5 && twentyOrMore <= 1
                DifficultySetting.EASY -> twelveOrMore <= 6 && twentyOrMore <= 2
                DifficultySetting.MEDIUM -> twelveOrMore in 2..8 && twentyOrMore in 1..4
                DifficultySetting.HARD -> twelveOrMore in 3..10 && twentyOrMore in 1..6
                DifficultySetting.EXTREME -> true
            }
            if (!acceptable) {
                return false
            }
        }

        val workingCopy = grid.copyWithEmptyUserValues()
        val prohibitNishio = level != DifficultySetting.EXTREME
        val solver = HumanSolver(
            workingCopy,
            avoidNishio = prohibitNishio,
            maxStrategyWeight = maxWeight,
            maxSuccessfulSteps = maxSteps,
        )
        solver.prepareGrid()
        val result = solver.solveAndCalculateDifficulty(avoidReveal = true)

        return result.success &&
            (!prohibitNishio || !result.usedNishio) &&
            solver.successfulSteps <= maxSteps
    }
}
