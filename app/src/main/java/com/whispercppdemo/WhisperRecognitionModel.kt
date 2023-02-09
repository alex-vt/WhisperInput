package com.whispercppdemo

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.io.File


class WhisperRecognitionModel(
    private val context: Context
) : ViewModel() {
    var canTranscribe = false
    var dataLog = ""

    private val modelsPath = File(context.filesDir, "models")
    private var whisperContext: WhisperContext? = null

    init {
        viewModelScope.launch {
            loadData()
        }
    }

    private suspend fun loadData() {
        printMessage("Loading data...\n")
        try {
            copyAssets()
            loadBaseModel()
            canTranscribe = true
        } catch (e: Exception) {
            Log.w(LOG_TAG, e)
            printMessage("${e.localizedMessage}\n")
        }
    }

    private suspend fun printMessage(msg: String) = withContext(Dispatchers.Main) {
        dataLog += msg
        println(msg)
    }

    private suspend fun copyAssets() = withContext(Dispatchers.IO) {
        modelsPath.mkdirs()
        printMessage("All data copied to working directory.\n")
    }

    private suspend fun loadBaseModel() = withContext(Dispatchers.IO) {
        printMessage("Loading model...\n")
        val models = context.assets.list("models/")
        if (models != null) {
            whisperContext =
                WhisperContext.createContextFromAsset(context.assets, "models/" + models[0])
            printMessage("Loaded model ${models[0]}.\n")
        }
    }

    fun transcribe(bytes: ByteArray, onResultListener: (String) -> Unit) = viewModelScope.launch {
        if (!canTranscribe) {
            onResultListener("[busy!]")
        }
        printMessage("Reading wave bytes...\n")
        val data = decodeBytes(bytes)
        printMessage("Transcribing data...\n")
        val text = whisperContext?.transcribeData(data)
        printMessage("Done: $text\n")
        text?.let { onResultListener(it) } ?: onResultListener("[no test!]")
    }

    override fun onCleared() {
        runBlocking {
            whisperContext?.release()
            whisperContext = null
            //stopPlayback()
        }
    }

    companion object {
        private const val LOG_TAG = "WhisperRecognitionModel"
    }
}

