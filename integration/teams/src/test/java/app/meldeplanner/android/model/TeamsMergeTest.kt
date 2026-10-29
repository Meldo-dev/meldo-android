package app.meldeplanner.android.model

import app.meldeplanner.android.core.MeldoCalendar
import app.meldeplanner.android.data.AppData
import app.meldeplanner.android.data.Subject
import app.meldeplanner.android.data.TeamsClient
import app.meldeplanner.android.data.TeamsFailure
import app.meldeplanner.android.data.TeamsLink
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** The Teams merge rules and sign-in callback (ports of MeldePlannerTests/UntisImportTests.swift). */
class TeamsMergeTest {
    private val calendar = MeldoCalendar(ZoneId.of("Europe/Berlin"))
    private val now: Instant get() = calendar.instant(LocalDate.of(2026, 10, 21))

    @Test
    fun teamsAssignmentsAreAddedOnceAndMatchSubjects() {
        val d = AppData()
        d.subjects.add(Subject(name = "Biologie"))
        d.teams = TeamsLink(account = "max@schule.de")
        val due = Instant.parse("2026-10-23T21:59:00Z")
        val assignments = listOf(
            TeamsClient.Assignment("a1", "Zellaufbau", "Q1 Biologie GK", due, "Lies S. 40", null),
            TeamsClient.Assignment("a2", "", "Q1 Biologie GK", null, "", null),
            TeamsClient.Assignment("a3", "Referat", "", null, "", null),
        )
        assertEquals(2, TeamsMerge.addAssignments(d, assignments, now, calendar))
        val task = d.tasks.first { it.teamsID == "a1" }
        assertEquals(d.subjects[0].id, task.subjectID)
        assertEquals("Q1 Biologie GK\n\nLies S. 40", task.notes)
        assertEquals(calendar.startOfDay(due), task.dueDate)
        assertNull(d.tasks.first { it.teamsID == "a3" }.subjectID)
        assertEquals(now, d.teams?.lastSync)
        // Deleted tasks aren't brought back either: only unseen ids count.
        d.tasks.removeAll { it.teamsID == "a3" }
        assertEquals(1, TeamsMerge.addAssignments(d, assignments, now, calendar))
        assertEquals(0, TeamsMerge.addAssignments(d, assignments, now, calendar))
    }

    /** An assignment the student deleted (here or on iOS) stays deleted on the next import. */
    @Test
    fun deletedAssignmentsAreNotImportedAgain() {
        val d = AppData()
        d.teams = TeamsLink(account = "max@schule.de")
        d.dismissedImports.add("teams:a1")
        val assignments = listOf(TeamsClient.Assignment("a1", "Zellaufbau", "Q1 Biologie GK", null, "", null))
        assertEquals(0, TeamsMerge.addAssignments(d, assignments, now, calendar))
        assertTrue(d.tasks.isEmpty())
    }

    @Test
    fun teamsCallbackAndHtml() {
        assertEquals("M.R3_BAY", TeamsClient.code("meldeplanner://auth?code=M.R3_BAY&state=x", "x"))
        try {
            TeamsClient.code("meldeplanner://auth?error=access_denied&error_description=Admin%20consent%20required&state=x", "x")
            fail("expected a refusal")
        } catch (e: TeamsFailure.Denied) {
            assertEquals("Admin consent required", e.detail)
        }
        assertEquals("Lies S. 40\nund fasse zusammen & lerne", TeamsClient.plainText("<p>Lies S.&nbsp;40</p><b>und</b> fasse zusammen &amp; lerne"))
        val auth = TeamsClient.authorization()
        assertTrue(auth.url.startsWith("https://login.microsoftonline.com/common/oauth2/v2.0/authorize?client_id="))
        assertTrue(auth.url.contains("code_challenge_method=S256"))
        assertTrue(auth.url.contains("redirect_uri=meldeplanner%3A%2F%2Fauth"))
        assertEquals(43, auth.verifier.length)
    }
}
