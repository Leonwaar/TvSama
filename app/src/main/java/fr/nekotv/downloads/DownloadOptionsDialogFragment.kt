package fr.nekotv.downloads

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.DialogFragment
import fr.nekotv.VideoSource

class DownloadOptionsDialogFragment(
    private val sources: List<VideoSource>,
    private val onConfirm: (VideoSource) -> Unit
) : DialogFragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setContent {
                DownloadOptionsDialog(
                    sources = sources,
                    onConfirm = { source ->
                        onConfirm(source)
                        dismiss()
                    },
                    onDismiss = {
                        dismiss()
                    }
                )
            }
        }
    }

    companion object {
        fun newInstance(sources: List<VideoSource>, onConfirm: (VideoSource) -> Unit): DownloadOptionsDialogFragment {
            // Note: Passing a lambda in a DialogFragment is tricky with state restoration.
            // In a production app, we'd use a ViewModel or a shared state.
            return DownloadOptionsDialogFragment(sources, onConfirm)
        }
    }
}
