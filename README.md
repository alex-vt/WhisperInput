# WhisperInput

Offline voice input panel & keyboard with punctuation for Android, experimental, powered by Whisper AI & Kõnele components.

[<img src="example/example_1.jpg" width="49%"/>](example/example_1.jpg)
[<img src="example/example_2.jpg" width="49%"/>](example/example_2.jpg)

Voice input is supported in English.


## Features

* Works as a voice keyboard (input method editor), a voice input panel, or an assistant app.
* On-device speech recognition, offline.
* Auto-start, auto-stop, audio cue option.


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


## Underlying projects

* [Kõnele](https://github.com/Kaljurand/K6nele), a voice inputs integration app for Android, by Kaljurand, Apache-2.0 license
* [speechutils](https://github.com/Kaljurand/speechutils), a voice recognition library for Android, by Kaljurand, Apache-2.0 license
* [whisper.cpp](https://github.com/ggerganov/whisper.cpp), a performance-tuned build of a speech model, by ggerganov, MIT license
* [whisper](https://github.com/openai/whisper), a speech model, by OpenAI, MIT license


## License

[MIT license](LICENSE)
