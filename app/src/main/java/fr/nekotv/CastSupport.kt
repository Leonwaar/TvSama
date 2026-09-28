package fr.nekotv

import android.content.Context
import android.app.UiModeManager
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
fun CastRouteButton(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var showPairing by remember { mutableStateOf(false) }
    Box(modifier.background(Color(0xFF171717), RoundedCornerShape(6.dp)).clickable { showPairing = true }
        .semantics { contentDescription = "Associer le téléphone à la télévision par QR code" }, contentAlignment = Alignment.Center) {
        androidx.compose.material3.Icon(painterResource(R.drawable.tvsama_remote_logo), contentDescription = "Télécommande TvSama", tint = Color.Unspecified, modifier = Modifier.size(34.dp))
    }
    if (showPairing) PairingDialog(context) { showPairing = false }
}

@Composable
private fun PairingDialog(context: Context, onDismiss: () -> Unit) {
    var scanMessage by remember { mutableStateOf("") }
    val scanner = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()) { result ->
        val raw = result.data?.getStringExtra(com.streamflixreborn.streamflix.activities.tools.QrScannerActivity.EXTRA_QR_VALUE)
        if (result.resultCode == android.app.Activity.RESULT_OK && raw != null) {
            val uri = android.net.Uri.parse(raw)
            val code = uri.getQueryParameter("token").orEmpty()
            if (uri.scheme == "tvsama" && uri.host == "pair" && code.matches(Regex("[a-zA-Z0-9]{12,64}"))) {
                context.getSharedPreferences("tvsama_settings", 0).edit().putString("paired_tv_token", code)
                    .putString("paired_tv_device", uri.getQueryParameter("device") ?: "TvSama").apply()
                scanMessage = "Code enregistré. Choisissez maintenant le téléviseur Cast."
            } else scanMessage = "Ce QR code n’est pas un code d’association TvSama."
        }
    }
    val token = remember { context.getSharedPreferences("tvsama_settings", Context.MODE_PRIVATE).let { prefs ->
        prefs.getString("pairing_token", null) ?: UUID.randomUUID().toString().replace("-", "").take(12).also { prefs.edit().putString("pairing_token", it).apply() }
    } }
    val payload = "tvsama://pair?device=TvSama&token=$token"
    val qr = remember(payload) { qrBitmap(payload, 720) }
    AlertDialog(onDismissRequest = onDismiss, containerColor = Color(0xFF111111),
        title = { Text("Associer la télécommande", color = Color.White) },
        text = { Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Scannez ce code avec l’application TvSama sur votre téléphone. Les deux appareils doivent être sur le même Wi‑Fi.", color = Color.LightGray)
            AndroidView(factory = { android.widget.ImageView(it).apply { setImageBitmap(qr); contentDescription = "QR code de liaison TvSama" } }, modifier = Modifier.size(220.dp))
            if (context.packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_CAMERA_ANY)) Action("Scanner l’écran avec la caméra") {
                scanner.launch(android.content.Intent(context, com.streamflixreborn.streamflix.activities.tools.QrScannerActivity::class.java))
            }
            if (scanMessage.isNotBlank()) Text(scanMessage, color = Accent)
            Text("Code ${token.chunked(4).joinToString(" ")}", color = Accent, fontSize = 16.sp)
            Text("Après l’association, choisissez le téléviseur dans le bouton Cast.", color = Color.LightGray, fontSize = 12.sp)
        } },
        confirmButton = {
            AndroidView(factory = { viewContext -> android.widget.FrameLayout(viewContext).apply {
                val route = MediaRouteButton(viewContext).apply { contentDescription = "Choisir le téléviseur Cast"; setAlwaysVisible(true); CastButtonFactory.setUpMediaRouteButton(viewContext, this) }
                addView(route, android.widget.FrameLayout.LayoutParams(190, 56))
            } }, modifier = Modifier.width(190.dp).height(56.dp))
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Fermer", color = Color.White) } })
}

private fun qrBitmap(value: String, size: Int): Bitmap {
    val matrix = QRCodeWriter().encode(value, BarcodeFormat.QR_CODE, size, size)
    return Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888).also { bitmap ->
        for (x in 0 until size) for (y in 0 until size) bitmap.setPixel(x, y, if (matrix[x, y]) AndroidColor.BLACK else AndroidColor.WHITE)
    }
}
