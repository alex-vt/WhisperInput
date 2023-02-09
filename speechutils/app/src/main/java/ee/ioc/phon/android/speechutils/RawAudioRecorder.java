/*
 * Copyright 2011-2015, Institute of Cybernetics at Tallinn University of Technology
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

import androidx.annotation.RequiresPermission;

import static android.Manifest.permission.RECORD_AUDIO;

/**
 * <p>Records raw audio using SpeechRecord and stores it into a byte array as</p>
 * <ul>
 * <li>signed</li>
 * <li>16-bit</li>
 * <li>native endian</li>
 * <li>mono</li>
 * <li>16kHz (recommended, but a different sample rate can be specified in the constructor)</li>
 * </ul>
 * <p/>
 * <p>For example, the corresponding <code>arecord</code> settings are</p>
 * <p/>
 * <pre>
 * arecord --file-type raw --format=S16_LE --channels 1 --rate 16000
 * arecord --file-type raw --format=S16_BE --channels 1 --rate 16000 (possibly)
 * </pre>
 * <p/>
 * TODO: maybe use: ByteArrayOutputStream
 *
 * @author Kaarel Kaljurand
 */
public class RawAudioRecorder extends AbstractAudioRecorder {

    /**
     * <p>Instantiates a new recorder and sets the state to INITIALIZING.
     * In case of errors, no exception is thrown, but the state is set to ERROR.</p>
     * <p/>
     * <p>Android docs say: 44100Hz is currently the only rate that is guaranteed to work on all devices,
     * but other rates such as 22050, 16000, and 11025 may work on some devices.</p>
     *
     * @param audioSource Identifier of the audio source (e.g. microphone)
     * @param sampleRate  Sample rate (e.g. 16000)
     */
    @RequiresPermission(RECORD_AUDIO)
    public RawAudioRecorder(int audioSource, int sampleRate) {
        super(audioSource, sampleRate);
        try {
            int bufferSize = getBufferSize();
            int framePeriod = bufferSize / (2 * RESOLUTION_IN_BYTES * CHANNELS);
            createRecorder(audioSource, sampleRate, bufferSize);
            createBuffer(framePeriod);
            setState(State.READY);
        } catch (Exception e) {
            if (e.getMessage() == null) {
                handleError("Unknown error occurred while initializing recorder");
            } else {
                handleError(e.getMessage());
            }
        }
    }

    @RequiresPermission(RECORD_AUDIO)
    public RawAudioRecorder(int sampleRate) {
        this(DEFAULT_AUDIO_SOURCE, sampleRate);
    }

    @RequiresPermission(RECORD_AUDIO)
    public RawAudioRecorder() {
        this(DEFAULT_AUDIO_SOURCE, DEFAULT_SAMPLE_RATE);
    }

    public String getContentType() {
        return "audio/x-raw, layout=(string)interleaved, rate=(int)" + getSampleRate() + ", format=(string)S16LE, channels=(int)1";
    }
}