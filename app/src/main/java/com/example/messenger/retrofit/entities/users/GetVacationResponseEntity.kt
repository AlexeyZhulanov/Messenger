package com.example.messenger.retrofit.entities.users

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class GetVacationResponseEntity(
    @Json(name = "vacation_start") val vacationStart: String? = "",
    @Json(name = "vacation_end") val vacationEnd: String? = ""
) {
    fun toPair(): Pair<String, String>? {
        return if(vacationStart == null || vacationEnd == null) null else Pair(vacationStart, vacationEnd)
    }

}