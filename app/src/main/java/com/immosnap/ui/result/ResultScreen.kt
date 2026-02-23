package com.immosnap.ui.result

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.immosnap.matching.MatchResult

@Composable
fun ResultScreen(
    results: List<MatchResult>,
    onRetry: () -> Unit
) {
    val context = LocalContext.current

    if (results.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("No listings found", style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(16.dp))
                Button(onClick = onRetry) { Text("Try Again") }
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
