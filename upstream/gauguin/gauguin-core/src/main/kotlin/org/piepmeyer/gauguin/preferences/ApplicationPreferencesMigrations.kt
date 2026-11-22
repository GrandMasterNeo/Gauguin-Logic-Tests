package org.piepmeyer.gauguin.preferences

import org.piepmeyer.gauguin.options.DifficultySetting

class ApplicationPreferencesMigrations(
    private val applicationPreferences: ApplicationPreferences,
) {
    fun migrateDifficultySettingIfNecessary() {
        val existingDifficulties = applicationPreferences.getStringSet("difficulties", null)
        if (existingDifficulties != null) {
            // Normalize existing multi-select preferences without dropping selections.
            if ("EXTREME" in existingDifficulties) {
                applicationPreferences.difficultiesSetting = existingDifficulties
                    .filterNotNull()
                    .map { DifficultySetting.valueOf(if (it == "EXTREME") "VERY_HARD" else it) }
                    .toSet()
            }
            return
        }

        val oldDifficultyValue = applicationPreferences.getString("difficulty", null)
        /*
         * Possible values:
         * ANY
         * VERY_EASY
         * EASY
         * MEDIUM
         * HARD
         * EXTREME (legacy)
         * VERY_HARD
         */

        if (oldDifficultyValue != null) {
            applicationPreferences.difficultiesSetting =
                if (oldDifficultyValue == "ANY") {
                    DifficultySetting.all()
                } else {
                    setOf(DifficultySetting.valueOf(if (oldDifficultyValue == "EXTREME") "VERY_HARD" else oldDifficultyValue))
                }
        }
    }

    fun migrateThemeToNightModeIfNecessary() {
        if (applicationPreferences.getString("nightMode", null) != null) {
            return
        }

        val oldThemeValue = applicationPreferences.getString("theme", null)
        /*
         * Possible values:
         * LIGHT
         * DARK
         * SYSTEM_DEFAULT
         * DYNAMIC_COLORS
         */

        val (newThemeValue, newNightModeValue) = migrateToNewThemeNightModesValues(oldThemeValue)

        applicationPreferences.theme = newThemeValue
        applicationPreferences.nightMode = newNightModeValue
    }

    private fun migrateToNewThemeNightModesValues(oldThemeValue: String?): Pair<Theme, NightMode> {
        val newThemeValue = if (oldThemeValue == "DYNAMIC_COLORS") Theme.DYNAMIC_COLORS else Theme.GAUGUIN

        val newNightModeValue =
            when (oldThemeValue) {
                "LIGHT", "DYNAMIC_COLORS" -> NightMode.LIGHT
                "DARK" -> NightMode.DARK
                "SYSTEM_DEFAULT" -> NightMode.SYSTEM_DEFAULT
                else -> NightMode.DARK
            }

        return Pair(newThemeValue, newNightModeValue)
    }
}
