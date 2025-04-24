package com.example.messenger.model

import android.os.Build
import android.os.Bundle
import android.os.Parcelable

inline fun <reified T : Parcelable> Bundle.getParcelableCompat(key: String): T? {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        getParcelable(key, T::class.java)
    } else {
        @Suppress("DEPRECATION")
        getParcelable(key) as? T
    }
}

inline fun <reified T : Parcelable> Bundle.getParcelableArrayListCompat(key: String): List<T> {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        getParcelableArrayList(key, T::class.java) ?: emptyList()
    } else {
        @Suppress("DEPRECATION")
        getParcelableArrayList(key) ?: emptyList()
    }
}