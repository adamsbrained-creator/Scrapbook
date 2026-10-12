package app.scrapbook.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController

// The About page, plus the two pages it opens inside the app: What's new and Open-source licenses.
// Layout, spacing and colors follow the "About" mockup. It reuses the grouped rows from Settings.kt.

/**
 * Where the About rows point. Fill these in. While one is blank, its row shows a short "not set up yet" message instead of opening something.
 * (Rate Scrapbook needs no setting: it opens this app's Google Play page.)
 */
object AboutLinks {
    const val FEEDBACK_EMAIL = ""
    const val PRIVACY_URL = ""
    const val PAPERBACK_URL = ""
    const val BINDER_URL = ""
}

private fun Context.toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_SHORT).show()

private fun Context.tryStart(intent: Intent) {
    try { startActivity(intent) } catch (e: ActivityNotFoundException) { toast("Nothing on this phone can open that.") }
}

private fun Context.openLink(url: String) {
    if (url.isBlank()) { toast("This link isn't set up yet."); return }
    tryStart(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
}

private fun Context.sendFeedback(version: String) {
    if (AboutLinks.FEEDBACK_EMAIL.isBlank()) { toast("The feedback address isn't set up yet."); return }
    tryStart(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:")).apply {
        putExtra(Intent.EXTRA_EMAIL, arrayOf(AboutLinks.FEEDBACK_EMAIL))
        putExtra(Intent.EXTRA_SUBJECT, "Scrapbook feedback")
        putExtra(Intent.EXTRA_TEXT, "\n\n--\nScrapbook $version, Android ${Build.VERSION.RELEASE}, ${Build.MODEL}")
    })
}

/** Opens this app's Play Store page, in the Play app if there is one, otherwise in a browser. */
private fun Context.rateApp() {
    try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$packageName"))) }
    catch (e: ActivityNotFoundException) { tryStart(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$packageName"))) }
}

/** Header for the About pages: a round back button and a centered title. */
@Composable
private fun PageHeader(title: String, nav: NavController) {
    Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        CircleBtn("Go back", { nav.popBackStack() }) { Ic("back", 22.dp, Sb.ink) }
        Text(title, Modifier.weight(1f), textAlign = TextAlign.Center, fontFamily = headFont(), fontWeight = FontWeight.Medium, fontSize = 20.sp,
            letterSpacing = (-0.01).em, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.size(44.dp))
    }
}

/** A small section label above a card, the same as on the Settings page. */
@Composable
private fun SectionLabel(text: String, first: Boolean) {
    Text(text, Modifier.padding(start = 24.dp, end = 24.dp, top = if (first) 22.dp else 26.dp, bottom = 8.dp), fontSize = 15.sp, fontWeight = FontWeight.Medium, color = Sb.mute)
}

/** The big "Scrapbook" with a blinking accent caret in front, sized to fit the screen (60sp on a normal phone). */
@Composable
private fun CaretWordmark() {
    val blink = rememberInfiniteTransition(label = "caret")
    // A hard on/off blink, not a fade: visible for the first half of each 1.1 seconds, hidden for the second.
    val caret by blink.animateFloat(
        initialValue = 1f, targetValue = 0f, label = "caretAlpha",
        animationSpec = infiniteRepeatable(keyframes { durationMillis = 1100; 1f at 0; 1f at 549; 0f at 550 }, RepeatMode.Restart))
    BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal = 24.dp), Alignment.Center) {
        val size = (maxWidth.value / 5.4f).coerceAtMost(60f)
        val u = with(LocalDensity.current) { size.sp.toDp() }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(u * 0.08f).height(u * 0.95f).clip(RoundedCornerShape(u * 0.04f)).alpha(caret).background(LocalSb.current.acc))
            Spacer(Modifier.width(u * 0.13f))
            Text("Scrapbook", fontFamily = headFont(), fontWeight = FontWeight.Medium, fontSize = size.sp, lineHeight = (size * 1.1f).sp,
                letterSpacing = (-0.02).em, maxLines = 1, softWrap = false)
        }
    }
}

/** One app in the family card: its name, a line about it, and the arrow that says it opens outside the app. */
@Composable
private fun FamilyRow(name: String, blurb: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().height(68.dp).tap("Open $name", onClick).padding(start = 18.dp, end = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(name, fontFamily = headFont(), fontWeight = FontWeight.Medium, fontSize = 20.sp, letterSpacing = (-0.02).em)
            Text(blurb, fontSize = 13.sp, color = Sb.mute)
        }
        Spacer(Modifier.width(12.dp))
        Ic("external", 18.dp, Sb.mute)
    }
}

@Composable
fun AboutScreen(nav: NavController) {
    val t = LocalSb.current; val ctx = LocalContext.current
    val version = remember { runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }.getOrNull().orEmpty() }
    Scaffold(containerColor = t.paper) { pad ->
        Column(Modifier.fillMaxSize().padding(pad)) {
            PageHeader("About", nav)
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                Spacer(Modifier.height(52.dp))
                CaretWordmark()
                Text("Quick notes, tidy boxes.", Modifier.fillMaxWidth().padding(top = 12.dp), textAlign = TextAlign.Center, fontSize = 16.sp, color = Sb.ink.copy(alpha = .85f))
                if (version.isNotEmpty()) Text("Version $version", Modifier.fillMaxWidth().padding(top = 6.dp), textAlign = TextAlign.Center, fontSize = 14.sp, color = Sb.mute)
                Spacer(Modifier.height(40.dp))
                GroupCard {
                    NavRow("What's new") { nav.navigate("whatsnew") }
                    RowDivider()
                    NavRow("Send feedback") { ctx.sendFeedback(version) }
                    RowDivider()
                    NavRow("Rate Scrapbook", icon = "external") { ctx.rateApp() }
                    RowDivider()
                    NavRow("Privacy policy", icon = "external") { ctx.openLink(AboutLinks.PRIVACY_URL) }
                    RowDivider()
                    NavRow("Open-source licenses") { nav.navigate("licenses") }
                }
                Spacer(Modifier.height(16.dp))
                // Scrapbook is one of three apps. This card points to the other two.
                GroupCard {
                    Text("PART OF THE FAMILY", Modifier.padding(start = 18.dp, end = 18.dp, top = 16.dp), fontSize = 12.sp, fontWeight = FontWeight.Medium, letterSpacing = (0.04).em, color = Sb.mute)
                    FamilyRow("paperback", "Your book reader") { ctx.openLink(AboutLinks.PAPERBACK_URL) }
                    RowDivider()
                    FamilyRow("binder", "Track what you watch, play and read") { ctx.openLink(AboutLinks.BINDER_URL) }
                }
                Spacer(Modifier.height(32.dp))
            }
        }
    }
}

private class Release(val title: String, val notes: List<String>)

/** Newest first. Add a Release at the top when you ship something new. */
private val RELEASES = listOf(
    Release("Latest", listOf(
        "Quotes and notes sent from the Paperback app land in a \"Paperback\" box, and the \"Open in Paperback\" link in them opens the book at that passage.",
        "A new Settings page with theme, background, accent color, header size, list options, boxes and backup in one place.",
        "White is now the default background. Soft grey is still there if you prefer it.",
        "Seven box styles: Tabbed, Paper stack, Tinted tiles, Folder tabs, Chips, List rows and Notebook covers.",
        "A single round menu button: tap it for the menu, hold it to pop out the + button.",
        "A redesigned editor header, with the box and tags under the title and a cleaner 3-dot menu.",
        "Scrapbook now opens in the box you were last in, instead of the main page. You can turn this off in Settings.",
        "The search bar inside a box can now be hidden, from the box's 3-dot menu or in Settings.",
        "A tidier menu, and this About page.",
    )),
)

@Composable
fun WhatsNewScreen(nav: NavController) {
    val t = LocalSb.current
    Scaffold(containerColor = t.paper) { pad ->
        Column(Modifier.fillMaxSize().padding(pad)) {
            PageHeader("What's new", nav)
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                RELEASES.forEachIndexed { i, r ->
                    SectionLabel(r.title, i == 0)
                    GroupCard {
                        Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            r.notes.forEach { n ->
                                Row(verticalAlignment = Alignment.Top) {
                                    Box(Modifier.padding(top = 8.dp).size(6.dp).clip(CircleShape).background(t.acc))
                                    Spacer(Modifier.width(12.dp))
                                    Text(n, fontSize = 15.sp, lineHeight = 22.sp)
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(32.dp))
            }
        }
    }
}

private class Library(val name: String, val license: String, val note: String? = null)

/** What the app is built with (see app/build.gradle.kts). Add a line here when you add a library. */
private val LIBRARIES = listOf(
    Library("Jetpack Compose and AndroidX", "Apache License 2.0", "Compose, Material 3, Activity, Navigation, Lifecycle, DataStore, Room, Core KTX and Glance"),
    Library("Coil", "Apache License 2.0", "Image loading"),
    Library("Kotlin and kotlinx.coroutines", "Apache License 2.0"),
)

@Composable
fun LicensesScreen(nav: NavController) {
    val t = LocalSb.current
    Scaffold(containerColor = t.paper) { pad ->
        Column(Modifier.fillMaxSize().padding(pad)) {
            PageHeader("Open-source licenses", nav)
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                SectionLabel("Built with", true)
                GroupCard {
                    LIBRARIES.forEachIndexed { i, lib ->
                        if (i > 0) RowDivider()
                        Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp)) {
                            Text(lib.name, fontSize = 16.sp)
                            Text(lib.license, Modifier.padding(top = 2.dp), fontSize = 14.sp, color = Sb.mute)
                            if (lib.note != null) Text(lib.note, Modifier.padding(top = 2.dp), fontSize = 13.sp, color = Sb.mute)
                        }
                    }
                }
                Text("The full Apache License 2.0 text is at apache.org/licenses/LICENSE-2.0.", Modifier.padding(start = 24.dp, end = 24.dp, top = 12.dp), fontSize = 13.sp, color = Sb.mute)
                Spacer(Modifier.height(32.dp))
            }
        }
    }
}
