package com.avsp.creator

import android.app.Application
import com.avsp.creator.data.repository.MediaRepository
import com.avsp.creator.data.repository.ProjectRepository
import com.avsp.creator.database.AvspDatabase
import com.avsp.creator.dataset.api.DatasetAutomationContract
import com.avsp.creator.dataset.api.MediaSelectionApi
import com.avsp.creator.dataset.repository.DatasetRepository

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

    /** M7 Personal Dataset layer */
    val datasetRepository: DatasetRepository by lazy {
        DatasetRepository(this@AvspApplication, database.mediaDao())
    }

    val mediaSelectionApi: MediaSelectionApi by lazy {
        MediaSelectionApi(datasetRepository)
    }

    val datasetAutomationContract: DatasetAutomationContract by lazy {
        DatasetAutomationContract(datasetRepository, mediaSelectionApi)
    }

    override fun onCreate() {
        super.onCreate()
    }
}
