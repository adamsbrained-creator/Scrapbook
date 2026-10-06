package app.scrapbook

import android.content.Context
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

/** Home-screen widget: one older note from the archive, a different one each day. */
class RediscoverWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val old = Db.get(context).dao().oldest()
        val item = if (old.isEmpty()) null else old[((System.currentTimeMillis() / 86_400_000L) % old.size).toInt()]
        provideContent {
            Column(GlanceModifier.fillMaxSize().background(Color(0xFFF8F6F0)).cornerRadius(20.dp).padding(14.dp)
                .clickable(actionStartActivity<MainActivity>())) {
                Text("From your archive", style = TextStyle(color = ColorProvider(Color(0xFF686862)), fontSize = 11.sp))
                Text(item?.title ?: "Nothing saved yet", style = TextStyle(color = ColorProvider(Color(0xFF24272A)), fontSize = 16.sp, fontWeight = FontWeight.Bold))
                Text(item?.body.orEmpty().take(110), style = TextStyle(color = ColorProvider(Color(0xFF24272A)), fontSize = 12.sp))
            }
        }
    }
}

class RediscoverReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = RediscoverWidget()
}
