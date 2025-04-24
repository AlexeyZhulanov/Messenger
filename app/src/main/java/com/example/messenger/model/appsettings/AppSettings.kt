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

    fun setTokenRefreshing(refreshing: Boolean)

    fun isTokenRefreshing(): Boolean

    fun isTokenRecentlyRefreshed(): Boolean

    fun getCurrentGitlabToken(): String?

    fun setCurrentGitlabToken(token: String?)

    fun getFCMToken(): String?

    fun setFCMToken(token: String?)

    fun getTheme(): Int

    fun setTheme(themeNumber: Int)

    fun getLightWallpaper(): String

    fun setLightWallpaper(wallpaper: String)

    fun getDarkWallpaper(): String

    fun setDarkWallpaper(wallpaper: String)
}