package app.meldeplanner.android.model

import app.meldeplanner.android.core.MeldoCalendar
import app.meldeplanner.android.data.AppData
import app.meldeplanner.android.data.SchoolTask
import app.meldeplanner.android.data.TaskKind
import app.meldeplanner.android.data.TeamsClient
import app.meldeplanner.android.data.TeamsConfig
import app.meldeplanner.android.data.TeamsFailure
import app.meldeplanner.android.data.TeamsLink
import java.time.Duration
import java.time.Instant

// Microsoft Teams assignments become homework in Aufgaben.
// Port of meldo-ios/MeldePlanner/App/AppModel+Teams.swift.

internal const val TEAMS_ACCOUNT = "microsoft-teams"

/** Teams is refreshed at most this often, when the app comes to the foreground. */
val teamsRefreshInterval: Duration = Duration.ofHours(3)

val AppModel.teamsLink: TeamsLink? get() = data.teams

/** Teams appears in Meldo when switched on in Einstellungen › Erweitert, or already connected. */
val AppModel.teamsAvailable: Boolean get() = data.preferences.teamsEnabled || data.teams != null

fun AppModel.setTeamsEnabled(enabled: Boolean) = mutate { it.preferences.teamsEnabled = enabled }

/** Finishes sign-in with the code from Microsoft's redirect, then imports. Returns the number of new assignments. */
suspend fun AppModel.connectTeams(code: String, verifier: String): Int {
    val generation = teamsGeneration
    val client = TeamsClient()
    val tokens = client.redeem(code, verifier)
    val account = try {
        client.accountName(tokens.access)
    } catch (e: TeamsFailure) {
        ""
    }
    if (generation != teamsGeneration) throw TeamsFailure.Cancelled
    secrets().save(tokens.refresh, TEAMS_ACCOUNT)
    mutate { it.teams = TeamsLink(account = account) }
    return importTeams(client, tokens.access, generation)
}

/** Re-imports with the stored refresh token. Returns the number of new assignments. */
suspend fun AppModel.refreshTeams(): Int {
    if (data.teams == null) return 0
    val refresh = secrets().read(TEAMS_ACCOUNT) ?: return 0
    val generation = teamsGeneration
    val client = TeamsClient()
    val tokens = client.refresh(refresh)
    // "Teams trennen" while Microsoft answered: don't store the new token.
    if (generation != teamsGeneration) throw TeamsFailure.Cancelled
    if (tokens.refresh.isNotEmpty()) secrets().save(tokens.refresh, TEAMS_ACCOUNT)
    return importTeams(client, tokens.access, generation)
}

/** At most every three hours, when the app comes to the foreground (and in the background). */
suspend fun AppModel.refreshTeamsIfStale() {
    if (!TeamsConfig.isConfigured) return
    val link = data.teams ?: return
    val last = link.lastSync
    val age = last?.let { Duration.between(it, currentDate) }
    if (age != null && !age.isNegative && age < teamsRefreshInterval) return
    try {
        refreshTeams()
    } catch (e: TeamsFailure) {
        // Silent: the Teams screen shows the last sync time.
    }
}

fun AppModel.disconnectTeams() {
    teamsGeneration += 1
    secrets().delete(TEAMS_ACCOUNT)
    mutate { it.teams = null }
}

private suspend fun AppModel.importTeams(client: TeamsClient, access: String, generation: Int): Int {
    val assignments = client.assignments(access)
    if (generation != teamsGeneration) throw TeamsFailure.Cancelled
    val calendar = calendar
    val now = currentDate
    var added = 0
    mutate { d -> added = TeamsMerge.addAssignments(d, assignments, now, calendar) }
    return added
}

/** The Teams merge as a pure function on [AppData] (unit-tested). */
internal object TeamsMerge {
    /**
     * Adds assignments Meldo hasn't seen; tasks already imported stay as the student
     * left them (done, edited, deleted ones aren't brought back). Returns how many were added.
     */
    fun addAssignments(d: AppData, assignments: List<TeamsClient.Assignment>, now: Instant, calendar: MeldoCalendar): Int {
        var added = 0
        val seen = d.tasks.mapNotNull { it.teamsID }.toMutableSet()
        for (a in assignments) {
            if (a.id in seen || a.title.isEmpty()) continue
            // Foundation's contains is false for an empty needle; Kotlin's is true, hence the checks.
            fun mentions(part: String?) = !part.isNullOrEmpty() && a.className.contains(part, ignoreCase = true)
            val subject = d.subjects.firstOrNull { subject ->
                a.className.isNotEmpty() && (mentions(subject.name) || mentions(subject.untisKey))
            }
            var notes = a.className
            if (a.instructions.isNotEmpty()) notes += if (notes.isEmpty()) a.instructions else "\n\n${a.instructions}"
            d.tasks.add(
                SchoolTask(
                    title = a.title, notes = notes, kind = TaskKind.homework, subjectID = subject?.id,
                    dueDate = a.due?.let { calendar.startOfDay(it) }, teamsID = a.id,
                ),
            )
            seen.add(a.id)
            added += 1
        }
        d.teams?.lastSync = now
        return added
    }
}
