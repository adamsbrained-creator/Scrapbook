package app.scrapbook.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController

/** Placeholder About page. It shares the header and title look of Settings, so the real content can be dropped into the card later. */
@Composable
fun AboutScreen(nav: NavController) {
    val t = LocalSb.current; val ctx = LocalContext.current
    val version = remember { runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }.getOrNull().orEmpty() }
    Scaffold(containerColor = t.paper) { pad ->
        Column(Modifier.fillMaxSize().padding(pad)) {
            Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                CircleBtn("Go back", { nav.popBackStack() }) { Ic("back", 22.dp, Sb.ink) }
                Box(Modifier.weight(1f), Alignment.Center) { BrandBar() }
                Spacer(Modifier.size(44.dp))
            }
            PageTitle("About")
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                Spacer(Modifier.height(22.dp))
                Column(Modifier.padding(horizontal = 20.dp).fillMaxWidth().clip(RoundedCornerShape(26.dp)).background(Sb.card).padding(horizontal = 18.dp, vertical = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Scrapbook", fontFamily = headFont(), fontWeight = FontWeight.Medium, fontSize = 26.sp, letterSpacing = (-0.02).em)
                    if (version.isNotEmpty()) Text("Version $version", fontSize = 15.sp, color = Sb.mute)
                    Text("A personal notes and archive app for notes, quotes, links and photos.", Modifier.padding(top = 10.dp), fontSize = 15.sp, lineHeight = 22.sp)
                }
                Text("More about Scrapbook is coming soon.", Modifier.padding(start = 24.dp, end = 24.dp, top = 12.dp), fontSize = 13.sp, color = Sb.mute)
                Spacer(Modifier.height(32.dp))
            }
        }
    }
}
