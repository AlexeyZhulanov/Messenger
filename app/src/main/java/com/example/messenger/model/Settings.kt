package com.example.messenger.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import javax.inject.Inject

@Parcelize
data class Settings(
    val id: Long,
    var remember: Boolean = false,
    var name: String,
    var password: String
) : Parcelable