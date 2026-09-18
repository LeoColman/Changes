// SPDX-License-Identifier: AGPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Leonardo Colman Lopes

package br.com.colman.changes.core.model

import kotlin.uuid.Uuid

public enum class BodyChangeCategory { SKIN, HAIR, BODY, SEXUAL_REPRODUCTIVE, VOICE, EMOTIONAL, PAIN, OTHER }

public enum class BodyMeasurementUnit { CM, IN, HZ, KG, LB, PERCENT }

/** Escala descritiva de intensidade (Seção 6): nenhuma, leve, moderada, acentuada, completa. */
public enum class Intensity(public val level: Int) {
    NONE(0),
    MILD(1),
    MODERATE(2),
    MARKED(3),
    COMPLETE(4),
    ;

    public companion object {
        public fun fromLevel(level: Int): Intensity? = entries.firstOrNull { it.level == level }
    }
}

public data class BodyChangeType(
    val id: Uuid,
    val code: String,
    /** Builtin: chave resolvida pelo BodyVocabularyResolver. Customizado: `null`. */
    val labelKey: String?,
    /** Customizado: texto da pessoa. Builtin: `null`. */
    val customLabel: String?,
    val category: BodyChangeCategory,
    /** `null` = a fonte não informa. */
    val isReversible: Boolean?,
    val supportsMeasurement: Boolean,
    val measurementUnit: BodyMeasurementUnit?,
    val isBuiltin: Boolean,
    val isHidden: Boolean,
)

public data class BodyChangeEntry(
    val id: Uuid,
    val changeTypeId: Uuid,
    val observedAt: RecordedTime,
    val intensity: Intensity?,
    val measurementValue: Double?,
    val measurementUnit: BodyMeasurementUnit?,
    val notes: String?,
)

public enum class MediaOwnerType { BODY_CHANGE_ENTRY, MEASUREMENT, LAB_RESULT, CALENDAR_EVENT, NOTE }

public data class MediaAttachment(
    val id: Uuid,
    val ownerType: MediaOwnerType,
    val ownerId: Uuid,
    /** '<uuid>.<ext>', relativo à raiz de mídia. Validado por [MediaPaths]. */
    val relativePath: String,
    val mimeType: String,
    val capturedAt: RecordedTime?,
    val checksumSha256: String,
    val caption: String?,
) {
    /** Gravação de voz (ADR 0013) em vez de foto: nunca é decodificada como imagem. */
    public val isAudio: Boolean get() = mimeType.lowercase().startsWith("audio/")
}

/** Regras de nome de arquivo de mídia. Nada de subdiretórios nem `..`: protege contra zip slip no import. */
public object MediaPaths {
    /** Formato da gravação de voz (ADR 0013): AAC em MPEG-4. */
    public const val VOICE_MIME_TYPE: String = "audio/mp4"

    private val FINAL =
        Regex("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.(jpg|jpeg|png|webp|heic|heif|m4a)$")

    public val EXTENSIONS: Set<String> = setOf("jpg", "jpeg", "png", "webp", "heic", "heif", "m4a")

    public fun isValid(relativePath: String): Boolean = FINAL.matches(relativePath)

    public fun forNew(id: Uuid, extension: String): String = "$id.${extension.lowercase()}"

    public fun extensionForMimeType(mimeType: String): String = when (mimeType.lowercase()) {
        "image/png" -> "png"
        "image/webp" -> "webp"
        "image/heic" -> "heic"
        "image/heif" -> "heif"
        VOICE_MIME_TYPE -> "m4a"
        else -> "jpg"
    }
}
