package app.scrapbook

import android.app.Application
import android.os.Build
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter

/** Saves the stack trace of any crash to a file, so the next launch can show it and let you share it. */
class ScrapbookApp : Application() {
    override fun onCreate() {
        super.onCreate()
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching {
                val trace = StringWriter().also { error.printStackTrace(PrintWriter(it)) }.toString()
                val version = runCatching { packageManager.getPackageInfo(packageName, 0).versionName }.getOrNull()
                File(filesDir, "crash.txt").writeText(
                    "Scrapbook $version crash\nDevice: ${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})\nThread: ${thread.name}\n\n$trace".take(20_000))
            }
            previous?.uncaughtException(thread, error)
        }
    }
}
