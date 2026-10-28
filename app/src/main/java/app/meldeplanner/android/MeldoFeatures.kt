package app.meldeplanner.android

import app.meldeplanner.android.ui.features.calendar.CalendarFeature
import app.meldeplanner.android.ui.features.dashboard.DashboardFeature
import app.meldeplanner.android.ui.features.exams.ExamsFeature
import app.meldeplanner.android.ui.features.grades.GradesFeature
import app.meldeplanner.android.ui.features.more.AboutFeature
import app.meldeplanner.android.ui.features.drive.DriveFeature
import app.meldeplanner.android.ui.features.more.MoreFeature
import app.meldeplanner.android.ui.features.more.SettingsFeature
import app.meldeplanner.android.surfaces.StatusFeature
import app.meldeplanner.android.ui.features.onboarding.OnboardingFeature
import app.meldeplanner.android.ui.features.participation.ParticipationFeature
import app.meldeplanner.android.ui.features.statistics.StatisticsFeature
import app.meldeplanner.android.ui.features.subjects.SubjectsFeature
import app.meldeplanner.android.ui.features.tasks.TasksFeature
import app.meldeplanner.android.ui.features.holidays.HolidaysFeature
import app.meldeplanner.android.ui.features.timetable.TimetableFeature
import app.meldeplanner.android.ui.features.untis.AbsencesFeature
import app.meldeplanner.android.ui.features.untis.TeamsFeature
import app.meldeplanner.android.ui.features.untis.UntisFeature
import app.meldeplanner.android.ui.navigation.Features

/**
 * Every feature in this build. The bar, the tablet sidebar, Mehr and Einstellungen
 * show them in this order. To leave a feature out, delete its line here and its
 * module from settings.gradle.kts and app/build.gradle.kts.
 */
val meldoFeatures = Features(
    listOf(
        // The bar's fixed places.
        DashboardFeature,
        TimetableFeature,
        // School: the bar's two flexible places come from these.
        TasksFeature,
        GradesFeature,
        CalendarFeature,
        StatisticsFeature,
        ExamsFeature,
        AbsencesFeature,
        HolidaysFeature,
        // Connections.
        UntisFeature,
        TeamsFeature,
        DriveFeature,
        // App.
        SubjectsFeature,
        MoreFeature,
        SettingsFeature,
        StatusFeature,
        AboutFeature,
        // Without a place of their own.
        ParticipationFeature,
        OnboardingFeature,
    ),
)
