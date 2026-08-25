package com.avsp.creator

import android.app.Application
import com.avsp.creator.data.repository.MediaRepository
import com.avsp.creator.data.repository.ProjectRepository
import com.avsp.creator.database.AvspDatabase

class AvspApplication : Application() {

    val database: AvspDatabase by lazy {
        AvspDatabase.getDatabase(this)
    }

    val projectRepository: ProjectRepository by lazy {
        ProjectRepository(database.projectDao())
    }

    val mediaRepository: MediaRepository by lazy {
        MediaRepository(this@AvspApplication, database.mediaDao())
    }

    override fun onCreate() {
        super.onCreate()
    }
}
