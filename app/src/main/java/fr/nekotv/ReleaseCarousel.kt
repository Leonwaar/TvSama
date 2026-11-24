package fr.nekotv

import android.content.Context
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Done
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.temporal.ChronoUnit

internal fun releaseDateBadge(date: String?, today: LocalDate = LocalDate.now()): String? {
    val release = runCatching { LocalDate.parse(date.orEmpty()) }.getOrNull() ?: return null
    val days = ChronoUnit.DAYS.between(today, release).toInt()
    return when {
        days == 0 -> "Aujourd'hui"
        days == 1 -> "Demain"
        days in 2..7 -> "Dans $days jours"
        days > 7 -> "Bientôt"
        else -> "Au cinéma"
    }
}

@Composable internal fun ReleaseRowSkeleton() {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.width(180.dp).height(28.dp).background(Color.DarkGray))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            repeat(6) { Box(Modifier.width(150.dp).height(225.dp).background(Color.DarkGray)) }
        }
    }
}

/**
 * Expanded card overlay showing backdrop, synopsis, and action buttons.
 * Positioned absolutely to avoid shifting neighbor cards.
 */
@Composable
internal fun ExpandedReleaseCard(
    film: Anime,
    wide: Boolean,
    onOpen: (Anime) -> Unit,
    onDownload: (Anime) -> Unit,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val mockSource = VideoSource("Default", "https://example.com/mock", "FR", "1080p", "TMDB")
    val downloadState = remember { OfflineDownloads.stateFor(context, mockSource) ?: "" }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.9f))
            .clickable(onClick = onClose),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .padding(24.dp)
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface, MaterialTheme.shapes.large)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Artwork(
                    url = film.banner.ifBlank { film.poster },
                    title = "Backdrop ${film.title}",
                    modifier = Modifier.size(120.dp, 180.dp).clip(MaterialTheme.shapes.medium),
                    crop = true,
                    isBanner = true
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = film.displayTitle("fr"), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text(text = film.firstAired.orEmpty(), style = MaterialTheme.typography.bodyMedium, color = Color.LightGray)
                    if (film.rating > 0) {
                        Text(text = "Note : ${film.rating} / 10", style = MaterialTheme.typography.labelMedium, color = Color.LightGray)
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    TextButton(onClick = { onOpen(film) }) { Text("Voir la fiche") }
                    TextButton(
                        onClick = { onDownload(film) },
                        enabled = downloadState != "Terminé"
                    ) {
                        Text(if (downloadState.isNotEmpty()) downloadState else "Télécharger")
                    }
                    if (downloadState == "Terminé") {
                        IconButton(onClick = {}) { Icon(imageVector = Icons.Default.Done, contentDescription = "Téléchargé") }
                    }
                }
            }

            if (SynopsisResolver.resolveOverview(film).text.isNotBlank()) {
                Text(text = "Synopsis", style = MaterialTheme.typography.titleMedium, color = Color.White)
                Text(text = SynopsisResolver.resolveOverview(film).text, style = MaterialTheme.typography.bodyMedium, color = Color.LightGray)
            }

            if (film.genres.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    film.genres.take(5).forEach { genre ->
                        Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.secondaryContainer) {
                            Text(text = genre, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                        }
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onClose) { Text("Fermer") }
            }
        }
    }
}

@Composable
internal fun ReleaseCarousel(title: String, films: List<Anime>, wide: Boolean, onOpen: (Anime) -> Unit, onSeeAll: () -> Unit = {}) {
    if (films.isEmpty()) return
    val listState = rememberSaveable(title, saver = androidx.compose.foundation.lazy.LazyListState.Saver) { androidx.compose.foundation.lazy.LazyListState() }
    val scope = rememberCoroutineScope()
    var expandedFilm by remember { mutableStateOf<Anime?>(null) }
    val context = LocalContext.current

    Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth().animateContentSize().semantics { contentDescription = "Rangée $title, ${films.size} films" }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            TextButton(onClick = onSeeAll, modifier = Modifier.semantics { contentDescription = "Voir tout $title" }) { Text("Voir tout") }
            Spacer(Modifier.weight(1f))
            if (listState.canScrollBackward) IconButton(onClick = { scope.launch { listState.animateScrollToItem((listState.firstVisibleItemIndex - pageSize(wide)).coerceAtLeast(0)) } }, modifier = Modifier.semantics { contentDescription = "Défiler $title vers la gauche" }) { Text("‹") }
            if (listState.canScrollForward) IconButton(onClick = { scope.launch { listState.animateScrollToItem(listState.firstVisibleItemIndex + pageSize(wide)) } }, modifier = Modifier.semantics { contentDescription = "Défiler $title vers la droite" }) { Text("›") }
        }

        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val count = when { maxWidth >= 1200.dp -> 6f; maxWidth >= 700.dp -> 4f; else -> 2.5f }
            val gap = 12.dp; val cardWidth = (maxWidth - gap * (count - 1)) / count
            Box {
                LazyRow(state = listState, horizontalArrangement = Arrangement.spacedBy(gap), contentPadding = PaddingValues(horizontal = 2.dp)) {
                    items(films, key = { "${it.tmdbId ?: it.id}:$title" }) { film ->
                        Box(Modifier.width(cardWidth).focusable().clickable { expandedFilm = film }) {
                            PosterCard(film, Modifier.fillMaxWidth()) { onOpen(film) }
                            Column(Modifier.align(Alignment.TopStart).padding(8.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                releaseDateBadge(film.firstAired)?.let { badge ->
                                    Surface(color = Accent, shape = MaterialTheme.shapes.small) { Text(text = badge, color = Ink, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)) }
                                }
                                if (film.references.isNotEmpty()) Surface(color = Color(0xCC2E7D5B), shape = MaterialTheme.shapes.small) { Text(text = "Disponible", color = Color.White, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)) }
                            }
                        }
                    }
                }
                if (expandedFilm != null) {
                    ExpandedReleaseCard(
                        film = expandedFilm!!,
                        wide = wide,
                        onOpen = { expandedFilm = null; onOpen(it) },
                        onDownload = { expandedFilm = null; downloadMovie(context, it) },
                        onClose = { expandedFilm = null }
                    )
                }
            }
        }
    }
}

private fun downloadMovie(context: Context, film: Anime) {
    val dummySource = VideoSource(film.title, "https://example.com/mock", "FR", "1080p", "TMDB")
    OfflineDownloads.download(context, dummySource, film.title, film.tmdbId?.toString() ?: film.id, film.tag)
}

private fun pageSize(wide: Boolean) = if (wide) 6 else 4

@Composable
internal fun ReleaseGridPage(rows: List<CinemaRow>, wide: Boolean, onOpen: (Anime) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    var selectedGenre by rememberSaveable { mutableStateOf("Tous") }
    val films = rows.flatMap { it.films }.distinctBy { it.tmdbId ?: it.id }
    val genres = listOf("Tous") + films.flatMap { it.genres }.distinct().sorted()
    val filtered = films.filter { film -> (query.isBlank() || film.displayTitle("VF").contains(query, true)) && (selectedGenre == "Tous" || selectedGenre in film.genres) }
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Sorties cinéma", style = MaterialTheme.typography.headlineMedium)
        OutlinedTextField(value = query, onValueChange = { query = it }, modifier = Modifier.fillMaxWidth(), singleLine = true, label = { Text("Filtrer par titre") })
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) { items(genres) { genre -> FilterChip(selected = selectedGenre == genre, onClick = { selectedGenre = genre }, label = { Text(genre) }) } }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(if (wide) 170.dp else 125.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            gridItems(filtered, key = { it.tmdbId ?: it.id }) { film ->
                PosterCard(film, Modifier.fillMaxWidth()) { onOpen(film) }
            }
        }
    }
}