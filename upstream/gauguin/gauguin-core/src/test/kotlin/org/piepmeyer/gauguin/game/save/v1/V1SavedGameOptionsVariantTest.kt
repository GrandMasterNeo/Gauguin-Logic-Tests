package org.piepmeyer.gauguin.game.save.v1

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.assertions.throwables.shouldThrow
import org.piepmeyer.gauguin.game.save.SavedDifficultySetting
import org.piepmeyer.gauguin.options.DifficultySetting
import org.piepmeyer.gauguin.options.DigitSetting
import org.piepmeyer.gauguin.options.GridCageOperation
import org.piepmeyer.gauguin.options.NumeralSystem
import org.piepmeyer.gauguin.options.SingleCageUsage

class V1SavedGameOptionsVariantTest : FunSpec({
    fun saved(
        legacy: SavedDifficultySetting?,
        selected: Set<DifficultySetting> = emptySet(),
    ) = V1SavedGameOptionsVariant(
        showOperators = true,
        cageOperation = GridCageOperation.OPERATIONS_ALL,
        digitSetting = DigitSetting.FIRST_DIGIT_ONE,
        difficultySetting = legacy,
        difficultiesSetting = selected,
        singleCageUsage = SingleCageUsage.FIXED_NUMBER,
        numeralSystem = NumeralSystem.Decimal,
    )

    test("legacy EXTREME save maps to VERY_HARD") {
        saved(SavedDifficultySetting.EXTREME).toSavedGameOptionsVariant()
            .difficultiesSetting shouldBe setOf(DifficultySetting.VERY_HARD)
    }

    test("V1 save without old single difficulty retains selected difficulty set") {
        val selected = setOf(DifficultySetting.HARD, DifficultySetting.VERY_HARD)
        saved(null, selected).toSavedGameOptionsVariant()
            .difficultiesSetting shouldBe selected
    }

    test("V1 save with no difficulty data fails explicitly") {
        shouldThrow<IllegalStateException> {
            saved(null).toSavedGameOptionsVariant()
        }
    }
})
