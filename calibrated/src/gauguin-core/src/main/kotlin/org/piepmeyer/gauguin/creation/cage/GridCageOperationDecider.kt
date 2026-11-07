package org.piepmeyer.gauguin.creation.cage

import org.piepmeyer.gauguin.Randomizer
import org.piepmeyer.gauguin.grid.GridCageAction
import org.piepmeyer.gauguin.grid.GridCell
import org.piepmeyer.gauguin.options.GridCageOperation
import kotlin.math.max
import kotlin.math.min

class GridCageOperationDecider(
    private val randomizer: Randomizer,
    private val cells: List<GridCell>,
    private val operationSet: GridCageOperation,
    private val balancedLargeGrid: Boolean = false,
) {
    fun decideOperation(): GridCageAction {
        if (cells.size == 1) {
            return GridCageAction.ACTION_NONE
        }
        if (operationSet == GridCageOperation.OPERATIONS_MULT) {
            return GridCageAction.ACTION_MULTIPLY
        }
        if (balancedLargeGrid && operationSet == GridCageOperation.OPERATIONS_ALL) {
            return decideLargeGridOperation()
        }
        return when (getCalculationDecision()) {
            CageCalculationDecision.ADDITION_AND_SUBTRACTION ->
                decideBetweenAdditionAndSubtraction()
            CageCalculationDecision.MULTIPLICATION_AND_DIVISION ->
                decideBetweenMultiplicationAndDivision()
        }
    }

    /**
     * Experimental 11x11 operation distribution. Target relative weights:
     * multiply 65, add 17, subtract 8, divide 8 (sum=98, normalized).
     *
     * Division can only be selected for divisible two-cell cages; probabilities
     * are conditioned on cage size and arithmetic validity, not fixed quotas.
     * A separate human-difficulty verifier still decides puzzle acceptance.
     */
    private fun decideLargeGridOperation(): GridCageAction {
        if (cells.size > 2) {
            return if (randomizer.nextDouble() < 0.20) {
                GridCageAction.ACTION_ADD
            } else {
                GridCageAction.ACTION_MULTIPLY
            }
        }

        // Divisibility is checked before selecting the operation.
        if (canHandleDivide() && randomizer.nextDouble() < 0.64) {
            return GridCageAction.ACTION_DIVIDE
        }
        val p = randomizer.nextDouble()
        return when {
            p < 0.27 -> GridCageAction.ACTION_SUBTRACT
            p < 0.42 -> GridCageAction.ACTION_ADD
            else -> GridCageAction.ACTION_MULTIPLY
        }
    }

    private fun decideBetweenAdditionAndSubtraction(): GridCageAction {
        if (cells.size > 2 || operationSet == GridCageOperation.OPERATIONS_ADD_MULT) {
            return GridCageAction.ACTION_ADD
        }
        val randomValue = randomizer.nextDouble()
        return if (randomValue >= 0.25) {
            GridCageAction.ACTION_SUBTRACT
        } else {
            GridCageAction.ACTION_ADD
        }
    }

    private fun decideBetweenMultiplicationAndDivision(): GridCageAction {
        if (cells.size > 2 || operationSet == GridCageOperation.OPERATIONS_ADD_MULT) {
            return GridCageAction.ACTION_MULTIPLY
        }
        val randomValue = randomizer.nextDouble()
        // For 9–11 square boards, use division whenever available slightly more often.
        val divisionThreshold = if (balancedLargeGrid && operationSet == GridCageOperation.OPERATIONS_ALL) 0.18 else 0.25
        return if (randomValue >= divisionThreshold && canHandleDivide()) {
            GridCageAction.ACTION_DIVIDE
        } else {
            GridCageAction.ACTION_MULTIPLY
        }
    }

    private fun canHandleDivide(): Boolean {
        val cell1Value = cells[0].value
        val cell2Value = cells[1].value

        val higher = max(cell1Value, cell2Value)
        val lower = min(cell1Value, cell2Value)

        if (lower == 0 && higher == 0) {
            return false
        }

        return if (lower == 0 || higher == 0) {
            true
        } else {
            higher % lower == 0
        }
    }

    private fun getCalculationDecision(): CageCalculationDecision {
        if (operationSet == GridCageOperation.OPERATIONS_ADD_SUB) {
            return CageCalculationDecision.ADDITION_AND_SUBTRACTION
        }
        // Do not force a 25% ratio: subtraction/division require two-cell
        // cages, while larger cages can only use addition or multiplication.
        // Slightly favor multiply on large cages; preserve sufficient two-cell
        // add/sub cages to keep the four operations represented.
        val additionFamilyChance =
            if (balancedLargeGrid && operationSet == GridCageOperation.OPERATIONS_ALL) {
                if (cells.size > 2) 0.37 else 0.54
            } else {
                0.50
            }
        val randomValue = randomizer.nextDouble()
        return if (randomValue >= additionFamilyChance) {
            CageCalculationDecision.MULTIPLICATION_AND_DIVISION
        } else {
            CageCalculationDecision.ADDITION_AND_SUBTRACTION
        }
    }
}
