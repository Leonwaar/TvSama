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
    val nextSource = mutableStateOf<VideoSource?>(null)
    @Volatile var advances = 0
    @Volatile var error = ""
    @Volatile var progress = 0L
    @Volatile var ended = false
    @Volatile var controlsVisible = true
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            source.value?.let { video ->
                TvSamaPlayer(source = video, title = "Local decoder fixture",
                    modifier = Modifier.fillMaxSize(), onBack = {},
                    onControlsVisibleChange = { controlsVisible = it },
                    onAutoNext = nextSource.value?.let { next -> ({ advances++; nextSource.value = null; source.value = next }) },
                    onProgress = { position, _ -> progress = position },
                    onError = { error = it }, onEnded = { ended = true })
            }
        }
    }
}
