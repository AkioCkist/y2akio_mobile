package com.example.y2akio_mobile

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import com.example.y2akio_mobile.settings.Strings

/**
 * "Y2Akio · Tải nền" share target. Shows no UI: pulls the link out of the shared text, queues a
 * background download with the share defaults from Settings, and finishes immediately so the user
 * stays in the app they shared from. Progress and completion appear as notifications.
 */
class ShareReceiverActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as Y2AkioApp
        val lang = app.settings.current.language

        val url = extractUrl(intent)
        if (url == null) {
            Toast.makeText(this, Strings.get("Job.NoLink", lang), Toast.LENGTH_SHORT).show()
        } else {
            app.downloads.enqueueShared(url)
            Toast.makeText(this, Strings.get("Job.BackgroundStarted", lang), Toast.LENGTH_SHORT).show()
        }
        finish()
        overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, 0, 0)
    }

    companion object {
        private val urlRegex = Regex("""https?://[^\s<>"']+""")

        /** First http(s) link in a SEND intent's text (apps often share "Title https://…"). */
        fun extractUrl(intent: Intent?): String? {
            if (intent?.action != Intent.ACTION_SEND) return null
            val text = intent.getStringExtra(Intent.EXTRA_TEXT) ?: intent.getStringExtra(Intent.EXTRA_SUBJECT) ?: return null
            return urlRegex.find(text)?.value?.trimEnd('.', ',', ')', ']', '!', '?')
        }
    }
}
