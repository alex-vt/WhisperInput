package com.whispercppdemo.whisper

import android.content.res.AssetManager
import android.os.Build
import android.util.Log
import java.io.File
import java.io.InputStream

class WhisperLib {
    companion object {
        private const val LOG_TAG = "LibWhisper"

        private fun isArmEabiV7a(): Boolean {
            return Build.SUPPORTED_ABIS[0].equals("armeabi-v7a")
        }

        private fun cpuInfo(): String? {
            return try {
                File("/proc/cpuinfo").inputStream().bufferedReader().use {
                    it.readText()
                }
            } catch (e: Exception) {
                Log.w(LOG_TAG, "Couldn't read /proc/cpuinfo", e)
                null
            }
        }

        init {
            Log.d(LOG_TAG, "Primary ABI: ${Build.SUPPORTED_ABIS[0]}")
            var loadVfpv4 = false
            if (isArmEabiV7a()) {
                // armeabi-v7a needs runtime detection support
                val cpuInfo = cpuInfo()
                cpuInfo?.let {
                    Log.d(LOG_TAG, "CPU info: $cpuInfo")
                    if (cpuInfo.contains("vfpv4")) {
                        Log.d(LOG_TAG, "CPU supports vfpv4")
                        loadVfpv4 = true
                    }
                }
            }

            if (loadVfpv4) {
                Log.d(LOG_TAG, "Loading libwhisper_vfpv4.so")
                System.loadLibrary("whisper_vfpv4")
            } else {
                Log.d(LOG_TAG, "Loading libwhisper.so")
                System.loadLibrary("whisper")
            }
        }

        // JNI methods
        external fun initContextFromInputStream(inputStream: InputStream): Long
        external fun initContextFromAsset(assetManager: AssetManager, assetPath: String): Long
        external fun initContext(modelPath: String): Long
        external fun freeContext(contextPtr: Long)
        external fun fullTranscribe(contextPtr: Long, language: String, numThreads: Int, audioData: FloatArray)
        external fun getTextSegmentCount(contextPtr: Long): Int
        external fun getTextSegment(contextPtr: Long, index: Int): String
        external fun getSystemInfo(): String
        external fun benchMemcpy(n_threads: Int)
        external fun benchGgmlMulMat(n_threads: Int)
    }
}
