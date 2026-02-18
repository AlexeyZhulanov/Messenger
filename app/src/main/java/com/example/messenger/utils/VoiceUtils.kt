package com.example.messenger.utils

import android.content.Context
import com.linc.amplituda.Amplituda
import com.linc.amplituda.exceptions.AmplitudaException

fun takeSample(context: Context, pathOrUrl: String): IntArray {
    val amplituda = Amplituda(context)
    val output = amplituda.processAudio(pathOrUrl)
    val result = output.get { exception: AmplitudaException -> exception.printStackTrace() }
    return result.amplitudesAsList().toTypedArray().toIntArray()
}
