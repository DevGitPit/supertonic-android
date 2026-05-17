package com.brahmadeo.supertonic.tts

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.net.Uri
import android.os.Bundle
import android.os.IBinder
import android.os.RemoteException
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.core.content.FileProvider
import com.brahmadeo.supertonic.tts.service.IPlaybackService
import com.brahmadeo.supertonic.tts.service.PlaybackService
import com.brahmadeo.supertonic.tts.ui.LexiconEditDialog
import com.brahmadeo.supertonic.tts.ui.LexiconScreen
import com.brahmadeo.supertonic.tts.ui.theme.SupertonicTheme
import com.brahmadeo.supertonic.tts.utils.LexiconItem
import com.brahmadeo.supertonic.tts.utils.LexiconManager
import com.brahmadeo.supertonic.tts.utils.AssetManager
import com.brahmadeo.supertonic.tts.utils.ModelVersion
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader

class LexiconActivity : ComponentActivity() {

    private val rulesState = mutableStateOf<List<LexiconItem>>(emptyList())
    private var playbackService: IPlaybackService? = null
    private var isBound = false

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            playbackService = IPlaybackService.Stub.asInterface(service)
            isBound = true
        }

        override fun onServiceDisconnected(arg0: ComponentName) {
            isBound = false
            playbackService = null
        }
    }

    private val importLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { performImport(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        // Load initial rules
        refreshRules()

        val intent = Intent(this, PlaybackService::class.java)
        bindService(intent, connection, BIND_AUTO_CREATE)

        setContent {
            SupertonicTheme {
                var showEditDialog by remember { mutableStateOf(false) }
                var editingItem by remember { mutableStateOf<LexiconItem?>(null) }

                if (showEditDialog) {
                    LexiconEditDialog(
                        item = editingItem,
                        onDismiss = { showEditDialog = false },
                        onSave = { term, replacement, ignoreCase, isRegex ->
                            saveRule(editingItem, term, replacement, ignoreCase, isRegex)
                            showEditDialog = false
                        },
                        onTest = { replacement ->
                            testPronunciation(replacement)
                        }
                    )
                }

                LexiconScreen(
                    rules = rulesState.value,
                    onBackClick = { finish() },
                    onImportClick = { importLauncher.launch("application/json") },
                    onExportClick = { performExport() },
                    onAddClick = {
                        editingItem = null
                        showEditDialog = true
                    },
                    onEditClick = { item ->
                        editingItem = item
                        showEditDialog = true
                    },
                    onDeleteClick = { item ->
                        deleteRule(item)
                    }
                )
            }
        }
    }

    private fun refreshRules() {
        rulesState.value = LexiconManager.load(this)
    }

    private fun saveRule(existingItem: LexiconItem?, term: String, replacement: String, ignoreCase: Boolean, isRegex: Boolean) {
        val currentRules = rulesState.value.toMutableList()

        if (existingItem != null) {
            val index = currentRules.indexOfFirst { it.id == existingItem.id }
            if (index != -1) {
                currentRules[index] = existingItem.copy(
                    term = term,
                    replacement = replacement,
                    ignoreCase = ignoreCase,
                    isRegex = isRegex
                )
            }
        } else {
            currentRules.add(LexiconItem(
                term = term,
                replacement = replacement,
                ignoreCase = ignoreCase,
                isRegex = isRegex
            ))
        }

        LexiconManager.save(this, currentRules)
        LexiconManager.reload(this)
        refreshRules()
    }

    private fun deleteRule(item: LexiconItem) {
        val currentRules = rulesState.value.toMutableList()
        currentRules.removeIf { it.id == item.id }
        LexiconManager.save(this, currentRules)
        LexiconManager.reload(this)
        refreshRules()
    }

    private fun performExport() {
        val rules = rulesState.value
        if (rules.isEmpty()) {
            Toast.makeText(this, getString(R.string.no_rules_export), Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val jsonArray = JSONArray()
            for (rule in rules) {
                val obj = JSONObject()
                obj.put("id", rule.id)
                obj.put("term", rule.term)
                obj.put("replacement", rule.replacement)
                obj.put("ignoreCase", rule.ignoreCase)
                obj.put("isRegex", rule.isRegex)
                jsonArray.put(obj)
            }

            val fileName = "supertonic_lexicon.json"
            val file = File(cacheDir, fileName)
            file.writeText(jsonArray.toString(2))

            val uri = FileProvider.getUriForFile(this, "${packageName}.fileprovider", file)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/json"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(intent, getString(R.string.export_chooser_title)))

        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, getString(R.string.export_failed_fmt, e.message), Toast.LENGTH_SHORT).show()
        }
    }

    private fun performImport(uri: Uri) {
        try {
            val inputStream = contentResolver.openInputStream(uri) ?: return
            val reader = BufferedReader(InputStreamReader(inputStream))
            val jsonString = reader.readText()
            reader.close()
            inputStream.close()

            val jsonArray = JSONArray(jsonString)
            val importedItems = mutableListOf<LexiconItem>()

            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                if (obj.has("term") && obj.has("replacement")) {
                    importedItems.add(LexiconItem(
                        id = obj.optString("id", java.util.UUID.randomUUID().toString()),
                        term = obj.getString("term"),
                        replacement = obj.getString("replacement"),
                        ignoreCase = obj.optBoolean("ignoreCase", true),
                        isRegex = obj.optBoolean("isRegex", false)
                    ))
                }
            }

            if (importedItems.isEmpty()) {
                Toast.makeText(this, getString(R.string.no_valid_rules), Toast.LENGTH_SHORT).show()
                return
            }

            var addedCount = 0
            var updatedCount = 0
            val currentRules = LexiconManager.load(this).toMutableList()

            for (imported in importedItems) {
                val existingIndex = currentRules.indexOfFirst { it.term == imported.term }
                if (existingIndex == -1) {
                    currentRules.add(imported)
                    addedCount++
                } else {
                    val existing = currentRules[existingIndex]
                    if (existing.replacement != imported.replacement || existing.ignoreCase != imported.ignoreCase) {
                        // Replace the item with updated values
                        currentRules[existingIndex] = existing.copy(
                            replacement = imported.replacement,
                            ignoreCase = imported.ignoreCase
                        )
                        updatedCount++
                    }
                }
            }

            if (addedCount > 0 || updatedCount > 0) {
                LexiconManager.save(this, currentRules)
                LexiconManager.reload(this)
                refreshRules()

                MaterialAlertDialogBuilder(this)
                    .setTitle(getString(R.string.import_complete_title))
                    .setMessage(getString(R.string.import_stats_fmt, addedCount, updatedCount))
                    .setPositiveButton(getString(R.string.ok), null)
                    .show()
            } else {
                Toast.makeText(this, getString(R.string.import_no_changes), Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, getString(R.string.import_error), Toast.LENGTH_LONG).show()
        }
    }

    private fun testPronunciation(text: String) {
        if (!isBound || playbackService == null) {
            Toast.makeText(this, getString(R.string.engine_error), Toast.LENGTH_SHORT).show()
            return
        }

        val prefs = getSharedPreferences("SupertonicPrefs", MODE_PRIVATE)
        val selectedLang = prefs.getString("selected_lang", "en") ?: "en"
        val modelVersion = ModelVersion.resolve(savedLang = selectedLang, isV3Ready = AssetManager.isV3Ready(this))
        val version = modelVersion.dirName

        val isReady = AssetManager.isReady(this, modelVersion)
        if (!isReady) {
            Toast.makeText(this, "Assets not ready. Please download them on the main screen.", Toast.LENGTH_LONG).show()
            return
        }

        val voiceFile = prefs.getString("selected_voice", "F3.json") ?: "F3.json"
        
        // Ensure we point to the correct versioned directory
        val stylePath = File(filesDir, "$version/voice_styles/$voiceFile").absolutePath
        val steps = prefs.getInt("diffusion_steps", 5)

        // Use higher steps (10) for test to ensure short words are audible and clear
        val testSteps = 10

        val cleanText = text.trim()
        if (cleanText.isEmpty()) return
        
        // Pad the word to increase reliability for the model
        val testMsg = getString(R.string.testing_pronunciation_fmt, cleanText)
        val finalText = "$testMsg."

        Toast.makeText(this, testMsg, Toast.LENGTH_SHORT).show()

        try {
            playbackService?.synthesizeAndPlay(finalText, selectedLang, stylePath, 1.0f, testSteps, 0)
        } catch (e: RemoteException) {
            e.printStackTrace()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isBound) {
            unbindService(connection)
            isBound = false
        }
    }
}