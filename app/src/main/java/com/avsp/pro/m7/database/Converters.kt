package com.avsp.pro.m7.database

import androidx.room.TypeConverter
import com.avsp.pro.m7.domain.model.ProjectStatus

class Converters {
    @TypeConverter
    fun fromProjectStatus(status: ProjectStatus): String {
        return status.name
    }

    @TypeConverter
    fun toProjectStatus(status: String): ProjectStatus {
        return ProjectStatus.fromString(status)
    }
}
