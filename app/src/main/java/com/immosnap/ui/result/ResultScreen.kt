package com.immosnap.ui.result

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import coil.compose.AsyncImage
import com.immosnap.matching.MatchResult
import com.immosnap.pipeline.DebugInfo

@Composable
fun ResultScreen(
    results: List<MatchResult>,
    onRetry: () -> Unit,
    debugInfo: DebugInfo? = null
) {
    val context = LocalContext.current

    if (results.isEmpty()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                Text("No listings found", style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(8.dp))
                Button(onClick = onRetry, modifier = Modifier.fillMaxWidth()) { Text("Try Again") }
            }
            if (debugInfo != null) {
                item {
                    Spacer(Modifier.height(8.dp))
                    Text("Debug Info", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(4.dp))
                    DebugCard(debugInfo)
                }
            }
        }
        return
    }

    val topResult = results.first()
    val showSingleResult = topResult.confidence >= 0.8f && results.size > 1

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (showSingleResult) {
            item {
                Text("Match Found!", style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(8.dp))
                MatchCard(topResult, highlighted = true) {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(topResult.candidate.url)))
                }
            }
        } else {
            item {
                Text(
                    "Select the correct listing:",
                    style = MaterialTheme.typography.headlineSmall
                )
            }
            items(results.take(3)) { result ->
                MatchCard(result, highlighted = false) {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(result.candidate.url)))
                }
            }
        }

        item {
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = onRetry, modifier = Modifier.fillMaxWidth()) {
                Text("Scan Another")
            }
        }
    }
}

@Composable
private fun MatchCard(
    result: MatchResult,
    highlighted: Boolean,
    onClick: () -> Unit
) {
    val images = result.candidate.imageUrls

    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = if (highlighted) CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        ) else CardDefaults.cardColors()
    ) {
        Column {
            if (images.isNotEmpty()) {
                Box {
                    val pagerState = rememberPagerState(pageCount = { images.size })
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.fillMaxWidth().height(200.dp)
                    ) { page ->
                        AsyncImage(
                            model = images[page],
                            contentDescription = "Listing photo ${page + 1}",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    // Page indicator dots
                    if (images.size > 1) {
                        Row(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(8.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            repeat(images.size) { index ->
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (index == pagerState.currentPage)
                                                MaterialTheme.colorScheme.primary
                                            else
                                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                                        )
                                )
                            }
                        }
                    }
                }
            }
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    result.candidate.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                if (result.candidate.snippet.isNotBlank()) {
                    Text(result.candidate.snippet, style = MaterialTheme.typography.bodySmall)
                }
                if (result.reasoning.isNotBlank() && result.reasoning != "Not evaluated") {
                    Text(
                        result.reasoning,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    "${result.candidate.source} • ${(result.confidence * 100).toInt()}% match",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (highlighted) {
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
                        Text("Open Listing")
                    }
                }
            }
        }
    }
}

@Composable
private fun DebugCard(info: DebugInfo) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            DebugRow("Location source", info.locationSource)
            DebugRow("GPS", if (info.lat != null && info.lng != null) "%.5f, %.5f".format(info.lat, info.lng) else "none")
            DebugRow("Address", info.address ?: "none")
            DebugRow("Agency", info.agencyName ?: "none")
            DebugRow("Ref#", info.refNumber ?: "none")
            DebugRow("Search query", info.searchQuery)
            info.searchError?.let {
                Spacer(Modifier.height(4.dp))
                Text("Search error: $it", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
            }
            if (info.searchResults.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text("Search results:", style = MaterialTheme.typography.labelSmall)
                info.searchResults.forEachIndexed { i, result ->
                    Text(
                        "${i + 1}. $result",
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            Text("OCR text:", style = MaterialTheme.typography.labelSmall)
            Text(
                info.ocrText.ifBlank { "(empty)" },
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

@Composable
private fun DebugRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            "$label: ",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.width(120.dp)
        )
        Text(value, style = MaterialTheme.typography.labelSmall)
    }
}
