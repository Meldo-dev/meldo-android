plugins {
    id("meldo.android.feature")
}

// Dokumente: scanned and imported exams and worksheets.
android {
    namespace = "app.meldeplanner.android.feature.exams"
}

dependencies {
    // Document scanner (edge detection, crop, PDF), the counterpart of VisionKit on iOS.
    implementation(libs.mlkit.document.scanner)
}
