package com.avsp.pro.capture.camera.guided

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import com.avsp.pro.AvspApplication
import com.avsp.pro.capture.camera.planner.LocalShotPlanner
import com.avsp.pro.capture.theme.AVSPTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Guided capture secondary Activity (not a second launcher).
 *
 * When [EXTRA_PROJECT_ID] is supplied:
 * 1. Loads the Pro project name/description
 * 2. Builds a [MasterShotPlan] via [LocalShotPlanner]
 * 3. Adapts it when the plan contains at least one VIDEO (PHOTO/VIDEO preserved)
 * 4. Otherwise uses explicit [GuidedCapturePlanAdapter.fromSampleFallback]
 *    (Intro/Wide/Medium/Close all VIDEO — V2 behavior)
 * 5. Registers completed clips into Media Library with mission/shot identity
 *
 * Falls back to sample also when no project context is available.
 */
class GuidedCaptureActivity : ComponentActivity() {

    private var sessionState by mutableStateOf<SessionLoadState>(SessionLoadState.Loading)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val projectId = intent.getStringExtra(EXTRA_PROJECT_ID).orEmpty()
        loadGuidedSession(projectId)

        setContent {
            AVSPTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    when (val loaded = sessionState) {
                        SessionLoadState.Loading -> {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator()
                            }
                        }
                        is SessionLoadState.Ready -> {
                            GuidedCaptureScreen(
                                session = loaded.session,
                                planContext = loaded.planContext,
                                onFinished = { clips ->
                                    if (projectId.isNotBlank()) {
                                        registerClips(projectId, clips) {
                                            finishWithResult(clips.size)
                                        }
                                    } else {
                                        finishWithResult(clips.size)
                                    }
                                },
                                onExit = { finish() }
                            )
                        }
                    }
                }
            }
        }
    }

    private fun loadGuidedSession(projectId: String) {
        lifecycleScope.launch {
            val loaded = withContext(Dispatchers.IO) {
                if (projectId.isBlank()) {
                    return@withContext SessionLoadState.Ready(
                        session = GuidedCapturePlanAdapter.fromSampleFallback(),
                        planContext = null
                    )
                }
                val app = application as? AvspApplication
                val project = runCatching {
                    app?.container?.projectRepository?.openProject(projectId)
                }.getOrNull()

                val request = buildString {
                    append(project?.name.orEmpty().ifBlank { "General coverage" })
                    val desc = project?.description.orEmpty().trim()
                    if (desc.isNotBlank()) {
                        append(". ")
                        append(desc)
                    }
                }
                val plan = LocalShotPlanner().createPlan(request)
                // Mixed plans keep PHOTO/VIDEO. PHOTO-only plans → explicit V2 sample VIDEO.
                SessionLoadState.Ready(
                    session = GuidedCapturePlanAdapter.fromMasterShotPlanOrSampleFallback(plan),
                    planContext = request
                )
            }
            sessionState = loaded
        }
    }

    private fun finishWithResult(clipCount: Int) {
        val resultIntent = Intent().putExtra(EXTRA_RESULT_CLIP_COUNT, clipCount)
        setResult(RESULT_OK, resultIntent)
        finish()
    }

    private fun registerClips(
        projectId: String,
        clips: List<ClipMetadata>,
        onDone: () -> Unit
    ) {
        val app = application as? AvspApplication
        val mediaRepository = app?.container?.mediaRepository
        if (mediaRepository == null) {
            onDone()
            return
        }
        lifecycleScope.launch {
            clips.forEach { clip ->
                val file = File(clip.file)
                if (!file.exists()) return@forEach
                val isPhoto = file.name.endsWith(".jpg", ignoreCase = true) ||
                    file.name.endsWith(".jpeg", ignoreCase = true)
                // Photos: existence + non-empty. Videos: playable MP4 gate.
                if (!isPhoto && !GuidedCaptureVideoValidator.isPlayable(file)) {
                    return@forEach
                }
                if (isPhoto && file.length() < 64L) {
                    return@forEach
                }
                val uri = GuidedCaptureUris.contentUriForFile(this@GuidedCaptureActivity, file).toString()
                val mediaType = if (isPhoto) "PHOTO" else "VIDEO"
                // Preserve semantic shot name + shot code. Framing (WIDE/MEDIUM/CLOSE) is
                // technical only and must not replace clipName / category.
                runCatching {
                    mediaRepository.saveCapturedMedia(
                        projectId = projectId,
                        uriString = uri,
                        mediaType = mediaType,
                        displayName = clip.clipName.ifBlank { clip.category },
                        shotType = clip.category,
                        missionId = clip.missionId,
                        missionShotId = clip.missionShotId,
                        durationSeconds = (clip.durationMs / 1000L).coerceAtLeast(0L),
                        latitude = clip.latitude,
                        longitude = clip.longitude,
                        category = clip.category.ifBlank { "Uncategorized" }
                    )
                }
            }
            onDone()
        }
    }

    private sealed class SessionLoadState {
        data object Loading : SessionLoadState()
        data class Ready(
            val session: GuidedCapturePlanAdapter.GuidedSession,
            val planContext: String?
        ) : SessionLoadState()
    }

    companion object {
        const val EXTRA_TEMPLATE_IS_SAMPLE = "extra_template_is_sample"
        const val EXTRA_RESULT_CLIP_COUNT = "extra_result_clip_count"
        const val EXTRA_PROJECT_ID = "extra_project_id"

        fun launchIntent(context: Context, projectId: String? = null): Intent =
            Intent(context, GuidedCaptureActivity::class.java).apply {
                if (!projectId.isNullOrBlank()) {
                    putExtra(EXTRA_PROJECT_ID, projectId)
                }
            }
    }
}
