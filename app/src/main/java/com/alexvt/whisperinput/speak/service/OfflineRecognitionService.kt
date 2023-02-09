package com.alexvt.whisperinput.speak.service

import android.Manifest.permission.RECORD_AUDIO
import android.content.Intent
import android.os.*
import android.speech.RecognizerIntent
import android.util.Pair
import androidx.annotation.RequiresPermission
import com.whispercppdemo.WhisperRecognitionModel
import com.alexvt.whisperinput.speak.ChunkedWebRecSessionBuilder
import com.alexvt.whisperinput.speak.Log
import com.alexvt.whisperinput.R
import com.alexvt.whisperinput.speak.utils.QueryUtils
import ee.ioc.phon.android.speechutils.AudioRecorder
import ee.ioc.phon.android.speechutils.Extras
import ee.ioc.phon.android.speechutils.service.AbstractRecognitionService
import ee.ioc.phon.android.speechutils.utils.PreferenceUtils
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.IOException

/**
 * Whisper.cpp based recognition
 */
class OfflineRecognitionService : AbstractRecognitionService() {

    @RequiresPermission(RECORD_AUDIO)
    @Throws(IOException::class)
    override fun configure(recognizerIntent: Intent?) {
        val bundle: Bundle = extras
        val builder = ChunkedWebRecSessionBuilder(this, bundle, null)
        val list: MutableList<Pair<String, String>> =
            QueryUtils.getQueryParams(recognizerIntent, builder)
        list.add(Pair("content-type", audioRecorder.contentType))

        // Extra overrides the preferences
        var obj = bundle.get(Extras.EXTRA_UNLIMITED_DURATION)
        if (obj == null) {
            obj = bundle.get(Extras.EXTRA_DICTATION_MODE)
        }
        val isUnlimitedDuration = if (obj == null) {
            !PreferenceUtils.getPrefBoolean(
                sharedPreferences, resources,
                R.string.keyWsAutoStopAfterPause, R.bool.defaultWsAutoStopAfterPause
            )
        } else {
            obj as Boolean
        }
        val isPartialResults = bundle.getBoolean(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
        if (whisperRecognitionModel == null) {
            whisperRecognitionModel = WhisperRecognitionModel(application)
            Log.i("WHISPER configured, once per app lifecycle")
        }
        // todo reimplement ad-hoc whisper.cpp integration
        GlobalScope.launch {
            Log.i("WHISPER on standby")
            while (recorder?.state != AudioRecorder.State.RECORDING) {
                delay(100)
            }
            Log.i("WHISPER recording started")
            var recordingBytes = byteArrayOf()
            while (recorder?.state == AudioRecorder.State.RECORDING) {
                recordingBytes += recorder.consumeRecordingAndTruncate()
                delay(100)
            }
            onEndOfSpeech()
            Log.i("WHISPER recording stopped, size: ${recordingBytes.size}, transcribing")
            whisperRecognitionModel?.transcribe(recordingBytes) { transcription ->
                Log.i("WHISPER transcription obtained")
                val trimmedTranscription = transcription.trim('.', ' ')
                onResults(toResultsBundle(arrayListOf(trimmedTranscription), true))
            } ?: Log.e("WHISPER no whisper model")
        }
    }

    override fun connect() {
    }

    override fun disconnect() {
    }

    override fun getEncoderType(): String = PreferenceUtils.getPrefString(
        sharedPreferences, resources,
        R.string.keyImeAudioFormat, R.string.defaultAudioFormat
    )

    override fun isAudioCues(): Boolean = PreferenceUtils.getPrefBoolean(
        sharedPreferences,
        resources,
        R.string.keyImeAudioCues,
        R.bool.defaultImeAudioCues
    )

    override fun getAutoStopAfterMillis(): Int {
        //return 1000 * PreferenceUtils.getPrefInt(getSharedPreferences(), getResources(),
        //        R.string.keyAutoStopAfterTime, R.string.defaultAutoStopAfterTime);
        return 1000 *
                sharedPreferences.getString(
                    getString(R.string.keyAutoStopAfterTime),
                    getString(R.string.defaultAutoStopAfterTime)
                )!!.toInt()
    }

    override fun isAutoStopAfterPause(): Boolean {
        // If the caller does not specify this extra, then we set it based on the settings.
        // TODO: in general, we could have 3-valued settings: true, false, use caller
        return if (extras.containsKey(Extras.EXTRA_UNLIMITED_DURATION)) {
            !extras.getBoolean(Extras.EXTRA_UNLIMITED_DURATION)
        } else PreferenceUtils.getPrefBoolean(
            sharedPreferences,
            resources, R.string.keyAutoStopAfterPause, R.bool.defaultAutoStopAfterPause
        )
    }

    companion object {

        private var whisperRecognitionModel: WhisperRecognitionModel? = null
    }
}