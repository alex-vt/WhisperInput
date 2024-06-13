Speechutils
===========

Speechutils is an Android library that helps to implement apps that need to include speech-to-text and text-to-speech functionality.
For example, it provides methods for

- audio recording and encoding
- aggregating speech-to-text and text-to-speech services
- playing audio cues before/after speech-to-text
- pausing the background audio during speech-to-text

Used by
-------

- https://github.com/Kaljurand/K6nele
- https://github.com/Kaljurand/K6nele-service
- https://github.com/Kaljurand/Arvutaja
- https://github.com/willblaschko/AlexaAndroid
- https://github.com/alex-vt/WhisperInput
- ...

Testing
-------

Note that some tests currently fail.

```
$ adb devices -l
List of devices attached
... transport_id:1
...

$ adb -t 1 shell am instrument -w -r \
  -e package ee.ioc.phon.android.speechutils -e debug false \
  ee.ioc.phon.android.speechutils.test/androidx.test.runner.AndroidJUnitRunner | egrep "^[0-9]+)"
1) test01(ee.ioc.phon.android.speechutils.RawAudioRecorderTest)
2) test02(ee.ioc.phon.android.speechutils.RawAudioRecorderTest)
3) test100(ee.ioc.phon.android.speechutils.editor.InputConnectionCommandEditorTest)
4) test218(ee.ioc.phon.android.speechutils.editor.InputConnectionCommandEditorTest)
5) test40(ee.ioc.phon.android.speechutils.editor.InputConnectionCommandEditorTest)
6) test61(ee.ioc.phon.android.speechutils.editor.InputConnectionCommandEditorTest)
7) test80(ee.ioc.phon.android.speechutils.editor.InputConnectionCommandEditorTest)
8) test90(ee.ioc.phon.android.speechutils.editor.InputConnectionCommandEditorTest)
9) test92(ee.ioc.phon.android.speechutils.editor.InputConnectionCommandEditorTest)
```
