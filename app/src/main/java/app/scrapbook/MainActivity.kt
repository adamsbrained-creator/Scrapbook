package app.scrapbook

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.IntentCompat
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import app.scrapbook.ui.*
import java.io.File

class MainActivity : ComponentActivity() {
    private val vm: ArchiveViewModel by viewModels()
    private val crashFile get() = File(filesDir, "crash.txt")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) handleShare(intent)
        setContent {
            var crash by remember { mutableStateOf(crashFile.takeIf { it.exists() }?.readText()) }
            val report = crash
            if (report != null) CrashScreen(report, onShare = { shareReport(report) }, onDismiss = { crashFile.delete(); crash = null })
            else AppRoot()
        }
    }

    private fun shareReport(report: String) {
        startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"; putExtra(Intent.EXTRA_SUBJECT, "Scrapbook crash report"); putExtra(Intent.EXTRA_TEXT, report)
        }, "Share crash report"))
    }

    @Composable
    private fun AppRoot() {
        val theme by vm.theme.collectAsState()
        val incoming by vm.incoming.collectAsState()
        val darkMode by vm.darkMode.collectAsState()
        ScrapbookTheme(theme, darkMode) {
            val nav = rememberNavController()
            val dark = Sb.dark
            LaunchedEffect(dark) {
                val bars = if (dark) SystemBarStyle.dark(android.graphics.Color.TRANSPARENT) else SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
                enableEdgeToEdge(statusBarStyle = bars, navigationBarStyle = bars)
            }
            LaunchedEffect(incoming != null) { if (incoming != null) nav.navigate("capture") { launchSingleTop = true } }
            Box(Modifier.fillMaxSize().background(LocalSb.current.paper), contentAlignment = Alignment.TopCenter) {
                Box(Modifier.widthIn(max = 640.dp).fillMaxSize()) {
                    NavHost(nav, "home") {
                        composable("home") { HomeScreen(vm, nav) }
                        composable("capture") { CaptureScreen(vm, nav) }
                        composable("collections") { CollectionsScreen(vm, nav) }
                        composable("shelf/{name}", listOf(navArgument("name") { type = NavType.StringType })) {
                            ShelfScreen(vm, nav, it.arguments!!.getString("name")!!)
                        }
                        composable("search") { SearchScreen(vm, nav) }
                        composable("rediscover") { RediscoverScreen(vm, nav) }
                        composable("item/{id}", listOf(navArgument("id") { type = NavType.LongType })) {
                            ItemScreen(vm, nav, it.arguments!!.getLong("id"))
                        }
                        composable("edit/{id}", listOf(navArgument("id") { type = NavType.LongType })) {
                            EditorScreen(vm, nav, it.arguments!!.getLong("id"))
                        }
                        composable("layouts/{id}", listOf(navArgument("id") { type = NavType.LongType })) {
                            LayoutScreen(vm, nav, it.arguments!!.getLong("id"))
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleShare(intent)
    }

    /** Text, links and images shared from other apps open the capture screen. */
    private fun handleShare(i: Intent?) {
        if (i == null || (i.action != Intent.ACTION_SEND && i.action != Intent.ACTION_SEND_MULTIPLE)) return
        val text = i.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString().orEmpty()
        val subject = i.getStringExtra(Intent.EXTRA_SUBJECT).orEmpty()
        val uris: List<Uri> = if (i.action == Intent.ACTION_SEND_MULTIPLE)
            IntentCompat.getParcelableArrayListExtra(i, Intent.EXTRA_STREAM, Uri::class.java).orEmpty()
        else listOfNotNull(IntentCompat.getParcelableExtra(i, Intent.EXTRA_STREAM, Uri::class.java))
        if (text.isNotBlank() || uris.isNotEmpty()) vm.incoming.value = Incoming(text, subject, uris)
    }
}
