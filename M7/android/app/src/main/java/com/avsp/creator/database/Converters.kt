package com.avsp.creator.database

import androidx.room.TypeConverter
import com.avsp.creator.domain.model.ProjectStatus

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
