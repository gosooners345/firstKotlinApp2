package com.kotlinFirst.project1

import android.content.Context
import android.location.Location
import androidx.core.content.edit
import java.text.SimpleDateFormat
import java.util.*


fun Location?.toText(): String {
    val date = Calendar.getInstance().time
    val formatter = SimpleDateFormat.getDateTimeInstance()
    val formattedDate = formatter.format(date)
    return if (this != null) {
        "($latitude, $longitude, $formattedDate)"
    } else {
        "Unknown location"
    }
}

internal object SharedPreferenceUtil {

    const val KEY_FOREGROUND_ENABLED = "tracking_foreground_location"

    /**
     * Returns true if requesting location updates, otherwise returns false.
     *
     * @param context The [Context].
     */
    fun getLocationTrackingPref(context: Context): Boolean =
            context.getSharedPreferences(
                    context.getString(R.string.preference_file_key), Context.MODE_PRIVATE)
                    .getBoolean(KEY_FOREGROUND_ENABLED, false)

    /**
     * Stores the location updates state in SharedPreferences.
     * @param requestingLocationUpdates The location updates state.
     */
    fun saveLocationTrackingPref(context: Context, requestingLocationUpdates: Boolean) =
            context.getSharedPreferences(
                    context.getString(R.string.preference_file_key),
                    Context.MODE_PRIVATE).edit {
                putBoolean(KEY_FOREGROUND_ENABLED, requestingLocationUpdates)
            }
}
