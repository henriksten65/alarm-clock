package com.example.alarmclock

import android.content.Context
import android.content.SharedPreferences
import android.os.UserManager

/**
 * SharedPreferences in *device-protected* storage, which is readable right after a reboot,
 * before the user has unlocked the phone ("Direct Boot"). Normal storage stays encrypted
 * until the first unlock, which would keep alarms from being restored and rung overnight.
 *
 * Preferences written by older versions (in normal storage) are moved over once the user
 * has unlocked; moving is a no-op when there's nothing left to move.
 */
fun deviceProtectedPrefs(context: Context, name: String): SharedPreferences {
    val app = context.applicationContext
    val deviceContext = app.createDeviceProtectedStorageContext()
    if (app.getSystemService(UserManager::class.java).isUserUnlocked) {
        deviceContext.moveSharedPreferencesFrom(app, name)
    }
    return deviceContext.getSharedPreferences(name, Context.MODE_PRIVATE)
}
