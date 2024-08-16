package com.example.messenger.voicerecorder

import com.arthenica.mobileffmpeg.FFmpeg

class AudioConverter {

    fun convertPcmToMp3(inputFilePath: String, outputFilePath: String, callback: (Boolean, String) -> Unit) {
        val command = "-f s16le -ar 44100 -ac 1 -i $inputFilePath -codec:a libmp3lame $outputFilePath"

        FFmpeg.executeAsync(command) { executionId, returnCode ->
            if (returnCode == 0) {
                callback(true, "Conversion successful")
            } else {
                callback(false, "Conversion failed with return code $returnCode")
            }
        }
    }

    fun convertPcmToOgg(inputFilePath: String, outputFilePath: String, callback: (Boolean, String) -> Unit) {
        val command = "-f s16le -ar 44100 -ac 1 -i $inputFilePath $outputFilePath"

        FFmpeg.executeAsync(command) { executionId, returnCode ->
            if (returnCode == 0) {
                callback(true, "Conversion successful")
            } else {
                callback(false, "Conversion failed with return code $returnCode")
            }
        }
    }

}