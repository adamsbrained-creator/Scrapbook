package app.scrapbook.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Shown before anything else if the app crashed last time. Deliberately independent of the rest of the app. */
@Composable
fun CrashScreen(report: String, onShare: () -> Unit, onDismiss: () -> Unit) {
    MaterialTheme {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.systemBarsPadding().padding(20.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Scrapbook closed unexpectedly last time", style = MaterialTheme.typography.titleMedium)
                Text("Share this report and it can be fixed. Nothing was sent anywhere automatically.", fontSize = 13.sp)
                Button(onShare) { Text("Share report") }
                OutlinedButton(onDismiss) { Text("Dismiss and continue") }
                Text(report, fontFamily = FontFamily.Monospace, fontSize = 10.sp, lineHeight = 13.sp)
            }
        }
    }
}
