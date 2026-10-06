package com.ahmedismail.flowtrack

import android.app.Application
import com.ahmedismail.flowtrack.data.AppDatabase
import com.ahmedismail.flowtrack.data.repository.FlowTrackRepository

class FlowTrackApplication : Application() {
    val database: AppDatabase by lazy { AppDatabase.get(this) }
    val repository: FlowTrackRepository by lazy { FlowTrackRepository(database) }
}
