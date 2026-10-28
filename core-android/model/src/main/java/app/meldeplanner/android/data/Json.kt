@file:Suppress("EnumEntryName")

package app.meldeplanner.android.data

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import java.time.Instant
import java.time.temporal.ChronoUnit

/** ISO-8601 in whole seconds ("2026-10-21T08:00:00Z"), like Swift's `.iso8601` strategy. */
object InstantSerializer : KSerializer<Instant> {
    override val descriptor = PrimitiveSerialDescriptor("Instant", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: Instant) =
        encoder.encodeString(value.truncatedTo(ChronoUnit.SECONDS).toString())
    override fun deserialize(decoder: Decoder): Instant = Instant.parse(decoder.decodeString())
}

/** The JSON format of data.json: missing fields take their defaults, unknown ones are kept out. */
val MeldoJson = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    coerceInputValues = true
    encodeDefaults = true
}

/** Accent color. Meldo orange is the default; light/dark is [AppearanceChoice]. */
@Serializable
enum class AppTheme { orange, indigo, green }

/** Light, dark or following the system. */
@Serializable
enum class AppearanceChoice { system, light, dark }

/** Subject palette, named like the iOS system colors it mirrors. */
@Serializable
enum class SubjectColor { blue, indigo, purple, pink, red, orange, yellow, green, mint, teal, cyan, brown, gray }
