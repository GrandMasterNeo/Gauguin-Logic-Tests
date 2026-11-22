package org.piepmeyer.gauguin.difficulty

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.mockk
import org.piepmeyer.gauguin.options.DifficultySetting

class GameDifficultyRatingBoundaryTest : FunSpec({
    test("equal medium and hard thresholds classify the boundary as hard") {
        val rating = GameDifficultyRating(
            variant = mockk<GameDifficultyVariant>(),
            thresholdEasy = 0.69,
            thresholdMedium = 2.48,
            thresholdHard = 2.48,
            thresholdExtreme = 2.77,
        )
        rating.getDifficulty(2.47) shouldBe DifficultySetting.EASY
        rating.getDifficulty(2.48) shouldBe DifficultySetting.HARD
        rating.getDifficulty(2.76) shouldBe DifficultySetting.HARD
        rating.getDifficulty(2.77) shouldBe DifficultySetting.VERY_HARD
    }
})
