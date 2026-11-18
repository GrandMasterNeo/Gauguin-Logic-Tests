package org.piepmeyer.gauguin.options

@kotlinx.serialization.Serializable(with = DifficultySettingSerializer::class)
enum class DifficultySetting {
    VERY_EASY,
    EASY,
    MEDIUM,
    HARD,
    VERY_HARD, ;

    companion object {
        @Deprecated("Use VERY_HARD; retained temporarily for older source references")
        val EXTREME: DifficultySetting = VERY_HARD

        fun all(): Set<DifficultySetting> = entries.toSet()

        fun isApplicableToSingleSelection(difficultiesSetting: Set<DifficultySetting>): Boolean =
            difficultiesSetting == all() || difficultiesSetting.size == 1
    }
}

/** Decode historical save values without writing the legacy name to new saves. */
object DifficultySettingSerializer : kotlinx.serialization.KSerializer<DifficultySetting> {
    override val descriptor: kotlinx.serialization.descriptors.SerialDescriptor =
        kotlinx.serialization.descriptors.PrimitiveSerialDescriptor("DifficultySetting", kotlinx.serialization.descriptors.PrimitiveKind.STRING)

    override fun serialize(encoder: kotlinx.serialization.encoding.Encoder, value: DifficultySetting) {
        encoder.encodeString(value.name)
    }

    override fun deserialize(decoder: kotlinx.serialization.encoding.Decoder): DifficultySetting {
        val saved = decoder.decodeString()
        return DifficultySetting.valueOf(if (saved == "EXTREME") "VERY_HARD" else saved)
    }
}
