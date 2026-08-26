package com.avsp.pro.capture.camera.guided

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import com.avsp.pro.AvspApplication
import com.avsp.pro.capture.theme.AVSPTheme
import kotlinx.coroutines.launch
import java.io.File

/**
 * Guided capture secondary Activity (not a second launcher).
 * When [EXTRA_PROJECT_ID] is supplied, completed clips are registered into the Pro
 * project's capture media store with M7 quality analysis.
 */
class GuidedCaptureActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val projectId = intent.getStringExtra(EXTRA_PROJECT_ID).orEmpty()
        val template = GuidedCaptureTemplate.sample()

        setContent {
            AVSPTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    GuidedCaptureScreen(
                        template = template,
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
                val uri = Uri.fromFile(file).toString()
                runCatching {
                    mediaRepository.saveCapturedMedia(
                        projectId = projectId,
                        uriString = uri,
                        mediaType = "VIDEO",
                        displayName = clip.clipId,
                        shotType = "GUIDED",
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
