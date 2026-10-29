package app.meldeplanner.android.data

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.nullable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import java.time.Instant

/**
 * When each synced item last changed on any device, and which were deleted.
 * Keys are "<collection>:<id>" for list items and the field name for single
 * values ("untisTimetable"); each setting is "preferences.<jsonKey>". [LocalStore.update] keeps it current
 * for every write, from the app or a widget. Port of meldo-ios/Shared/SyncLedger.swift;
 * same JSON shape, so an iPhone and an Android phone share one Drive backup.
 */
@Serializable
data class SyncLedger(
    var stamps: MutableMap<String, @Serializable(InstantSerializer::class) Instant> = mutableMapOf(),
    var tombstones: MutableMap<String, @Serializable(InstantSerializer::class) Instant> = mutableMapOf(),
)

/**
 * Item-by-item merge of two devices' data: for each item the newer change wins,
 * and a deletion wins over an older edit. Pure, so it is tested without Google Drive.
 */
object SyncMerge {
    /** Swift's `Date.distantPast`: "never stamped". */
    private val distantPast: Instant = Instant.MIN

    /**
     * Stamps what differs between [old] and [new] (both written by this device) at [clock].
     * A change is always stamped after every stamp this device has seen (a hybrid logical
     * clock), so a device whose clock runs ahead can't make its edits win over later ones.
     */
    fun record(old: AppData, new: AppData, clock: Instant) {
        val ledger = SyncLedger(new.sync.stamps.toMutableMap(), new.sync.tombstones.toMutableMap())
        val seen = maxOf(ledger.stamps.values.maxOrNull() ?: distantPast, ledger.tombstones.values.maxOrNull() ?: distantPast)
        // One second, not less: stamps are saved in whole seconds, so a smaller step would be lost
        // and the new edit would tie with the stamp it must beat.
        val now = maxOf(clock, seen.plusSeconds(1))
        // A refresh only moving "last updated" is no edit: it must not beat a disconnect
        // (tombstone) made on another device in the meantime.
        val before = withoutSyncTimes(old)
        val after = withoutSyncTimes(new)
        for (field in fields) {
            val was = field.items(before)
            val isNow = field.items(after)
            for (key in was.keys + isNow.keys) {
                val a = was[key]
                val b = isNow[key]
                if (a == b) continue
                if (b == null) {
                    ledger.stamps.remove(key)
                    ledger.tombstones[key] = now
                } else {
                    ledger.stamps[key] = now
                    ledger.tombstones.remove(key)
                }
            }
        }
        new.sync = ledger
    }

    /** [local] with [remote] merged in. Device settings (app icon, Material You) stay local. */
    fun merge(local: AppData, remote: AppData): AppData {
        val result = copy(local)
        val ledger = SyncLedger()
        ledger.tombstones = local.sync.tombstones.toMutableMap()
        for ((key, date) in remote.sync.tombstones) {
            ledger.tombstones[key] = ledger.tombstones[key]?.let { maxOf(it, date) } ?: date
        }
        for (field in fields) {
            val mine = field.items(local)
            val theirs = field.items(remote)
            val chosen = mutableMapOf<String, String>()
            for (key in mine.keys + theirs.keys) {
                val localStamp = stamp(key, local.sync)
                val remoteStamp = stamp(key, remote.sync)
                val stamp = maxOf(localStamp, remoteStamp)
                // A deletion newer than the last edit removes the item on both sides.
                val deleted = ledger.tombstones[key]
                if (deleted != null && deleted >= stamp) continue
                val m = mine[key]
                val t = theirs[key]
                chosen[key] = when {
                    m != null && t != null -> if (remoteStamp > localStamp) t else m
                    m != null -> m
                    t != null -> t
                    else -> continue
                }
                if (stamp > distantPast) ledger.stamps[key] = stamp
                ledger.tombstones.remove(key)
            }
            field.apply(chosen, result)
        }
        result.dismissedImports = (local.dismissedImports + remote.dismissedImports).toMutableSet()
        result.preferences.appIcon = local.preferences.appIcon
        result.preferences.dynamicColor = local.preferences.dynamicColor
        result.sync = ledger
        return result
    }

    /**
     * When [key] last changed. Settings stamped before they were merged one by one
     * carry the stamp of the whole "preferences" block (same rule as iOS).
     */
    private fun stamp(key: String, ledger: SyncLedger): Instant =
        ledger.stamps[key] ?: (if (key.startsWith(PREFERENCES_PREFIX)) ledger.stamps["preferences"] else null) ?: distantPast

    private const val PREFERENCES_PREFIX = "preferences."

    private fun copy(data: AppData): AppData =
        MeldoJson.decodeFromString(AppData.serializer(), MeldoJson.encodeToString(AppData.serializer(), data))

    private fun withoutSyncTimes(data: AppData): AppData = data.copy(
        untis = data.untis?.copy(lastSync = null),
        teams = data.teams?.copy(lastSync = null),
    )

    // region The synced fields

    /** One synced part of [AppData], as encoded items by key. */
    private class Field(
        val items: (AppData) -> Map<String, String>,
        val apply: (Map<String, String>, AppData) -> Unit,
    )

    private fun <T : Any> list(
        name: String,
        serializer: KSerializer<T>,
        id: (T) -> String,
        get: (AppData) -> MutableList<T>,
        set: (AppData, MutableList<T>) -> Unit,
    ) = Field(
        items = { data ->
            val out = LinkedHashMap<String, String>()
            for (item in get(data)) out.putIfAbsent("$name:${id(item).uppercase()}", MeldoJson.encodeToString(serializer, item))
            out
        },
        apply = { chosen, data ->
            // Keep the local order; items new from the other device go at the end.
            val decoded = chosen.mapNotNull { (key, json) ->
                runCatching { MeldoJson.decodeFromString(serializer, json) }.getOrNull()?.let { key to it }
            }.toMap()
            val seen = mutableSetOf<String>()
            val merged = get(data).mapNotNull { item ->
                val key = "$name:${id(item).uppercase()}"
                seen.add(key)
                decoded[key]
            }.toMutableList()
            merged += decoded.filterKeys { it !in seen }.toSortedMap().values
            set(data, merged)
        },
    )

    private fun <T : Any> optional(
        name: String,
        serializer: KSerializer<T>,
        get: (AppData) -> T?,
        set: (AppData, T?) -> Unit,
    ) = Field(
        items = { data -> get(data)?.let { mapOf(name to MeldoJson.encodeToString(serializer, it)) } ?: emptyMap() },
        apply = { chosen, data ->
            set(data, chosen[name]?.let { runCatching { MeldoJson.decodeFromString(serializer.nullable, it) }.getOrNull() })
        },
    )

    /** JSON with object keys sorted (iOS: `.sortedKeys`), so equal values encode equally. */
    private fun sorted(element: JsonElement): JsonElement = when (element) {
        is JsonObject -> JsonObject(element.entries.sortedBy { it.key }.associate { it.key to sorted(it.value) })
        is JsonArray -> JsonArray(element.map(::sorted))
        else -> element
    }

    /**
     * Every setting is its own item ("preferences.studentName", …), so a setting changed
     * on one device doesn't overwrite a different one changed on another. Same keys as iOS.
     */
    private val preferences = Field(
        items = { data ->
            val encoded = MeldoJson.encodeToJsonElement(Preferences.serializer(), data.preferences) as? JsonObject
            encoded?.entries?.associate { (key, value) -> "$PREFERENCES_PREFIX$key" to MeldoJson.encodeToString(JsonElement.serializer(), sorted(value)) }
                ?: emptyMap()
        },
        apply = { chosen, data ->
            val parts = chosen.mapNotNull { (key, json) ->
                val value = runCatching { MeldoJson.parseToJsonElement(json) }.getOrNull() ?: return@mapNotNull null
                key.removePrefix(PREFERENCES_PREFIX) to value
            }.toMap()
            // Missing settings decode to their defaults.
            runCatching { MeldoJson.decodeFromJsonElement(Preferences.serializer(), JsonObject(parts)) }.getOrNull()
                ?.let { data.preferences = it }
        },
    )

    private val fields: List<Field> = listOf(
        list("subjects", Subject.serializer(), { it.id }, { it.subjects }, { d, v -> d.subjects = v }),
        list("lessons", Lesson.serializer(), { it.id }, { it.lessons }, { d, v -> d.lessons = v }),
        list("holidays", Holiday.serializer(), { it.id }, { it.holidays }, { d, v -> d.holidays = v }),
        list("participation", ParticipationEntry.serializer(), { it.id }, { it.participation }, { d, v -> d.participation = v }),
        list("tasks", SchoolTask.serializer(), { it.id }, { it.tasks }, { d, v -> d.tasks = v }),
        list("exams", ExamDocument.serializer(), { it.id }, { it.exams }, { d, v -> d.exams = v }),
        list("changes", TimetableChangeRecord.serializer(), { it.id }, { it.changes }, { d, v -> d.changes = v }),
        list("grades", GradeEntry.serializer(), { it.id }, { it.grades }, { d, v -> d.grades = v }),
        optional("untis", UntisLink.serializer(), { it.untis }, { d, v -> d.untis = v }),
        optional("untisTimetable", UntisTimetable.serializer(), { it.untisTimetable }, { d, v -> d.untisTimetable = v }),
        optional("untisExtras", UntisExtras.serializer(), { it.untisExtras }, { d, v -> d.untisExtras = v }),
        optional("teams", TeamsLink.serializer(), { it.teams }, { d, v -> d.teams = v }),
        preferences,
    )

    // endregion
}
