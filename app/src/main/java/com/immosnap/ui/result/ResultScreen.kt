package com.immosnap.ui.result

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.width
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
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = if (highlighted) CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        ) else CardDefaults.cardColors()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            result.candidate.thumbnailUrl?.let { url ->
                AsyncImage(
                    model = url,
                    contentDescription = "Listing photo",
                    modifier = Modifier.fillMaxWidth().height(160.dp)
                )
                Spacer(Modifier.height(8.dp))
            }
            Text(
                result.candidate.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(result.candidate.snippet, style = MaterialTheme.typography.bodySmall)
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
