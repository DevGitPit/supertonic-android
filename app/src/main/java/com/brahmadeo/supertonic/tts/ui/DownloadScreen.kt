package com.brahmadeo.supertonic.tts.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.brahmadeo.supertonic.tts.ui.components.WavyLinearProgressIndicator

@Composable
fun DownloadScreen(
    status: String,
    progress: Float,
    version: String,
    downloadedBytes: Long = 0L,
    totalBytes: Long = 0L,
    error: String? = null,
    onRetry: () -> Unit = {}
) {
    val message = when (version) {
        "v2" -> "Downloading Multilingual Models (~255MB). This enables support for French, Spanish, Portuguese, and Korean. This specific download happens only once."
        "v3" -> "Downloading Multilingual Models (~380MB). This enables support for Japanese, Arabic, German, Hindi, Russian, and 21 other languages. This specific download happens only once."
        else -> "Downloading Standard English Models (~255MB). This is a one-time setup for English synthesis."
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = if (error != null) "Download Failed" else "Downloading Models",
                style = MaterialTheme.typography.headlineMedium,
                color = if (error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = error ?: message,
                style = MaterialTheme.typography.bodyMedium,
                color = if (error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            
            if (error == null) {
                Spacer(modifier = Modifier.height(32.dp))
                WavyLinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = status,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                val mbText = if (totalBytes > 0) {
                    val downloaded = downloadedBytes / (1024.0 * 1024.0)
                    val total = totalBytes / (1024.0 * 1024.0)
                    "%.1f / %.1f MB".format(downloaded, total)
                } else if (downloadedBytes > 0) {
                    "%.1f MB".format(downloadedBytes / (1024.0 * 1024.0))
                } else {
                    ""
                }
                if (mbText.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = mbText,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                Spacer(modifier = Modifier.height(32.dp))
                Button(
                    onClick = onRetry,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text("Retry Download")
                }
            }
        }
    }
}
