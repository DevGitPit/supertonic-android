package com.brahmadeo.supertonic.tts

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.setContent
import androidx.core.content.FileProvider
import com.brahmadeo.supertonic.tts.ui.SavedAudioScreen
import com.brahmadeo.supertonic.tts.ui.theme.SupertonicTheme
import java.io.File

class SavedAudioActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            SupertonicTheme {
                SavedAudioScreen(
                    onBackClick = { finish() },
                    onPlayAudio = { file -> playAudio(file) },
                    onShareAudio = { files -> shareAudio(files) }
                )
            }
        }
    }

    private fun playAudio(file: File) {
        try {
            val uri = FileProvider.getUriForFile(this, "${packageName}.fileprovider", file)
            val intent = Intent(Intent.ACTION_VIEW)
            intent.setDataAndType(uri, "audio/*")
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

            val chooser = Intent.createChooser(intent, "Play with...")
            startActivity(chooser)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun shareAudio(files: List<File>) {
        try {
            if (files.isEmpty()) return

            val intent = if (files.size == 1) {
                Intent(Intent.ACTION_SEND).apply {
                    type = "audio/*"
                    val uri = FileProvider.getUriForFile(this@SavedAudioActivity, "${packageName}.fileprovider", files[0])
                    putExtra(Intent.EXTRA_STREAM, uri)
                    clipData = android.content.ClipData.newRawUri("", uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            } else {
                Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                    type = "audio/*"
                    val uris = ArrayList(files.map {
                        FileProvider.getUriForFile(this@SavedAudioActivity, "${packageName}.fileprovider", it)
                    })
                    putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
                    if (uris.isNotEmpty()) {
                        val clip = android.content.ClipData.newRawUri("", uris[0])
                        for (i in 1 until uris.size) {
                            clip.addItem(android.content.ClipData.Item(uris[i]))
                        }
                        clipData = clip
                    }
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            }

            val chooser = Intent.createChooser(intent, if (files.size == 1) getString(R.string.share_audio_file) else getString(R.string.share_audio_files))
            startActivity(chooser)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}