package com.example.messenger.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class Settings(
    val id: Long,
    var remember: Int = 0,
    var name: String? = "",
    var password: String? = ""
) : Parcelable