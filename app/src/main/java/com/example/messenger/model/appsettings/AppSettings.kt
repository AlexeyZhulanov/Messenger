package com.example.messenger.model.appsettings

interface AppSettings {

    /**
     * Get auth token of the current logged-in user.
     */
    fun getCurrentAccessToken(): String?

    /**
     * Set auth token of the logged-in user.
     */
    fun setCurrentAccessToken(token: String?)

    fun getCurrentRefreshToken(): String?

    fun setCurrentRefreshToken(token: String?)

    fun getRemember(): Boolean

    fun setRemember(bool: Boolean)
}