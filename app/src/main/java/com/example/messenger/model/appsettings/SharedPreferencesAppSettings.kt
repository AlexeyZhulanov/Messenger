package com.example.messenger.model.appsettings

import android.content.Context

class SharedPreferencesAppSettings(
    appContext: Context
) : AppSettings {

    private val sharedPreferences = appContext.getSharedPreferences(PREF_SETTINGS, Context.MODE_PRIVATE)

    private var lastTokenRefreshTime: Long = 0

    override fun setTokenRefreshing(refreshing: Boolean) {
        sharedPreferences.edit().putBoolean(PREF_IS_REFRESHING, refreshing).apply()
        if (refreshing) {
            lastTokenRefreshTime = System.currentTimeMillis()
        }
    }

    override fun isTokenRefreshing(): Boolean {
        return sharedPreferences.getBoolean(PREF_IS_REFRESHING, false)
    }

    override fun isTokenRecentlyRefreshed(): Boolean {
        return System.currentTimeMillis() - lastTokenRefreshTime < 60000 // 60 секунд
    }

    override fun setCurrentAccessToken(token: String?) {
        val editor = sharedPreferences.edit()
        if (token == null)
            editor.remove(PREF_CURRENT_ACCESS_TOKEN)
        else
            editor.putString(PREF_CURRENT_ACCESS_TOKEN, "Bearer $token")
        editor.apply()
    }

    override fun getCurrentAccessToken(): String? =
        sharedPreferences.getString(PREF_CURRENT_ACCESS_TOKEN, null)

    override fun setCurrentRefreshToken(token: String?) {
        val editor = sharedPreferences.edit()
        if(token == null)
            editor.remove(PREF_CURRENT_REFRESH_TOKEN)
        else
            editor.putString(PREF_CURRENT_REFRESH_TOKEN, "Bearer $token")
        editor.apply()
    }

    override fun getCurrentRefreshToken(): String? =
        sharedPreferences.getString(PREF_CURRENT_REFRESH_TOKEN, null)

    override fun setRemember(bool: Boolean) {
        sharedPreferences.edit().putBoolean(PREF_IS_REMEMBER, bool).apply()
    }

    override fun getRemember(): Boolean =
        sharedPreferences.getBoolean(PREF_IS_REMEMBER, false)

    override fun getCurrentGitlabToken(): String? =
        sharedPreferences.getString(PREF_CURRENT_GITLAB_TOKEN, null)

    override fun setCurrentGitlabToken(token: String?) {
        val editor = sharedPreferences.edit()
        if(token == null)
            editor.remove(PREF_CURRENT_GITLAB_TOKEN)
        else
            editor.putString(PREF_CURRENT_GITLAB_TOKEN, token)
        editor.apply()
    }

    override fun getFCMToken(): String? =
        sharedPreferences.getString(PREF_FCM_TOKEN, null)

    override fun setFCMToken(token: String?) {
        val editor = sharedPreferences.edit()
        if(token == null)
            editor.remove(PREF_FCM_TOKEN)
        else
            editor.putString(PREF_FCM_TOKEN, token)
        editor.apply()
    }

    override fun getTheme(): Int = sharedPreferences.getInt(PREF_THEME, 0)

    override fun setTheme(themeNumber: Int) {
        sharedPreferences.edit().putInt(PREF_THEME, themeNumber).apply()
    }

    override fun getLightWallpaper(): String = sharedPreferences.getString(PREF_WALLPAPER_LIGHT, "") ?: ""


    override fun setLightWallpaper(wallpaper: String) {
        sharedPreferences.edit().putString(PREF_WALLPAPER_LIGHT, wallpaper).apply()
    }

    override fun getDarkWallpaper(): String = sharedPreferences.getString(PREF_WALLPAPER_DARK, "") ?: ""

    override fun setDarkWallpaper(wallpaper: String) {
        sharedPreferences.edit().putString(PREF_WALLPAPER_DARK, wallpaper).apply()
    }

    companion object {
        const val APP_PREFERENCES = "APP_PREFERENCES"
        private const val PREF_CURRENT_ACCESS_TOKEN = "accessToken"
        private const val PREF_CURRENT_REFRESH_TOKEN = "refreshToken"
        private const val PREF_CURRENT_GITLAB_TOKEN = "gitlabToken"
        private const val PREF_IS_REMEMBER = "rememberUser"
        private const val PREF_IS_REFRESHING = "isTokenRefreshing"
        private const val PREF_SETTINGS = "settings"
        private const val PREF_FCM_TOKEN = "fcmToken"
        private const val PREF_THEME = "PREF_THEME"
        private const val PREF_WALLPAPER_LIGHT = "PREF_WALLPAPER_LIGHT"
        private const val PREF_WALLPAPER_DARK = "PREF_WALLPAPER_DARK"
    }
}