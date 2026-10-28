package app.meldeplanner.android.ui.features.more

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.meldeplanner.android.core.L
import app.meldeplanner.android.ui.components.DS
import app.meldeplanner.android.ui.navigation.MeldoScreen
import app.meldeplanner.android.ui.rows.NoteRow
import app.meldeplanner.android.ui.rows.FormSection

/**
 * The privacy policy: what Meldo stores, where, and what leaves the device. Meldo has
 * no server of its own; every connection is to a service the student turns on
 * (WebUntis, Microsoft Teams, Google Drive). Port of PrivacyPolicyView.swift: the same
 * sections, with Android's storage (Keystore, Dateien, device transfer) where iOS
 * names the Keychain, the Files app and iCloud.
 */
@Composable
fun PrivacyPolicyScreen() {
    val sections = listOf(
        L("Kurz gesagt") to
            L("Meldo hat keinen eigenen Server und kein Konto. Wir zeigen keine Werbung, nutzen kein Tracking und keine Analyse-Dienste. Deine Daten werden auf deinem Gerät gespeichert. Nur wenn du WebUntis, Microsoft Teams oder Google Drive selbst verbindest, tauscht Meldo Daten mit diesen Diensten aus."),
        L("Was auf deinem Gerät gespeichert wird") to
            L("Fächer, Stundenplan, Meldungen, Noten, Aufgaben, Ferien, Abwesenheiten, Einstellungen, eingescannte oder importierte Dokumente sowie Entschuldigungen, die du in Meldo erstellst. Eine Kopie deiner Dokumente legt Meldo unter Dateien › Dokumente › Meldo › Scans ab. Eine Entschuldigung kann einen Grund wie Krankheit enthalten; Meldo merkt sich dafür deinen Namen und deine Klasse. Widgets, die Melden-Kachel und Mitteilungen lesen dieselben Daten auf diesem Gerät."),
        L("Passwörter und Anmeldungen") to
            L("Dein WebUntis-Passwort oder deine Schul-Anmeldung, die Anmeldung bei Microsoft und die Anmeldung bei Google werden nur verschlüsselt auf diesem Gerät gespeichert. Der Schlüssel dazu liegt im Android Keystore und kann das Gerät nicht verlassen. Sie werden weder synchronisiert noch in Backups übertragen. Auf einem neuen Gerät meldest du dich deshalb erneut an."),
        L("WebUntis") to
            L("Wenn du WebUntis verbindest, meldet sich Meldo direkt beim WebUntis-Server deiner Schule an und lädt deinen Stundenplan, Vertretungen, Abwesenheiten, Prüfungen, Hausaufgaben und Mitteilungen der Schule. Die Daten fließen nur zwischen deinem Gerät und diesem Server. Für die Verarbeitung dort sind deine Schule und der Anbieter Untis verantwortlich. Solange die Verbindung besteht, fragt Meldo dort regelmäßig nach Änderungen, auch im Hintergrund, etwa alle 30 Minuten."),
        L("Microsoft Teams") to
            L("Wenn du Teams verbindest, meldest du dich auf der Seite von Microsoft an. Meldo liest dann deine Aufgaben aus Teams über die Schnittstelle von Microsoft und speichert sie als Hausaufgaben auf deinem Gerät. Meldo sieht dein Microsoft-Passwort nicht. Meldo sucht höchstens alle drei Stunden nach neuen Aufgaben, auch im Hintergrund. Microsoft kann Daten außerhalb der EU verarbeiten; dafür gelten die Bedingungen von Microsoft und deiner Schule."),
        L("Backup in Google Drive") to
            L("Wenn du das Backup einschaltest, speichert Meldo deine Daten und Dokumente in einem versteckten Ordner in deinem Google Drive. Vorher werden sie auf deinem Gerät mit deinem Backup-Passwort verschlüsselt. Google kann den Inhalt nicht lesen, sieht aber Anzahl, Größe und Zeitpunkt der Dateien. Meldo hat nur Zugriff auf diesen eigenen Ordner, nicht auf deine anderen Dateien. Außerdem liest Meldo die E-Mail-Adresse deines Google-Kontos, um anzuzeigen, welches Konto verbunden ist. Google kann die verschlüsselten Dateien auf Servern außerhalb der EU speichern."),
        L("Mitteilungen und Live-Updates") to
            L("Erinnerungen, die Frage nach jeder Stunde und das Live-Update während der Stunde werden auf deinem Gerät erzeugt. Dafür sendet Meldo keine Daten an einen Server."),
        L("Backups deines Geräts") to
            L("Je nach Einstellung kann Android deine Meldo-Daten beim Umzug auf ein neues Gerät mitnehmen. Passwörter und Anmeldungen sind davon ausgenommen: Sie bleiben auf diesem Gerät."),
        L("Daten löschen") to
            L("Du kannst einzelne Einträge in der App löschen. Wenn du Meldo deinstallierst, entfernt Android deine Meldo-Daten und Anmeldungen auf diesem Gerät. Die Kopien deiner Dokumente unter Dateien › Dokumente › Meldo bleiben, bis du sie dort löschst. Kann Meldo seine Datei einmal nicht lesen, hebt es eine Kopie höchstens 30 Tage auf, damit nichts verloren geht. Verbindungen zu WebUntis, Teams und Google Drive kannst du jederzeit in der App trennen. Das Backup in Google Drive löschst du unter Mehr > Google Drive > Backup in Google Drive löschen."),
        L("Kinder und Jugendliche") to
            L("Meldo ist für Schülerinnen und Schüler gemacht, auch unter 16 Jahren. Meldo selbst erhebt keine Daten. Für die Anmeldung bei WebUntis, Microsoft und Google gelten die Regeln deiner Schule und dieser Anbieter. Bist du unter 16, sprich mit deinen Eltern, bevor du Microsoft oder Google verbindest."),
        L("Deine Rechte") to
            L("Da Meldo deine Daten nicht auf einem eigenen Server speichert, kann der Entwickler sie weder einsehen noch herausgeben. Anfragen zu Daten bei WebUntis, Microsoft oder Google richtest du an deine Schule oder an den jeweiligen Anbieter. Deine Rechte nach der Datenschutz-Grundverordnung (DSGVO) gelten dort weiterhin."),
        L("Verantwortlich und Kontakt") to
            L("Meldo wird von Yavuz Selim Kolbasar als kostenloses, nicht-kommerzielles Projekt entwickelt. Bei Fragen zum Datenschutz erreichst du den Entwickler unter contact@meldo.dev. Meldo ist Open Source: Den Quellcode kannst du auf GitHub selbst prüfen. Meldo ist kein Angebot von Untis, Microsoft oder Google und steht mit ihnen in keiner Verbindung."),
    )
    MeldoScreen(L("Datenschutzerklärung")) { padding ->
        LazyColumn(Modifier.fillMaxSize(), contentPadding = padding) {
            items(sections, key = { it.first }) { (title, body) ->
                FormSection(header = title) {
                    SelectionContainer { NoteRow(body, MaterialTheme.colorScheme.onSurface) }
                }
            }
            item { Spacer(Modifier.height(DS.spacingXL)) }
        }
    }
}
