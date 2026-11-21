package com.example.alarmclock

import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import android.provider.Settings

/** Helpers for the alarm sound setting. */
object AlarmSound {

    /** A human-readable name for the chosen sound; null means the default alarm sound. */
    fun title(context: Context, uriString: String?): String {
        if (uriString == null) return context.getString(R.string.default_alarm_sound)
        val uri = Uri.parse(uriString)
        return try {
            if (DocumentsContract.isDocumentUri(context, uri)) {
                context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                    ?.use { if (it.moveToFirst()) it.getString(0) else null }
            } else {
                RingtoneManager.getRingtone(context, uri)?.getTitle(context)
            }
        } catch (e: Exception) {
            null
        } ?: uri.lastPathSegment ?: uriString
    }

    /**
     * Intent for the system's alarm sound picker, with [currentUri] preselected. [showDefault] adds
     * the picker's "Default" entry (the phone's default alarm sound).
     */
    fun pickerIntent(context: Context, currentUri: String?, showDefault: Boolean = true): Intent =
        Intent(RingtoneManager.ACTION_RINGTONE_PICKER)
            .putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
            .putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, context.getString(R.string.alarm_sound))
            .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, showDefault)
            .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
            .putExtra(RingtoneManager.EXTRA_RINGTONE_DEFAULT_URI, Settings.System.DEFAULT_ALARM_ALERT_URI)
            .putExtra(
                RingtoneManager.EXTRA_RINGTONE_EXISTING_URI,
                currentUri?.let(Uri::parse) ?: Settings.System.DEFAULT_ALARM_ALERT_URI,
            )

    /**
     * Prepares a newly picked sound for storing and returns the string to store (null for the
     * phone's default alarm sound). Audio files picked with the document picker keep a
     * persistable read permission so the alarm can still play them after a reboot.
     */
    fun accept(context: Context, uri: Uri?): String? {
        if (uri != null && DocumentsContract.isDocumentUri(context, uri)) {
            context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        // Picking "Default" in the system picker means: follow the phone's default alarm sound.
        return uri?.takeIf { it != Settings.System.DEFAULT_ALARM_ALERT_URI }?.toString()
    }

    /** Stores a newly chosen sound in the settings (the sound for alarms without their own). */
    fun choose(context: Context, settings: AppSettings, uri: Uri?) {
        settings.setAlarmSoundUri(accept(context, uri))
    }

    /**
     * Gives back the read permission for audio files that neither the settings nor any alarm use
     * any more. Called when the app starts rather than on every change, so "Undo" after deleting
     * an alarm, or cancelling the edit dialog, can't leave an alarm with a file it can't read.
     */
    fun releaseUnused(context: Context) {
        val inUse = buildSet {
            AppSettings.getInstance(context).alarmSoundUri.value?.let(::add)
            AlarmRepository.getInstance(context).alarms.value.forEach { alarm -> alarm.soundUri?.let(::add) }
        }
        val resolver = context.contentResolver
        resolver.persistedUriPermissions
            .filter { it.uri.toString() !in inUse }
            .forEach { runCatching { resolver.releasePersistableUriPermission(it.uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) } }
    }
}
