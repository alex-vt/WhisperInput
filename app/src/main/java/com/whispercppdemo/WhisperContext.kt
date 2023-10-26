package com.whispercppdemo

import android.content.res.AssetManager
import com.whispercppdemo.whisper.WhisperLib
import kotlinx.coroutines.*
import java.io.InputStream
import java.util.concurrent.Executors


class WhisperContext private constructor(private var ptr: Long) {
    // Meet Whisper C++ constraint: Don't access from more than one thread at a time.
    private val scope: CoroutineScope = CoroutineScope(
        Executors.newSingleThreadExecutor().asCoroutineDispatcher()
    )

    suspend fun transcribeData(language: String, data: FloatArray): String = withContext(scope.coroutineContext) {
        require(ptr != 0L)
        WhisperLib.fullTranscribe(ptr, language, 6, data)
        val textCount = WhisperLib.getTextSegmentCount(ptr)
        return@withContext buildString {
            for (i in 0 until textCount) {
                append(WhisperLib.getTextSegment(ptr, i))
            }
        }
    }

    suspend fun release() = withContext(scope.coroutineContext) {
        if (ptr != 0L) {
            WhisperLib.freeContext(ptr)
            ptr = 0
        }
    }

    protected fun finalize() {
        runBlocking {
            release()
        }
    }

    companion object {
        fun createContextFromAsset(assetManager: AssetManager, assetPath: String): WhisperContext {
            val ptr = WhisperLib.initContextFromAsset(assetManager, assetPath)

            if (ptr == 0L) {
                throw java.lang.RuntimeException("Couldn't create context from asset $assetPath")
            }
            return WhisperContext(ptr)
        }
    }
}

