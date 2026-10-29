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
            L("Meldo hat keinen eigenen Server und kein Konto. Wir zeigen keine Werbung, nutzen kein Tracking und keine Analyse-Dienste. Deine Daten werden auf deinem Gerät gespeichert. Nur wenn du Microsoft Teams oder Google Drive selbst verbindest, tauscht Meldo Daten mit diesen Diensten aus."),
        L("Was auf deinem Gerät gespeichert wird") to
            L("Fächer, Stundenplan, Meldungen, Noten, Aufgaben, Einstellungen, eingescannte oder importierte Dokumente. Eine Kopie deiner Dokumente legt Meldo unter Dateien › Dokumente › Meldo › Scans ab. Widgets, die Melden-Kachel und Mitteilungen lesen dieselben Daten auf diesem Gerät."),
        L("Scannen, Fotos und Dateien") to
            L("Zum Scannen nutzt Meldo den Dokumentenscanner von Google (ML Kit), der über die Google Play-Dienste läuft. Die Bilder werden auf deinem Gerät verarbeitet und als PDF in Meldo gespeichert. Die Google Play-Dienste können dabei technische Nutzungs- und Fehlerdaten zum Scanner an Google senden, aber nicht deine Scans. Bei Fotos bekommt Meldo über die Fotoauswahl von Android nur die Bilder, die du auswählst. PDFs wählst du über die Dateiauswahl aus."),
        L("Passwörter und Anmeldungen") to
            L("Die Anmeldung bei Microsoft und die Anmeldung bei Google werden nur verschlüsselt auf diesem Gerät gespeichert. Der Schlüssel dazu liegt im Android Keystore und kann das Gerät nicht verlassen. Sie werden weder synchronisiert noch in Backups übertragen. Auf einem neuen Gerät meldest du dich deshalb erneut an."),
        L("WebUntis") to
            L("WebUntis-Anbindung: wartet auf Freigabe durch Untis. WebUntis ist derzeit nicht Teil der App, Meldo verbindet sich nicht mit WebUntis. Hast du WebUntis mit einer früheren Version verbunden, bleiben die damals geladenen Daten auf deinem Gerät und werden nicht mehr aktualisiert. Deine damalige WebUntis-Anmeldung bleibt verschlüsselt auf dem Gerät gespeichert, wird aber nicht mehr verwendet."),
        L("Microsoft Teams") to
            L("Wenn du Teams verbindest, meldest du dich auf der Seite von Microsoft an. Meldo liest dann über die Schnittstelle von Microsoft deine Aufgaben, deine Klassen und deinen Namen (oder, falls keiner hinterlegt ist, deine Schul-E-Mail-Adresse) und bleibt angemeldet. Die Aufgaben speichert Meldo als Hausaufgaben auf deinem Gerät. Deinen Namen und die Namen deiner Klassen zeigt Meldo nur in der App an. Meldo sieht dein Microsoft-Passwort nicht. Meldo sucht höchstens alle drei Stunden nach neuen Aufgaben, auch im Hintergrund. Microsoft kann Daten außerhalb der EU verarbeiten; dafür gelten die Bedingungen von Microsoft und deiner Schule."),
        L("Backup in Google Drive") to
            L("Wenn du das Backup einschaltest, speichert Meldo deine Daten und Dokumente in einem versteckten Ordner in deinem Google Drive. Vorher werden sie auf deinem Gerät mit deinem Backup-Passwort verschlüsselt. Google kann den Inhalt nicht lesen, sieht aber Anzahl, Größe und Zeitpunkt der Dateien. Meldo hat nur Zugriff auf diesen eigenen Ordner, nicht auf deine anderen Dateien. Außerdem liest Meldo die E-Mail-Adresse deines Google-Kontos, um anzuzeigen, welches Konto verbunden ist. Google kann die verschlüsselten Dateien auf Servern außerhalb der EU speichern. Die Nutzung von Informationen, die Meldo von Google-APIs erhält, und ihre Übertragung an andere Apps entsprechen der Google API Services User Data Policy, einschließlich der Anforderungen zur eingeschränkten Nutzung (Limited Use)."),
        L("Mitteilungen und Live-Updates") to
            L("Erinnerungen, die Frage nach jeder Stunde und das Live-Update während der Stunde werden auf deinem Gerät erzeugt. Dafür sendet Meldo keine Daten an einen Server. Das Live-Update und Mitteilungen zeigen Fächer und Aufgaben auch auf dem Sperrbildschirm. Ob Inhalte dort erscheinen, stellst du in den Einstellungen deines Geräts ein."),
        L("Backups deines Geräts") to
            L("Je nach Einstellung kann Android deine Meldo-Daten beim Umzug auf ein neues Gerät mitnehmen. Passwörter und Anmeldungen sind davon ausgenommen: Sie bleiben auf diesem Gerät."),
        L("Daten löschen") to
            L("Du kannst einzelne Einträge in der App löschen. Wenn du Meldo deinstallierst, entfernt Android deine Meldo-Daten und Anmeldungen auf diesem Gerät. Die Kopien deiner Dokumente unter Dateien › Dokumente › Meldo bleiben, bis du sie dort löschst. Kann Meldo seine Datei einmal nicht lesen, hebt es eine Kopie höchstens 30 Tage auf, damit nichts verloren geht. Verbindungen zu Teams und Google Drive kannst du jederzeit in der App trennen. Das Backup in Google Drive löschst du unter Mehr > Google Drive > Backup in Google Drive löschen."),
        L("Rechtsgrundlage") to
            L("Was du in Meldo einträgst, bleibt auf deinem Gerät. Deine Einträge bekommt der Entwickler nicht. Meldo verarbeitet deine Einträge und die Verbindungen, die du einschaltest, weil das nötig ist, damit du Meldo wie in den Nutzungsbedingungen beschrieben nutzen kannst (Art. 6 Abs. 1 lit. b DSGVO). Meldo stützt sich auf keine Einwilligung. Tippst du bei Google oder Microsoft auf „Zulassen“, bekommt Meldo dort Zugriff. Für die Verarbeitung auf ihrer Seite sind diese Anbieter oder deine Schule verantwortlich."),
        L("Kinder und Jugendliche") to
            L("Meldo ist für Schülerinnen und Schüler gemacht, auch unter 16 Jahren. Der Entwickler sammelt über Meldo keine Daten über dich. Für die Anmeldung bei Microsoft und Google gelten die Regeln deiner Schule und dieser Anbieter. Bist du unter 16, sprich mit deinen Eltern, bevor du Microsoft oder Google verbindest."),
        L("Deine Rechte") to
            L("Da Meldo deine Einträge nicht auf einem eigenen Server speichert, kann der Entwickler sie weder einsehen noch herausgeben. Hast du Meldo aus Google Play installiert und erlaubt, dass dein Gerät Nutzungs- und Diagnosedaten an Google sendet, gibt Google Play Absturzberichte ohne deinen Namen an den Entwickler weiter. Sie enthalten Gerätemodell, Systemversion und die Stelle im Code, aber keine Meldo-Einträge, und dienen nur dazu, Fehler zu beheben (Art. 6 Abs. 1 lit. f DSGVO). Anfragen zu Daten bei Microsoft oder Google richtest du an deine Schule oder an den jeweiligen Anbieter. Deine Rechte nach der Datenschutz-Grundverordnung (DSGVO) gelten dort weiterhin. Du hast das Recht auf Auskunft, Berichtigung, Löschung, Einschränkung, Datenübertragbarkeit, Widerspruch und Beschwerde bei einer Aufsichtsbehörde. Widerspruchsrecht: Verarbeiten wir Daten nach Art. 6 Abs. 1 lit. f DSGVO, kannst du dem jederzeit widersprechen, per E-Mail an contact@meldo.dev. Die ganze Datenschutzerklärung findest du unter meldo.dev/de/privacy."),
        L("Verantwortlich und Kontakt") to
            L("Meldo wird von Yavuz Selim Kolbasar als kostenloses, nicht-kommerzielles Projekt entwickelt. Bei Fragen zum Datenschutz erreichst du den Entwickler unter contact@meldo.dev. Meldo ist Open Source: Den Quellcode kannst du auf GitHub selbst prüfen. Meldo ist kein Angebot von Untis, Microsoft oder Google und steht mit ihnen in keiner Verbindung."),
        L("Widerspruchsrecht") to
            L("Verarbeitet der Entwickler Daten nach Art. 6 Abs. 1 lit. f DSGVO (Absturzberichte, E-Mails), kannst du dem jederzeit widersprechen, per E-Mail an contact@meldo.dev."),
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
