package app.scrapbook

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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

class MainActivity : ComponentActivity() {
    private val vm: ArchiveViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) handleShare(intent)
        setContent {
            val theme by vm.theme.collectAsState()
            val incoming by vm.incoming.collectAsState()
            ScrapbookTheme(theme) {
                val nav = rememberNavController()
                LaunchedEffect(incoming != null) { if (incoming != null) nav.navigate("capture") { launchSingleTop = true } }
                Box(Modifier.fillMaxSize().background(theme.paper), contentAlignment = Alignment.TopCenter) { Box(Modifier.widthIn(max = 640.dp).fillMaxSize()) {
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
                } }
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
