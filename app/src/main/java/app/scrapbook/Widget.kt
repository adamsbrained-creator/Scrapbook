package app.scrapbook

import android.content.Context
import android.content.Intent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Column
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider

/** Home-screen widget: one older note from the archive, a different one each day. Same colors as the app. */
class RediscoverWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val old = Db.get(context).dao().oldest()
        val item = if (old.isEmpty()) null else old[((System.currentTimeMillis() / 86_400_000L) % old.size).toInt()]
        val title = item?.title ?: "Nothing saved yet"
        val preview = item?.let { plainPreview(bodyWithoutTitle(it.title, it.body)).take(160) } ?: "Notes you save will come back here."
        val ink = ColorProvider(Color(0xFF1C1C1C)); val mute = ColorProvider(Color(0xFF727272)); val coral = ColorProvider(Color(0xFFF46C4E))
        provideContent {
            Column(GlanceModifier.fillMaxSize().background(Color(0xFFF4F4F4)).cornerRadius(24.dp).padding(16.dp)
                .clickable(actionStartActivity(Intent(context, MainActivity::class.java)))) {
                Text("scrapbook", style = TextStyle(color = coral, fontSize = 12.sp, fontWeight = FontWeight.Bold))
                Text("From your archive", style = TextStyle(color = mute, fontSize = 11.sp), modifier = GlanceModifier.padding(top = 2.dp))
                Text(title, style = TextStyle(color = ink, fontSize = 17.sp, fontWeight = FontWeight.Medium), maxLines = 2, modifier = GlanceModifier.padding(top = 8.dp))
                Text(preview, style = TextStyle(color = mute, fontSize = 13.sp), maxLines = 4, modifier = GlanceModifier.padding(top = 4.dp))
            }
        }
    }
}

class RediscoverReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = RediscoverWidget()
}
