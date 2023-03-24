# WhisperInput

Offline voice input panel & keyboard with punctuation for Android, experimental, powered by Whisper AI & Kõnele components.

[<img src="example/example_1.jpg" width="49%"/>](example/example_1.jpg)
[<img src="example/example_2.jpg" width="49%"/>](example/example_2.jpg)

Voice input is supported in English. Multilingual input can be used, see in Installation.


## Features

* Works as a voice keyboard (input method editor), a voice input panel, or an assistant app.
* On-device speech recognition, offline.
* Auto-start, auto-stop, audio cue option.


### Usage tips

* To set the app as a web search assistant (long press Home button to open voice input), open the app -> `Settings` gear icon -> `Recognition services (system UI)`. A system menu should open for selecting the assistant app, for example in Samsung UI it's `Device assistance app`. Select `Whisper Input` in the list.
* You can switch your keyboard to `Whisper Input voice keyboard` in the system keyboard list. Click the keyboard icon when done to switch back.


## Installation

Requirements: Java, Android SDK.

Initial setup:
* Put your `keystore.jks` to the project's root folder for signing the app.
* Create a `signing.properties` in the project's root folder with `keystore.jks` credentials:
```
signingStoreLocation=../keystore.jks
signingStorePassword=<keystore.jks password>
signingKeyAlias=<keystore.jks alias>
signingKeyPassword=<keystore.jks key password>
```
* _(Optional)_ To replace the included English-only speech model with a bigger or multilingual one, replace `ggml-tiny.en.bin` in the [assets/models](https://github.com/alex-vt/WhisperInput/tree/main/app/src/main/assets/models) folder with another `.bin` type model from the [whisper.cpp model list](https://huggingface.co/ggerganov/whisper.cpp/tree/main). The models without `.en.` are multilingual. Tiny or base size models recommended. _Note:_ a multilingual model is expected to perform worse in English than the `.en.` model of the similar size.

Run: 
```
git clone https://github.com/alex-vt/WhisperInput.git
cd WhisperInput/
./gradlew assembleRelease
```
Install `app/build/outputs/apk/release/app-release.apk` on Android device.


## Development

Points of interest:
* [OfflineRecognitionService.kt](app/src/main/java/com/alexvt/whisperinput/speak/service/OfflineRecognitionService.kt)
* [WhisperRecognitionModel.kt](app/src/main/java/com/whispercppdemo/WhisperRecognitionModel.kt)

Possible issues:
* Permissions handling. The permissing model from the Kõnele components may be falling back behind new Android versions requirements, that may result in failure to record voice. A workaround is to allow permissions manually in Android's app settings.
* The voice model may take a few seconds to load - the first recording may be inconsistent. Optimally the ready status of the model at [WhisperRecognitionModel.kt:35](https://github.com/alex-vt/WhisperInput/blob/main/app/src/main/java/com/whispercppdemo/WhisperRecognitionModel.kt#L35) should be reflected in the recording UI.


## Underlying projects

* [Kõnele](https://github.com/Kaljurand/K6nele), a voice inputs integration app for Android, by Kaljurand, Apache-2.0 license
* [speechutils](https://github.com/Kaljurand/speechutils), a voice recognition library for Android, by Kaljurand, Apache-2.0 license
* [whisper.cpp](https://github.com/ggerganov/whisper.cpp), a performance-tuned build of a speech model, by ggerganov, MIT license
* [whisper](https://github.com/openai/whisper), a speech model, by OpenAI, MIT license


## License

[MIT license](LICENSE)
