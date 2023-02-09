/*
 * Copyright 2015-2016, Institute of Cybernetics at Tallinn University of Technology
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package ee.ioc.phon.android.speechutils;

import android.annotation.TargetApi;
import android.media.AudioRecord;
import android.media.MediaCodec;
import android.media.MediaFormat;
import android.os.Build;

import androidx.annotation.RequiresPermission;

import java.nio.ByteBuffer;
import java.util.List;

import ee.ioc.phon.android.speechutils.utils.AudioUtils;

import static android.Manifest.permission.RECORD_AUDIO;

/**
 * Based on https://android.googlesource.com/platform/cts/+/jb-mr2-release/tests/tests/media/src/android/media/cts/EncoderTest.java
 * Requires Android v4.1 / API 16 / JELLY_BEAN
 */
public class EncodedAudioRecorder extends AbstractAudioRecorder {

    // TODO: support other formats than FLAC
    private static final String MIME = "audio/flac";
    private static final String CONTENT_TYPE = "audio/x-flac";
    //private static final String MIME = "audio/opus";
    //private static final String CONTENT_TYPE = "audio/x-opus";

    // Stop encoding if output buffer has not been available that many times.
    private static final int MAX_NUM_RETRIES_DEQUEUE_OUTPUT_BUFFER = 10;

    // Timeout in microseconds to dequeue a buffer
    // TODO: not sure what values are best here (behaves weird with negative values and very large values)
    private static final long DEQUEUE_INPUT_BUFFER_TIMEOUT = 10000;
    private static final long DEQUEUE_OUTPUT_BUFFER_TIMEOUT = 10000;

    // TODO: Use queue of byte[]
    private final byte[] mRecordingEnc;
    private int mRecordedEncLength = 0;
    private int mConsumedEncLength = 0;

    private int mNumBytesSubmitted = 0;
    private int mNumBytesDequeued = 0;

    @RequiresPermission(RECORD_AUDIO)
    public EncodedAudioRecorder(int audioSource, int sampleRate) {
        super(audioSource, sampleRate);
        try {
            int bufferSize = getBufferSize();
            createRecorder(audioSource, sampleRate, bufferSize);
            int framePeriod = bufferSize / (2 * RESOLUTION_IN_BYTES * CHANNELS);
            createBuffer(framePeriod);
            setState(State.READY);
        } catch (Exception e) {
            if (e.getMessage() == null) {
                handleError("Unknown error occurred while initializing recording");
            } else {
                handleError(e.getMessage());
            }
        }
        // TODO: replace 35 with the max length of the recording
        mRecordingEnc = new byte[RESOLUTION_IN_BYTES * CHANNELS * sampleRate * 35]; // 35 sec raw
    }

    @RequiresPermission(RECORD_AUDIO)
    public EncodedAudioRecorder(int sampleRate) {
        this(DEFAULT_AUDIO_SOURCE, sampleRate);
    }

    @RequiresPermission(RECORD_AUDIO)
    public EncodedAudioRecorder() {
        this(DEFAULT_AUDIO_SOURCE, DEFAULT_SAMPLE_RATE);
    }

    /**
     * TODO: the MIME should be configurable as the server might not support all formats
     * (returning "Your GStreamer installation is missing a plug-in.")
     * TODO: according to the server docs, for encoded data we do not need to specify the content type
     * such as "audio/x-flac", but it did not work without (nor with "audio/flac").
     */
    public String getContentType() {
        return CONTENT_TYPE;
    }

    public synchronized byte[] consumeRecordingEncAndTruncate() {
        int len = getConsumedEncLength();
        byte[] bytes = getCurrentRecordingEnc(len);
        setRecordedEncLength(0);
        setConsumedEncLength(0);
        return bytes;
    }

    /**
     * @return bytes that have been recorded and encoded since this method was last called
     */
    public synchronized byte[] consumeRecordingEnc() {
        byte[] bytes = getCurrentRecordingEnc(getConsumedEncLength());
        setConsumedEncLength(getRecordedEncLength());
        return bytes;
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    @RequiresPermission(RECORD_AUDIO)
    @Override
    protected void recorderLoop(AudioRecord speechRecord) {
        mNumBytesSubmitted = 0;
        mNumBytesDequeued = 0;
        MediaFormat format = MediaFormatFactory.createMediaFormat(MIME, getSampleRate());
        MediaCodec codec = getCodec(format);
        if (codec == null) {
            handleError("no codec found");
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                Log.i("Using codec: " + codec.getCanonicalName());
            }
            int status = recorderEncoderLoop(codec, speechRecord);
            if (Log.DEBUG) {
                AudioUtils.showMetrics(format, mNumBytesSubmitted, mNumBytesDequeued);
            }
            if (status < 0) {
                handleError("encoder error");
            }
        }
    }

    // TODO: we currently return the first suitable codec
    private MediaCodec getCodec(MediaFormat format) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            List<String> componentNames = AudioUtils.getEncoderNamesForType(format.getString(MediaFormat.KEY_MIME));
            for (String componentName : componentNames) {
                Log.i("component/format: " + componentName + "/" + format);
                MediaCodec codec = AudioUtils.createCodec(componentName, format);
                if (codec != null) {
                    return codec;
                }
            }
        }
        return null;
    }

    private int getConsumedEncLength() {
        return mConsumedEncLength;
    }

    private void setConsumedEncLength(int len) {
        mConsumedEncLength = len;
    }

    private void setRecordedEncLength(int len) {
        mRecordedEncLength = len;
    }

    private int getRecordedEncLength() {
        return mRecordedEncLength;
    }

    private void addEncoded(byte[] buffer) {
        int len = buffer.length;
        if (mRecordingEnc.length >= mRecordedEncLength + len) {
            System.arraycopy(buffer, 0, mRecordingEnc, mRecordedEncLength, len);
            mRecordedEncLength += len;
        } else {
            handleError("RecorderEnc buffer overflow: " + mRecordedEncLength);
        }
    }

    private byte[] getCurrentRecordingEnc(int startPos) {
        int len = getRecordedEncLength() - startPos;
        byte[] bytes = new byte[len];
        System.arraycopy(mRecordingEnc, startPos, bytes, 0, len);
        Log.i("Copied (enc) from pos: " + startPos + ", bytes: " + bytes.length);
        return bytes;
    }

    /**
     * Copy audio from the recorder into the encoder.
     */
    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    @RequiresPermission(RECORD_AUDIO)
    private int queueInputBuffer(MediaCodec codec, ByteBuffer[] inputBuffers, int index, AudioRecord speechRecord) {
        if (speechRecord == null || speechRecord.getRecordingState() != SpeechRecord.RECORDSTATE_RECORDING) {
            return -1;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            ByteBuffer inputBuffer = inputBuffers[index];
            inputBuffer.clear();
            int size = inputBuffer.limit();
            byte[] buffer = new byte[size];
            int status = read(speechRecord, buffer);
            if (status < 0) {
                handleError("status = " + status);
                return -1;
            }
            inputBuffer.put(buffer);
            codec.queueInputBuffer(index, 0, size, 0, 0);
            return size;
        }
        return -1;
    }

    /**
     * Save the encoded (output) buffer into the complete encoded recording.
     * TODO: copy directly (without the intermediate byte array)
     */
    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    private void dequeueOutputBuffer(MediaCodec codec, ByteBuffer[] outputBuffers, int index, MediaCodec.BufferInfo info) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            ByteBuffer buffer = outputBuffers[index];
            Log.i("size/remaining: " + info.size + "/" + buffer.remaining());
            if (info.size <= buffer.remaining()) {
                final byte[] bufferCopied = new byte[info.size];
                buffer.get(bufferCopied); // TODO: catch BufferUnderflow
                // TODO: do we need to clear?
                // on N5: always size == remaining(), clearing is not needed
                // on SGS2: remaining decreases until it becomes less than size, which results in BufferUnderflow
                // (but SGS2 records only zeros anyway)
                //buffer.clear();
                codec.releaseOutputBuffer(index, false);
                addEncoded(bufferCopied);
                if (Log.DEBUG) {
                    AudioUtils.showSomeBytes("out", bufferCopied);
                }
            } else {
                Log.e("size > remaining");
                codec.releaseOutputBuffer(index, false);
            }
        }
    }

    /**
     * Reads bytes from the given recorder and encodes them with the given encoder.
     * Uses the (deprecated) Synchronous Processing using Buffer Arrays.
     * <p/>
     * Encoders (or codecs that generate compressed data) will create and return the codec specific
     * data before any valid output buffer in output buffers marked with the codec-config flag.
     * Buffers containing codec-specific-data have no meaningful timestamps.
     */
    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    @RequiresPermission(RECORD_AUDIO)
    private int recorderEncoderLoop(MediaCodec codec, AudioRecord speechRecord) {
        int status = -1;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            codec.start();
            // Getting some buffers (e.g. 4 of each) to communicate with the codec
            ByteBuffer[] codecInputBuffers = codec.getInputBuffers();
            ByteBuffer[] codecOutputBuffers = codec.getOutputBuffers();
            Log.i("input buffers " + codecInputBuffers.length + "; output buffers: " + codecOutputBuffers.length);
            boolean doneSubmittingInput = false;
            int numDequeueOutputBufferTimeout = 0;
            int index;
            while (true) {
                if (!doneSubmittingInput) {
                    index = codec.dequeueInputBuffer(DEQUEUE_INPUT_BUFFER_TIMEOUT);
                    if (index >= 0) {
                        int size = queueInputBuffer(codec, codecInputBuffers, index, speechRecord);
                        if (size == -1) {
                            codec.queueInputBuffer(index, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                            Log.i("enc: in: EOS");
                            doneSubmittingInput = true;
                        } else {
                            Log.i("enc: in: " + size);
                            mNumBytesSubmitted += size;
                        }
                    } else {
                        Log.i("enc: in: timeout, will try again");
                    }
                }
                MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
                index = codec.dequeueOutputBuffer(info, DEQUEUE_OUTPUT_BUFFER_TIMEOUT);
                Log.i("enc: out: flags/index: " + info.flags + "/" + index);
                if (index == MediaCodec.INFO_TRY_AGAIN_LATER) {
                    numDequeueOutputBufferTimeout++;
                    Log.i("enc: out: INFO_TRY_AGAIN_LATER: " + numDequeueOutputBufferTimeout);
                    if (numDequeueOutputBufferTimeout > MAX_NUM_RETRIES_DEQUEUE_OUTPUT_BUFFER) {
                        break;
                    }
                } else if (index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    MediaFormat format = codec.getOutputFormat();
                    Log.i("enc: out: INFO_OUTPUT_FORMAT_CHANGED: " + format.toString());
                } else if (index == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                    codecOutputBuffers = codec.getOutputBuffers();
                    Log.i("enc: out: INFO_OUTPUT_BUFFERS_CHANGED");
                } else {
                    dequeueOutputBuffer(codec, codecOutputBuffers, index, info);
                    mNumBytesDequeued += info.size;
                    numDequeueOutputBufferTimeout = 0;
                    if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        Log.i("enc: out: EOS");
                        status = 0;
                        break;
                    }
                }
            }
            codec.stop();
            codec.release();
            Log.i("stopped and released codec");
        }
        return status;
    }
}