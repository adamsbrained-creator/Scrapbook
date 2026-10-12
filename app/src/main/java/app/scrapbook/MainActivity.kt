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
    /** True when the app is starting from scratch. Not after a rotation or when Android restores the app, because the navigation state comes back by itself then. */
    private var freshLaunch = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        freshLaunch = savedInstanceState == null
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
        val greyBg by vm.greyBg.collectAsState()
        val showPreview by vm.showPreview.collectAsState()
        val boxStyle by vm.boxStyle.collectAsState()
        ScrapbookTheme(theme, darkMode, greyBg) {
            val nav = rememberNavController()
            val dark = Sb.dark
            LaunchedEffect(dark) {
                val bars = if (dark) SystemBarStyle.dark(android.graphics.Color.TRANSPARENT) else SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
                enableEdgeToEdge(statusBarStyle = bars, navigationBarStyle = bars)
            }
            // Like Samsung Notes reopening its last folder: a fresh launch opens the box you were in. Until that is decided (a few
            // milliseconds to read the saved settings) a blank page is shown instead of flashing the main page first.
            var ready by remember { mutableStateOf(!freshLaunch) }
            var reopen by remember { mutableStateOf(emptyList<String>()) }
            LaunchedEffect(Unit) {
                if (!ready) {
                    // Sharing something into the app always goes to the capture screen instead.
                    if (incoming == null && vm.readReopenLastBox()) reopen = vm.boxPath(vm.readLastBox())
                    ready = true
                }
            }
            LaunchedEffect(ready) {
                if (!ready) return@LaunchedEffect
                // Open each box on the way (box, then the box inside it), so Back climbs up the same way it would have.
                reopen.forEach { nav.navigate("shelf/$it") }
                // From here on, remember the box on screen. The main page, All scraps, Pinned and Recent are not boxes: they clear it.
                nav.currentBackStackEntryFlow.collect { e ->
                    when (e.destination.route) {
                        "home" -> vm.setLastBox("")
                        "shelf/{name}" -> vm.setLastBox(e.arguments?.getString("name")?.takeUnless { it == "All" || it == "Pinned" || it == "Recent" } ?: "")
                    }
                }
            }
            LaunchedEffect(incoming != null, ready) { if (ready && incoming != null) nav.navigate("capture") { launchSingleTop = true } }
            Box(Modifier.fillMaxSize().background(LocalSb.current.paper), contentAlignment = Alignment.TopCenter) {
                Box(Modifier.widthIn(max = 640.dp).fillMaxSize()) {
                    if (ready) CompositionLocalProvider(LocalShowPreview provides showPreview, LocalBoxStyle provides boxStyle) {
                        NavHost(nav, "home") {
                            composable("home") { HomeScreen(vm, nav) }
                            composable("settings") { SettingsScreen(vm, nav) }
                            composable("about") { AboutScreen(nav) }
                            composable("whatsnew") { WhatsNewScreen(nav) }
                            composable("licenses") { LicensesScreen(nav) }
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
