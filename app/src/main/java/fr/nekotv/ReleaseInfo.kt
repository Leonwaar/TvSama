package fr.nekotv

import androidx.compose.material3.*
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.Composable
import fr.nekotv.Anime
import fr.nekotv.ReleaseInfoFormatter
import fr.nekotv.Muted
import androidx.compose.foundation.shape.RoundedCornerShape

@Composable
fun ReleaseInfo(anime: Anime, variant: String = "full", modifier: Modifier = Modifier) {
    val text = ReleaseInfoFormatter.formatReleaseInfo(anime, variant)
    
    if (variant == "compact") {
        Surface(
            color = Muted.copy(alpha = 0.8f),
            shape = RoundedCornerShape(4.dp),
            modifier = modifier
        ) {
            Text(
                text = text,
                color = Color.White,
                fontSize = 11.sp,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
            )
        }
    } else {
        Text(
            text = text,
            color = Muted,
            fontSize = 14.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = modifier
        )
    }
}
