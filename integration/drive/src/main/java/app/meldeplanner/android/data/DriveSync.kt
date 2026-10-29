package app.meldeplanner.android.data

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.nio.file.Files
import java.nio.file.LinkOption
import java.security.GeneralSecurityException
import java.security.MessageDigest
import java.security.SecureRandom
import java.text.BreakIterator
import java.text.Normalizer
import java.time.Instant
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

// Google Drive backup and sync, port of meldo-ios/MeldePlanner/Data/DriveSync.swift.
// Byte-compatible with the iOS app: same file names, key file, key derivation, sealing
// and JSON, so an iPhone and an Android phone share one backup.

/** One file in the app's hidden Google Drive folder. */
data class DriveFile(val id: String, val name: String, val created: Instant)

/**
 * The app-data folder of the student's Google Drive ([GoogleDriveFiles]), or a
 * stand-in in tests. Meldo sees only this folder, never the student's other files.
 */
interface DriveFiles {
    suspend fun list(): List<DriveFile>

    /** The file's bytes; throws [DriveProblem.TooLarge] past [limit] without reading further. */
    suspend fun read(id: String, limit: Int): ByteArray
    suspend fun create(name: String, data: ByteArray): DriveFile
    suspend fun update(id: String, data: ByteArray)
    suspend fun delete(id: String)
}

/** Why Drive sync stopped. */
sealed class DriveProblem : Exception() {
    /**
     * The backup was deleted, or another device started a new one with a new
     * password: the key this device holds no longer belongs to it.
     */
    data object KeyChanged : DriveProblem()

    /** A file in Drive is larger than any file Meldo writes; it is never downloaded. */
    data object TooLarge : DriveProblem()

    /** Another device saved with a newer Meldo; this one never overwrites that. */
    data object NewerVersion : DriveProblem()
}

sealed class BackupKeyProblem : Exception() {
    data object WrongPassword : BackupKeyProblem()
    data object TooShort : BackupKeyProblem()
    data object Damaged : BackupKeyProblem()

    /** The unlocked key couldn't be stored in this device's secure store. */
    data object NotSaved : BackupKeyProblem()
}

/**
 * The key that seals everything in Drive, wrapped with a key derived from the
 * student's backup password. Only the wrapped key is stored in Drive, so Google
 * can't read the backup, and a forgotten password can't be recovered.
 *
 * JSON like Swift's `Codable` for `Data`: [salt] and [wrapped] are standard Base64.
 */
@Serializable
data class BackupKey(
    val version: Int = 1,
    val salt: String,
    val rounds: Int,
    val wrapped: String,
) {
    /** The data key, when [password] is the one it was created with. */
    fun unlock(password: String, minimumRounds: Int = DEFAULT_ROUNDS): ByteArray {
        val saltBytes = decode(salt) ?: throw BackupKeyProblem.Damaged
        val wrappedBytes = decode(wrapped) ?: throw BackupKeyProblem.Damaged
        // The rounds come from Drive: values that would make this hang or be trivially weak are refused.
        if (version != 1 || rounds !in minimumRounds..MAXIMUM_ROUNDS || saltBytes.size < 16) throw BackupKeyProblem.Damaged
        val kek = derive(password, saltBytes, rounds)
        val plain = runCatching { SyncBox.open(wrappedBytes, kek, CONTEXT) }.getOrNull()
        if (plain == null || plain.size != 32) throw BackupKeyProblem.WrongPassword
        return plain
    }

    companion object {
        const val FILE_NAME = "meldo-key"

        /** PBKDF2-HMAC-SHA256 rounds (OWASP 2023): about a second on a phone, once per device. */
        const val DEFAULT_ROUNDS = 600_000
        const val MAXIMUM_ROUNDS = 3_000_000
        const val MINIMUM_PASSWORD_LENGTH = 8

        private val CONTEXT = "MeldoBackupKey/v1".toByteArray(Charsets.UTF_8)

        /** A new random data key and its wrapped form for [password]. */
        fun create(password: String, rounds: Int = DEFAULT_ROUNDS): Pair<BackupKey, ByteArray> {
            if (characterCount(password) < MINIMUM_PASSWORD_LENGTH) throw BackupKeyProblem.TooShort
            val random = SecureRandom()
            val salt = ByteArray(16).also(random::nextBytes)
            val key = ByteArray(32).also(random::nextBytes)
            val kek = derive(password, salt, rounds)
            val wrapped = SyncBox.seal(key, kek, CONTEXT)
            return BackupKey(salt = encode(salt), rounds = rounds, wrapped = encode(wrapped)) to key
        }

        /**
         * PBKDF2-HMAC-SHA256 (RFC 8018) over the NFC-normalized UTF-8 password, 32 bytes, as
         * CommonCrypto's `CCKeyDerivationPBKDF` on iOS. Written out on `Mac` so the password
         * bytes are exactly the same as on iOS, whatever charset a provider would pick.
         */
        fun derive(password: String, salt: ByteArray, rounds: Int): ByteArray {
            // The same password typed on another keyboard must give the same key.
            val secret = Normalizer.normalize(password, Normalizer.Form.NFC).toByteArray(Charsets.UTF_8)
            val mac = Mac.getInstance("HmacSHA256")
            // HMAC keys may be empty in theory; SecretKeySpec refuses that, so use one zero byte
            // (HMAC pads the key with zeros, giving the same result).
            mac.init(SecretKeySpec(if (secret.isEmpty()) ByteArray(1) else secret, "HmacSHA256"))
            mac.update(salt)
            mac.update(byteArrayOf(0, 0, 0, 1))
            val u = mac.doFinal()
            val result = u.copyOf()
            for (i in 1 until rounds) {
                mac.update(u)
                mac.doFinal(u, 0)
                for (j in result.indices) result[j] = (result[j].toInt() xor u[j].toInt()).toByte()
            }
            return result
        }

        /** Swift's `String.count`: user-perceived characters, not UTF-16 units. */
        internal fun characterCount(text: String): Int {
            val iterator = BreakIterator.getCharacterInstance()
            iterator.setText(text)
            var count = 0
            while (iterator.next() != BreakIterator.DONE) count += 1
            return count
        }

        internal fun encode(bytes: ByteArray): String = Base64.getEncoder().encodeToString(bytes)

        internal fun decode(text: String): ByteArray? = runCatching { Base64.getDecoder().decode(text) }.getOrNull()
    }
}

/**
 * Where document PDFs live. The app uses [ExamFiles]' Scans folder; tests use a
 * temporary folder so they never touch the student's real scans.
 */
class DocumentFolder(
    val locate: (String) -> File?,
    val scans: () -> File?,
    /** Only regular files inside Meldo's folder are uploaded (no links someone placed there). */
    val isUploadable: (File) -> Boolean,
) {
    companion object {
        /** Documents in [root] (filesDir/Scans in the app). */
        fun inside(root: File) = DocumentFolder(
            locate = { name -> if (isSafeName(name)) File(root, name) else null },
            scans = { root },
            isUploadable = { file ->
                val path = file.toPath()
                !Files.isSymbolicLink(path) && Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) &&
                    runCatching { file.canonicalPath.startsWith(root.canonicalPath + File.separator) }.getOrDefault(false)
            },
        )

        /** A relative path without empty, "." or ".." parts (same rule as iOS `ExamFiles.isSafe`). */
        fun isSafeName(fileName: String): Boolean {
            val parts = fileName.split('/')
            return !fileName.startsWith("/") && !fileName.contains('\\') && parts.none { it.isEmpty() || it == "." || it == ".." }
        }
    }
}

/**
 * Google Drive sync and backup: an item-by-item merge ([SyncMerge]), with every
 * file sealed by [SyncBox] under the backup key.
 */
object DriveSync {
    const val DATA_NAME = "meldo-data"

    /** Upper bounds for what is read from Drive (sealing adds 28 bytes). */
    const val MAX_KEY_BYTES = 64 * 1024
    const val MAX_DATA_BYTES = 50 * 1024 * 1024

    /** iOS `ExamFiles.maxImportBytes` (100 MB) plus room for sealing. */
    const val MAX_FILE_BYTES = 100 * 1024 * 1024 + 1024

    /** Upper-case, like Swift's `UUID.uuidString`. */
    fun fileName(document: String): String = "meldo-file-${document.uppercase()}"

    private val keyJson = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    /** The key file as Swift's `JSONEncoder` writes it (Data as Base64, "/" escaped). */
    fun encodeKey(key: BackupKey): ByteArray =
        keyJson.encodeToString(BackupKey.serializer(), key).replace("/", "\\/").toByteArray(Charsets.UTF_8)

    fun decodeKey(bytes: ByteArray): BackupKey? = runCatching {
        val key = keyJson.decodeFromString(BackupKey.serializer(), String(bytes, Charsets.UTF_8))
        // Swift decodes both as Data: anything that isn't Base64 is a damaged file there too.
        if (BackupKey.decode(key.salt) == null || BackupKey.decode(key.wrapped) == null) null else key
    }.getOrNull()

    /**
     * Identifies a key file: SHA-256 of the whole file (salt, rounds, wrapped key) as Swift's
     * `JSONEncoder` with `.sortedKeys` writes it, so both apps compute the same ID.
     */
    fun keyID(key: BackupKey): String {
        val salt = BackupKey.decode(key.salt)?.let(BackupKey::encode) ?: key.salt
        val wrapped = BackupKey.decode(key.wrapped)?.let(BackupKey::encode) ?: key.wrapped
        fun string(text: String) = "\"" + text.replace("\\", "\\\\").replace("\"", "\\\"").replace("/", "\\/") + "\""
        val json = "{\"rounds\":${key.rounds},\"salt\":${string(salt)},\"version\":${key.version},\"wrapped\":${string(wrapped)}}"
        return MessageDigest.getInstance("SHA-256").digest(json.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    }

    /** The backup key file in Drive, or null when this Google account has no Meldo backup yet. */
    suspend fun keyFile(drive: DriveFiles): BackupKey? = keyFile(drive, drive.list())

    private suspend fun keyFile(drive: DriveFiles, files: List<DriveFile>): BackupKey? {
        val file = earliest(BackupKey.FILE_NAME, files) ?: return null
        val data = try {
            drive.read(file.id, MAX_KEY_BYTES)
        } catch (e: DriveProblem.TooLarge) {
            throw BackupKeyProblem.Damaged
        }
        return decodeKey(data) ?: throw BackupKeyProblem.Damaged
    }

    /**
     * Stores a new key file. When another device stored one at the same moment, the
     * first one wins and is returned instead.
     */
    suspend fun storeKeyFile(key: BackupKey, drive: DriveFiles): BackupKey {
        val mine = drive.create(BackupKey.FILE_NAME, encodeKey(key))
        val winner = keyFile(drive)
        if (winner == null || winner == key) return key
        // Lost the race: remove this device's key file so only one stays in Drive.
        runCatching { drive.delete(mine.id) }
        return winner
    }

    /**
     * Merges the Drive copy into [store], uploads the result, then the documents.
     * Throws [DriveProblem.KeyChanged] when Drive's backup isn't the one [keyID] names;
     * stops between steps when the coroutine is cancelled ("trennen").
     */
    suspend fun run(store: LocalStore, drive: DriveFiles, key: ByteArray, keyID: String, documents: DocumentFolder) {
        val files = drive.list()
        // A deleted or replaced backup is never recreated under the old key. Network errors
        // pass through: only a missing, damaged or different key file means "changed".
        val current = try {
            keyFile(drive, files)
        } catch (e: BackupKeyProblem.Damaged) {
            throw DriveProblem.KeyChanged
        }
        if (current == null || keyID(current) != keyID) throw DriveProblem.KeyChanged
        var merged = store.load()
        val existing = earliest(DATA_NAME, files)
        if (existing != null) {
            val theirs = remoteData(existing, drive, key)
            if (theirs != null) {
                merged = store.update(stamping = false) { local ->
                    val result = SyncMerge.merge(local, theirs)
                    overwrite(local, result)
                }
                // Nothing new here: no upload.
                if (canonical(merged) == canonical(theirs)) {
                    syncDocuments(merged, files, drive, key, documents)
                    return
                }
            }
        }
        currentCoroutineContext().ensureActive()
        val sealed = SyncBox.seal(MeldoJson.encodeToString(AppData.serializer(), merged).toByteArray(Charsets.UTF_8), key, context(DATA_NAME))
        // Every device reads a larger file as damaged and replaces it with its own data, so one
        // this large is never uploaded: that would cost the other devices their changes.
        if (sealed.size > MAX_DATA_BYTES) throw DriveProblem.TooLarge
        // shortcut: Drive has no conditional write, so two devices saving at the same moment can
        // overwrite each other's upload. Nothing is lost: each keeps its data and merges next time.
        if (existing != null) {
            // Also repairs a meldo-data that couldn't be opened (other devices merge theirs back in).
            drive.update(existing.id, sealed)
        } else {
            drive.create(DATA_NAME, sealed)
        }
        syncDocuments(merged, files, drive, key, documents)
    }

    /** Copies every field of [source] into [target] (LocalStore.update changes data in place). */
    private fun overwrite(target: AppData, source: AppData) {
        target.schemaVersion = source.schemaVersion
        target.subjects = source.subjects
        target.lessons = source.lessons
        target.holidays = source.holidays
        target.participation = source.participation
        target.tasks = source.tasks
        target.exams = source.exams
        target.changes = source.changes
        target.untis = source.untis
        target.untisTimetable = source.untisTimetable
        target.untisExtras = source.untisExtras
        target.teams = source.teams
        target.grades = source.grades
        target.preferences = source.preferences
        target.dismissedImports = source.dismissedImports
        target.sync = source.sync
    }

    /**
     * The other devices' data, or null when the file is damaged or planted (too large, or
     * it doesn't open with the key): it is then overwritten with this device's data.
     * Data that opens but this version can't read came from a newer Meldo: never overwritten.
     */
    private suspend fun remoteData(file: DriveFile, drive: DriveFiles, key: ByteArray): AppData? {
        val sealed = try {
            drive.read(file.id, MAX_DATA_BYTES)
        } catch (e: DriveProblem.TooLarge) {
            return null
        } catch (e: OutOfMemoryError) {
            // More than this device can hold right now. That says nothing about the file, so it is
            // never overwritten for it: sync stops and tries again later.
            throw DriveProblem.TooLarge
        }
        val plain = orNull { SyncBox.open(sealed, key, context(DATA_NAME)) } ?: return null
        val text = String(plain, Charsets.UTF_8)
        // A blob that names a newer schema really is a newer Meldo: never overwrite it. One that
        // opens with our key but can't be decoded as current data — and names no newer schema — is
        // corrupt, not newer: return null so it's overwritten instead of wedging sync for every device.
        val probe = orNull { MeldoJson.decodeFromString(SchemaProbe.serializer(), text) }
        if (probe != null && probe.schemaVersion > AppData.CURRENT_SCHEMA_VERSION) throw DriveProblem.NewerVersion
        return orNull { MeldoJson.decodeFromString(AppData.serializer(), text) }
    }

    /** Null when [block] fails on the file itself. Running out of memory is this device's problem,
     *  not the file's, so it stops sync instead of letting the file be overwritten. */
    private inline fun <T> orNull(block: () -> T): T? = try {
        block()
    } catch (e: OutOfMemoryError) {
        throw DriveProblem.TooLarge
    } catch (e: Exception) {
        null
    }

    /** Just the schemaVersion of a sealed data blob, to tell a genuinely newer Meldo from a corrupt file. */
    @kotlinx.serialization.Serializable
    private data class SchemaProbe(val schemaVersion: Int = 1)

    /**
     * Uploads PDFs Drive doesn't have, downloads the ones this device misses, and
     * removes the files of deleted documents. One damaged file never stops the others.
     */
    // shortcut: a PDF is uploaded once and never compared again; edited scans need a content hash then.
    private suspend fun syncDocuments(data: AppData, files: List<DriveFile>, drive: DriveFiles, key: ByteArray, folder: DocumentFolder) {
        val byName = mutableMapOf<String, DriveFile>()
        for (file in files) {
            val kept = byName[file.name]
            if (kept != null && earlier(kept, file)) continue
            byName[file.name] = file
        }
        for (document in data.exams) {
            currentCoroutineContext().ensureActive()
            val name = fileName(document.id)
            val local = folder.locate(document.fileName)
            val hasLocal = local?.exists() ?: false
            val remote = byName[name]
            if (remote != null && !hasLocal) {
                // The synced name is lower-trust input: it must stay inside the scans folder.
                if (!DocumentFolder.isSafeName(document.fileName)) continue
                val target = folder.scans()?.let { File(it, document.fileName) } ?: continue
                if (target.exists()) continue
                val pdf = try {
                    SyncBox.open(drive.read(remote.id, MAX_FILE_BYTES), key, context(name))
                } catch (e: DriveProblem.TooLarge) {
                    // Not a file Meldo wrote: remove it, a device that has the PDF uploads it again.
                    runCatching { drive.delete(remote.id) }
                    continue
                } catch (e: GeneralSecurityException) {
                    runCatching { drive.delete(remote.id) }
                    continue
                } catch (e: OutOfMemoryError) {
                    // Too big for this device's memory right now. It can be a real scan (up to 100 MB),
                    // and Drive may hold its only copy, so it is skipped, never deleted.
                    continue
                }
                // A file added here while downloading (same name) is never replaced.
                if (target.exists()) continue
                // A name this device can't store (too long, or below a file) skips that document only;
                // the others and the cleanup below still run.
                target.parentFile?.mkdirs()
                val temp = File(target.parentFile, ".${target.name}.download")
                val stored = runCatching { temp.writeBytes(pdf) }.isSuccess && temp.renameTo(target)
                if (!stored) {
                    temp.delete()
                    continue
                }
            } else if (remote == null && hasLocal && local != null) {
                if (!folder.isUploadable(local)) continue
                val sealed = SyncBox.seal(local.readBytes(), key, context(name))
                // Other devices delete a larger file as not Meldo's, so it would never arrive.
                if (sealed.size > MAX_FILE_BYTES) continue
                drive.create(name, sealed)
            }
        }
        for (entry in data.sync.tombstones.keys) {
            if (!entry.startsWith("exams:")) continue
            val id = entry.removePrefix("exams:")
            if (!uuid.matches(id)) continue
            val file = byName[fileName(id)] ?: continue
            currentCoroutineContext().ensureActive()
            for (duplicate in files.filter { it.name == file.name }) drive.delete(duplicate.id)
        }
    }

    private val uuid = Regex("^[0-9A-Fa-f]{8}-[0-9A-Fa-f]{4}-[0-9A-Fa-f]{4}-[0-9A-Fa-f]{4}-[0-9A-Fa-f]{12}$")

    /** The same data always gives the same bytes, for comparing. */
    private fun canonical(data: AppData): String = MeldoJson.encodeToString(AppData.serializer(), data)

    /** Two devices can create the same file at once; the oldest one counts. */
    fun earliest(name: String, files: List<DriveFile>): DriveFile? =
        files.filter { it.name == name }.minWithOrNull(compareBy<DriveFile> { it.created }.thenBy { it.id })

    private fun earlier(a: DriveFile, b: DriveFile): Boolean =
        a.created < b.created || (a.created == b.created && a.id < b.id)

    /** Binds a sealed file to its name, so one file's content can't stand in for another's. */
    fun context(name: String): ByteArray = "drive/$name".toByteArray(Charsets.UTF_8)
}

/**
 * AES-256-GCM like CryptoKit's `AES.GCM.SealedBox.combined`: 12-byte nonce, ciphertext,
 * 16-byte tag. A fresh nonce per message; [context] (the file it belongs to) is
 * authenticated too, so it must match on [open].
 */
object SyncBox {
    private const val NONCE_BYTES = 12
    private const val TAG_BITS = 128

    fun seal(plain: ByteArray, key: ByteArray, context: ByteArray): ByteArray =
        seal(plain, key, context, ByteArray(NONCE_BYTES).also(SecureRandom()::nextBytes))

    /** With a given nonce: only for test vectors, never reuse a nonce. */
    internal fun seal(plain: ByteArray, key: ByteArray, context: ByteArray, nonce: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, nonce))
        cipher.updateAAD(context)
        return nonce + cipher.doFinal(plain)
    }

    /** Throws a [GeneralSecurityException] when the box was changed or the key or context differ. */
    fun open(sealed: ByteArray, key: ByteArray, context: ByteArray): ByteArray {
        if (sealed.size < NONCE_BYTES + TAG_BITS / 8) throw javax.crypto.AEADBadTagException("too short")
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, sealed, 0, NONCE_BYTES))
        cipher.updateAAD(context)
        return cipher.doFinal(sealed, NONCE_BYTES, sealed.size - NONCE_BYTES)
    }
}
