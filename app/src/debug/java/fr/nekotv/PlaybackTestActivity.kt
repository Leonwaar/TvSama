package fr.nekotv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxSize

/** Instrumentation host, excluded from every release variant. */
class PlaybackTestActivity : ComponentActivity() {
    val source = mutableStateOf<VideoSource?>(null)
    @Volatile var error = ""
    @Volatile var progress = 0L
    @Volatile var ended = false
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            source.value?.let { video ->
                TvSamaPlayer(source = video, title = "Local decoder fixture",
                    modifier = Modifier.fillMaxSize(), onBack = {},
                    onProgress = { position, _ -> progress = position },
                    onError = { error = it }, onEnded = { ended = true })
            }
        }
    }
}
