package com.example.messenger.utils

import android.content.Context
import com.linc.amplituda.Amplituda
import com.linc.amplituda.exceptions.AmplitudaException
import java.util.Locale
import java.util.concurrent.TimeUnit

fun takeSample(context: Context, pathOrUrl: String): IntArray {
    val amplituda = Amplituda(context)
    val output = amplituda.processAudio(pathOrUrl)
    val result = output.get { exception: AmplitudaException -> exception.printStackTrace() }
    return result.amplitudesAsList().toTypedArray().toIntArray()
}

fun formatTime(milliseconds: Long): String {
    val minutes = TimeUnit.MILLISECONDS.toMinutes(milliseconds)
    val seconds = TimeUnit.MILLISECONDS.toSeconds(milliseconds) % 60
    return String.format(Locale.ROOT,"%02d:%02d", minutes, seconds)
}