package com.ahmedismail.flowtrack.util

import android.content.Context

/**
 * Remembers which project was last opened, across app restarts. Without
 * this, "currentProject" only ever lived in Compose state for the nav host,
 * so a fresh launch always showed Dashboard/Add/Reports/Settings as "no
 * project open" even when projects already existed — you had to reopen one
 * from Projects every single time the app was killed and relaunched.
 */
object CurrentProjectStore {
    private const val PREFS = "flowtrack_prefs"
    private const val KEY_PROJECT_ID = "current_project_id"

    fun get(context: Context): Long? {
        val id = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong(KEY_PROJECT_ID, -1L)
        return if (id == -1L) null else id
    }

    fun set(context: Context, projectId: Long) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putLong(KEY_PROJECT_ID, projectId).apply()
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(KEY_PROJECT_ID).apply()
    }
}
