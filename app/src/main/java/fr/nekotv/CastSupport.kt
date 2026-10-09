package fr.nekotv

import android.content.Context
import android.app.UiModeManager
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.mediarouter.app.MediaRouteButton
import com.google.android.gms.cast.CastMediaControlIntent
import com.google.android.gms.cast.framework.CastButtonFactory
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastOptions
import com.google.android.gms.cast.framework.OptionsProvider
import com.google.android.gms.cast.framework.SessionProvider
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import java.util.UUID
import androidx.compose.runtime.collectAsState
import kotlinx.coroutines.launch

class TvSamaCastOptions : OptionsProvider {
    override fun getCastOptions(context: Context): CastOptions = CastOptions.Builder()
        .setReceiverApplicationId(CastMediaControlIntent.DEFAULT_MEDIA_RECEIVER_APPLICATION_ID)
        .build()
    override fun getAdditionalSessionProviders(context: Context): List<SessionProvider>? = null
}

internal fun Context.isTelevision(): Boolean =
    (getSystemService(Context.UI_MODE_SERVICE) as UiModeManager).currentModeType == Configuration.UI_MODE_TYPE_TELEVISION

internal fun castContextOrNull(context: Context): CastContext? =
    if (context.isTelevision()) null else runCatching { CastContext.getSharedInstance(context) }.getOrNull()

/** The remote icon starts phone/TV pairing by QR, then offers the native Cast receiver chooser. */
@Composable
fun CastRouteButton(modifier: Modifier = Modifier, onDialogVisibilityChange: (Boolean) -> Unit = {}) {
    val context = LocalContext.current
    var showPairing by remember { mutableStateOf(false) }
    IconAction(R.drawable.tvsama_remote_logo, "Associer le téléphone à la télévision par QR code",
        modifier = modifier, tint = Color.Unspecified, iconSize = 34.dp) {
        showPairing = true; onDialogVisibilityChange(true)
    }
    androidx.compose.runtime.DisposableEffect(Unit) { onDispose { onDialogVisibilityChange(false) } }
    if (showPairing) PairingDialog(context) { showPairing = false; onDialogVisibilityChange(false) }
}

@Composable
private fun PairingDialog(context: Context, onDismiss: () -> Unit) {
    var scanMessage by remember { mutableStateOf("") }
    var payload by remember { mutableStateOf("") }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val paired by RemoteLink.target.collectAsState()
    androidx.compose.runtime.LaunchedEffect(Unit) {
        try { payload = RemoteLink.start(context) }
        catch (e: Exception) { scanMessage = e.message.orEmpty() }
    }
    val scanner = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()) { result ->
        val raw = result.data?.getStringExtra(com.streamflixreborn.streamflix.activities.tools.QrScannerActivity.EXTRA_QR_VALUE)
        if (result.resultCode == android.app.Activity.RESULT_OK && raw != null) scope.launch {
            try { RemoteLink.pair(context, raw); scanMessage = "Télévision liée : vos recherches et lectures sont envoyées à cet écran." }
            catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (e: Exception) { scanMessage = "Association impossible : ${e.message}" }
        }
    }
    val qr = remember(payload) { if (payload.isBlank()) null else qrBitmap(payload, 360) }
    AlertDialog(onDismissRequest = onDismiss, containerColor = Color(0xFF111111),
        title = { Text("Associer la télécommande", color = Color.White) },
        text = { Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Scannez ce code avec l’application TvSama sur votre téléphone. Les deux appareils doivent être sur le même Wi‑Fi.", color = Color.LightGray)
            if (qr != null) AndroidView(factory = { android.widget.ImageView(it).apply { setImageBitmap(qr); contentDescription = "QR code de liaison TvSama" } }, modifier = Modifier.size(220.dp))
            if (context.packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_CAMERA_ANY)) Action("Scanner l’écran avec la caméra") {
                scanner.launch(android.content.Intent(context, com.streamflixreborn.streamflix.activities.tools.QrScannerActivity::class.java))
            }
            if (scanMessage.isNotBlank()) Text(scanMessage, color = Accent)
            if (paired != null) Action("Délier la télévision ($paired)") { RemoteLink.disconnect(context) }
            Text("Gardez TvSama ouvert sur l’écran associé.", color = Color.LightGray, fontSize = 12.sp)
        } },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Terminé") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Fermer", color = Color.White) } })
}

private fun qrBitmap(value: String, size: Int): Bitmap {
    val matrix = QRCodeWriter().encode(value, BarcodeFormat.QR_CODE, size, size)
    return Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888).also { bitmap ->
        val pixels = IntArray(size * size) { i -> if (matrix[i % size, i / size]) AndroidColor.BLACK else AndroidColor.WHITE }
        bitmap.setPixels(pixels, 0, size, 0, 0, size, size)
    }
}
