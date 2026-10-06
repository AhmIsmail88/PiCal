package com.ahmedismail.flowtrack.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "projects")
data class Project(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val number: String? = null,
    val responsibleEngineer: String,
    val contractor: String? = null,
    val client: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)
