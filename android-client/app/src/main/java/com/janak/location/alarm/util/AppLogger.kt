package com.janak.location.alarm.util

import android.util.Log

/**
 * Global Logger for the Location Alarm V4 project.
 * Use the tag "LOCATION_ALARM_V4" to filter all app logs in Logcat.
 */
object AppLogger {
    private const val TAG = "LOCATION_ALARM_V4"

    fun d(component: String, message: String) {
        Log.d(TAG, "[$component] $message")
    }

    fun e(component: String, message: String, throwable: Throwable? = null) {
        Log.e(TAG, "[$component] $message", throwable)
    }

    fun i(component: String, message: String) {
        Log.i(TAG, "[$component] $message")
    }

    fun w(component: String, message: String, throwable: Throwable? = null) {
        Log.w(TAG, "[$component] $message", throwable)
    }

    fun v(component: String, message: String) {
        Log.v(TAG, "[$component] $message")
    }
}
