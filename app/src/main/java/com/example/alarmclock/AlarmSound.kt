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

    /** Intent for the system's alarm sound picker, with [currentUri] preselected. */
    fun pickerIntent(context: Context, currentUri: String?): Intent =
        Intent(RingtoneManager.ACTION_RINGTONE_PICKER)
            .putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
            .putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, context.getString(R.string.alarm_sound))
            .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
            .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
            .putExtra(RingtoneManager.EXTRA_RINGTONE_DEFAULT_URI, Settings.System.DEFAULT_ALARM_ALERT_URI)
            .putExtra(
                RingtoneManager.EXTRA_RINGTONE_EXISTING_URI,
                currentUri?.let(Uri::parse) ?: Settings.System.DEFAULT_ALARM_ALERT_URI,
            )

    /**
     * Stores a newly chosen sound. Audio files picked with the document picker keep a
     * persistable read permission so the alarm can still play them after a reboot; the
     * permission for the previously chosen file is given back.
     */
    fun choose(context: Context, settings: AppSettings, uri: Uri?) {
        val resolver = context.contentResolver
        settings.alarmSoundUri.value?.let(Uri::parse)?.let { old ->
            if (old != uri && DocumentsContract.isDocumentUri(context, old)) {
                runCatching { resolver.releasePersistableUriPermission(old, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            }
        }
        if (uri != null && DocumentsContract.isDocumentUri(context, uri)) {
            resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        // Picking "Default" in the system picker means: follow the phone's default alarm sound.
        settings.setAlarmSoundUri(uri?.takeIf { it != Settings.System.DEFAULT_ALARM_ALERT_URI }?.toString())
    }
}
