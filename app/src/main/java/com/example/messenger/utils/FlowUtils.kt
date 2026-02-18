package com.example.messenger.utils

import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch

fun <T> Flow<T>.chunkedFlowLast(timeout: Long): Flow<List<T>> = channelFlow {
    val buffer = mutableListOf<T>()
    var emitJob: Job? = null

    try {
        collect { message ->
            buffer.add(message)
            emitJob?.cancel()
            emitJob = coroutineScope {
                launch {
                    delay(timeout)
                    if(buffer.isNotEmpty()) {
                        send(buffer.toList())
                        buffer.clear()
                    }
                }
            }
        }
    } finally {
        emitJob?.cancel()
        if (buffer.isNotEmpty()) {
            send(buffer.toList())
        }
    }
}