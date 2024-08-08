package com.example.messenger.picker

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper

object ImageLoaderUtils {
    fun assertValidRequest(context: Context?): Boolean {
        if (context is Activity) {
            return !isDestroy(context)
        } else if (context is ContextWrapper) {
            if (context.baseContext is Activity) {
                val activity = context.baseContext as Activity
                return !isDestroy(activity)
            }
        }
        return true
    }

    private fun isDestroy(activity: Activity?): Boolean {
        if (activity == null) {
            return true
        }
        return activity.isFinishing || activity.isDestroyed
    }
}