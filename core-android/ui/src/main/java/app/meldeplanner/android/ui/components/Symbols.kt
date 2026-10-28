package app.meldeplanner.android.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material.icons.rounded.Contrast
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.AddCircleOutline
import androidx.compose.material.icons.rounded.MoveToInbox
import androidx.compose.material.icons.rounded.Numbers
import androidx.compose.material.icons.rounded.TrackChanges
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material.icons.rounded.AppShortcut
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material.icons.rounded.Inbox
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.automirrored.rounded.Assignment
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.automirrored.rounded.ShowChart
import androidx.compose.material.icons.automirrored.rounded.ViewList
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.rounded.AddCircle
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.Book
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.CalendarToday
import androidx.compose.material.icons.rounded.Campaign
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Circle
import androidx.compose.material.icons.rounded.CloseFullscreen
import androidx.compose.material.icons.rounded.Computer
import androidx.compose.material.icons.rounded.Dashboard
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.DocumentScanner
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.EventRepeat
import androidx.compose.material.icons.rounded.Functions
import androidx.compose.material.icons.rounded.FileCopy
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FolderOff
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.HelpOutline
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.HourglassEmpty
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Dock
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MedicalServices
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.OpenInFull
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Pets
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.PersonSearch
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.SquareFoot
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.TableChart
import androidx.compose.material.icons.rounded.TheaterComedy
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Balance
import androidx.compose.material.icons.rounded.Eco
import androidx.compose.material.icons.rounded.DirectionsRun
import androidx.compose.material.icons.rounded.Biotech
import androidx.compose.material.icons.rounded.VolunteerActivism
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material.icons.rounded.WbTwilight
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.RadioButtonChecked
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.ViewTimeline
import androidx.compose.material.icons.rounded.CropSquare
import androidx.compose.material.icons.rounded.VerticalSplit
import androidx.compose.material.icons.rounded.ViewWeek
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * The SF Symbols used in the iOS app, mapped to Material Symbols (rounded). Subjects
 * store the SF Symbol name in data.json (`Subject.symbolName`), so the names stay the
 * key; add a mapping here when porting a screen that uses a new symbol.
 */
fun sfSymbol(name: String): ImageVector = when (name) {
    // Subjects (DS.subjectSymbols on iOS)
    "book.closed", "book", "book.fill" -> Icons.Rounded.Book
    "function" -> Icons.Rounded.Functions
    "x.squareroot" -> Icons.Rounded.SquareFoot
    "globe.europe.africa" -> Icons.Rounded.Public
    "atom" -> Icons.Rounded.Science
    "flask" -> Icons.Rounded.Biotech
    "leaf" -> Icons.Rounded.Eco
    "pawprint" -> Icons.Rounded.Pets
    "paintpalette" -> Icons.Rounded.Palette
    "music.note" -> Icons.Rounded.MusicNote
    "figure.run" -> Icons.Rounded.DirectionsRun
    "theatermasks" -> Icons.Rounded.TheaterComedy
    "building.columns" -> Icons.Rounded.AccountBalance
    "text.book.closed" -> Icons.AutoMirrored.Rounded.MenuBook
    "character.book.closed" -> Icons.Rounded.Translate
    "laptopcomputer" -> Icons.Rounded.Computer
    "chart.line.uptrend.xyaxis" -> Icons.AutoMirrored.Rounded.ShowChart
    "scalemass" -> Icons.Rounded.Balance
    "cross.case" -> Icons.Rounded.MedicalServices
    "hands.and.sparkles" -> Icons.Rounded.VolunteerActivism
    // Navigation and actions
    "house", "house.fill" -> Icons.Rounded.Home
    "tablecells" -> Icons.Rounded.TableChart
    "checklist" -> Icons.Rounded.Checklist
    "doc.on.doc" -> Icons.Rounded.FileCopy
    "calendar" -> Icons.Rounded.CalendarMonth
    "calendar.badge.clock" -> Icons.Rounded.Event
    "calendar.day.timeline.left" -> Icons.Rounded.ViewTimeline
    "chart.bar", "chart.bar.xaxis" -> Icons.Rounded.BarChart
    "ellipsis", "ellipsis.circle" -> Icons.Rounded.MoreHoriz
    "graduationcap" -> Icons.Rounded.School
    "person.crop.circle.badge.clock" -> Icons.Rounded.PersonSearch
    "sun.horizon", "sun.horizon.fill" -> Icons.Rounded.WbTwilight
    "sun.max" -> Icons.Rounded.WbSunny
    "arrow.triangle.2.circlepath" -> Icons.Rounded.Sync
    "person.3" -> Icons.Rounded.Groups
    "books.vertical" -> Icons.AutoMirrored.Rounded.ViewList
    "gearshape" -> Icons.Rounded.Settings
    "stethoscope" -> Icons.Rounded.MedicalServices
    "info.circle", "info.circle.fill" -> Icons.Rounded.Info
    "hand.raised", "hand.raised.fill" -> MeldoHand
    "flame", "flame.fill" -> Icons.Rounded.LocalFireDepartment
    "bell" -> Icons.Rounded.Notifications
    "bell.badge" -> Icons.Rounded.NotificationsActive
    "checkmark" -> Icons.Rounded.Check
    "checkmark.circle", "checkmark.circle.fill" -> Icons.Rounded.CheckCircle
    "chevron.left" -> Icons.Rounded.ChevronLeft
    "chevron.right" -> Icons.Rounded.ChevronRight
    "circle" -> Icons.Rounded.RadioButtonUnchecked
    "circle.fill" -> Icons.Rounded.Circle
    "smallcircle.filled.circle" -> Icons.Rounded.RadioButtonChecked
    "clock" -> Icons.Rounded.Schedule
    "hourglass" -> Icons.Rounded.HourglassEmpty
    "doc.text", "doc.questionmark" -> Icons.Rounded.Description
    "doc.text.viewfinder", "doc.viewfinder" -> Icons.Rounded.DocumentScanner
    "exclamationmark.circle" -> Icons.Rounded.ErrorOutline
    "exclamationmark.circle.fill" -> Icons.Rounded.Error
    "exclamationmark.triangle.fill" -> Icons.Rounded.Warning
    "folder" -> Icons.Rounded.Folder
    "questionmark.folder" -> Icons.Rounded.FolderOff
    "lock", "lock.fill" -> Icons.Rounded.Lock
    "magnifyingglass", "text.magnifyingglass" -> Icons.Rounded.Search
    "megaphone" -> Icons.Rounded.Campaign
    "minus" -> Icons.Rounded.Remove
    "plus" -> Icons.Rounded.Add
    "plus.circle.fill" -> Icons.Rounded.AddCircle
    "pencil" -> Icons.Rounded.Edit
    "pencil.and.list.clipboard" -> Icons.Rounded.EditNote
    "person.badge.key", "person.badge.key.fill" -> Icons.Rounded.Key
    "photo.on.rectangle" -> Icons.Rounded.PhotoLibrary
    "questionmark", "questionmark.circle" -> Icons.Rounded.HelpOutline
    "rectangle.3.group", "square.grid.2x2" -> Icons.Rounded.GridView
    "repeat" -> Icons.Rounded.Repeat
    "slider.horizontal.3" -> Icons.Rounded.Tune
    "circle.lefthalf.filled" -> Icons.Rounded.Contrast
    "sparkles" -> Icons.Rounded.AutoAwesome
    "square.and.arrow.down" -> Icons.Rounded.Download
    "square.and.arrow.up" -> Icons.Rounded.Share
    "star.fill" -> Icons.Rounded.Star
    "trash" -> Icons.Rounded.DeleteOutline
    "wrench.and.screwdriver" -> Icons.Rounded.Build
    "xmark.circle.fill" -> Icons.Rounded.Cancel
    "arrow.uturn.backward" -> Icons.AutoMirrored.Rounded.Undo
    "arrow.up.left.and.arrow.down.right" -> Icons.Rounded.OpenInFull
    "arrow.down.right", "arrow.up.left" -> Icons.Rounded.CloseFullscreen
    "app.badge" -> Icons.Rounded.Apps
    "language" -> Icons.Rounded.Language
    "assignment" -> Icons.AutoMirrored.Rounded.Assignment
    "dashboard" -> Icons.Rounded.Dashboard
    "event.repeat" -> Icons.Rounded.EventRepeat
    "calendar.today" -> Icons.Rounded.CalendarToday
    "arrow.back" -> Icons.AutoMirrored.Rounded.ArrowBack
    "arrow.forward" -> Icons.AutoMirrored.Rounded.ArrowForward
    "arrow.up" -> Icons.Rounded.ArrowUpward
    "arrow.down" -> Icons.Rounded.ArrowDownward
    // Timetable view menu
    "square" -> Icons.Rounded.CropSquare
    "rectangle.split.2x1" -> Icons.Rounded.VerticalSplit
    "rectangle.split.3x1" -> Icons.Rounded.ViewWeek
    // Aufgaben board
    "tray" -> Icons.Rounded.Inbox
    // Onboarding, Google Drive
    "hand.tap" -> Icons.Rounded.TouchApp
    "a.circle" -> Icons.Rounded.SwapHoriz
    "plus.circle" -> Icons.Rounded.AddCircleOutline
    "tray.and.arrow.down" -> Icons.Rounded.MoveToInbox
    "number" -> Icons.Rounded.Numbers
    "target" -> Icons.Rounded.TrackChanges
    "camera" -> Icons.Rounded.PhotoCamera
    "link" -> Icons.Rounded.Link
    "externaldrive" -> Icons.Rounded.CloudUpload
    "externaldrive.badge.xmark" -> Icons.Rounded.CloudOff
    "lock.shield" -> Icons.Rounded.Shield
    "iphone" -> Icons.Rounded.Smartphone
    "key" -> Icons.Rounded.Key
    "dock.rectangle" -> Icons.Rounded.Dock
    "checkmark.seal" -> Icons.Rounded.Verified
    "lock.iphone" -> Icons.Rounded.AppShortcut
    "person.crop.circle.badge.plus" -> Icons.Rounded.PersonAdd
    "exclamationmark.triangle" -> Icons.Rounded.WarningAmber
    else -> Icons.Rounded.Circle
}
