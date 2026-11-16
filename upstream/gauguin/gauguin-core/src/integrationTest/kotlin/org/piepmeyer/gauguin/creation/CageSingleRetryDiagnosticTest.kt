package org.piepmeyer.gauguin.creation

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import org.piepmeyer.gauguin.Randomizer
import org.piepmeyer.gauguin.grid.GridSize
import org.piepmeyer.gauguin.options.GameOptionsVariant
import org.piepmeyer.gauguin.options.GameVariant
import org.piepmeyer.gauguin.options.SingleCageUsage
import kotlin.random.Random

/**
 * Regression: fixed singles must terminate even when the randomizer always
 * chooses index zero. This does not certify puzzle uniqueness or difficulty.
 */
class CageSingleRetryDiagnosticTest : FunSpec({
    test("fixed singles select distinct eligible cells under adversarial RNG") {
        val randomizer = RepeatingCellDiagnosticRandomizer()
        val variant = GameVariant(
            GridSize(4, 4),
            GameOptionsVariant.createClassic().copy(singleCageUsage = SingleCageUsage.FIXED_NUMBER),
        )
        val grid = GridCreator(variant, randomizer, ShufflerStub()).createRandomizedGridWithCages()
        grid.cages.count { it.cells.size == 1 } shouldBe 2
        randomizer.cellSelections shouldBe 2
        println("CAGE_REGRESSION fixedSingles=2 selections=${randomizer.cellSelections} outcome=terminated")
    }
})

private class RepeatingCellDiagnosticRandomizer : Randomizer {
    val random = Random(0)
    var cellSelections = 0
        private set

    override fun random(): Random = random
    override fun discard() {}
    override fun nextDouble(): Double = 0.0
    override fun nextInt(maximumNumber: Int): Int {
        cellSelections++
        if (cellSelections > 64) error("DIAGNOSTIC_SINGLE_RETRY_LIMIT")
        return 0
    }
}
