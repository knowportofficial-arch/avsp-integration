package com.avsp.pro.m7

import android.content.Context
import com.avsp.pro.m7.data.repository.MediaRepository
import com.avsp.pro.m7.data.repository.ProjectRepository
import com.avsp.pro.m7.database.AvspDatabase
import com.avsp.pro.m7.dataset.api.DatasetAutomationContract
import com.avsp.pro.m7.dataset.api.MediaSelectionApi
import com.avsp.pro.m7.dataset.repository.DatasetRepository

class M7Services(context: Context) {
    private val appContext = context.applicationContext
    val database: AvspDatabase = AvspDatabase.getDatabase(appContext)
    val projectRepository = ProjectRepository(database.projectDao())
    val mediaRepository = MediaRepository(appContext, database.mediaDao())
    val datasetRepository = DatasetRepository(appContext, database.mediaDao())
    val mediaSelectionApi = MediaSelectionApi(datasetRepository)
    val datasetAutomationContract = DatasetAutomationContract(datasetRepository, mediaSelectionApi)
}
