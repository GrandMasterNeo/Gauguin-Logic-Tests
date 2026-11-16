package org.piepmeyer.gauguin.creation

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import org.piepmeyer.gauguin.Randomizer
import org.piepmeyer.gauguin.grid.GridSize
import org.piepmeyer.gauguin.options.GameOptionsVariant
import org.piepmeyer.gauguin.options.GameVariant
import org.piepmeyer.gauguin.options.SingleCageUsage
import kotlin.random.Random

/**
 * Diagnostic only: deliberately always selects the same cell for FIXED_NUMBER
 * singles. A watchdog exception prevents a hung CI worker and measures retries.
 * This is NOT a generator acceptance or puzzle-correctness test.
 */
class CageSingleRetryDiagnosticTest : FunSpec({
    test("fixed singles repeatedly select an occupied cell under adversarial RNG") {
        val randomizer = RepeatingCellDiagnosticRandomizer()
        val variant = GameVariant(
            GridSize(4, 4),
            GameOptionsVariant.createClassic().copy(singleCageUsage = SingleCageUsage.FIXED_NUMBER),
        )
        val exception = shouldThrow<IllegalStateException> {
            GridCreator(variant, randomizer, ShufflerStub()).createRandomizedGridWithCages()
        }
        exception.message shouldBe "DIAGNOSTIC_SINGLE_RETRY_LIMIT"
        randomizer.cellSelections shouldBe 65
        println("CAGE_DIAGNOSTIC fixedSingles=2 repeatedCellSelections=${randomizer.cellSelections} outcome=watchdog_exhausted")
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
