package com.avsp.pro.m7.capture.camera.guided

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import com.avsp.pro.m7.core.theme.AVSPTheme

/**
 * M6 — standalone entry point for the guided-capture module.
 *
 * Independently runnable/testable per the M6 requirement: this Activity can be launched
 * directly (see run commands in the M6 handoff doc) without going through the rest of the
 * AVSP app's navigation graph, dashboard, or project workflow.
 *
 * Launches with [GuidedCaptureTemplate.sample] unless a template is supplied via
 * [EXTRA_TEMPLATE_IS_SAMPLE] (reserved for a future richer template-passing mechanism;
 * for now this activity is the manual/QA entry point, while production integration should
 * call [GuidedCaptureScreen] directly from a host screen with a real template).
 */
class GuidedCaptureActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val template = GuidedCaptureTemplate.sample()

        setContent {
            AVSPTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    GuidedCaptureScreen(
                        template = template,
                        onFinished = { clips ->
                            val resultIntent = Intent().putExtra(EXTRA_RESULT_CLIP_COUNT, clips.size)
                            setResult(RESULT_OK, resultIntent)
                            finish()
                        },
                        onExit = { finish() }
                    )
                }
            }
        }
    }

    companion object {
        const val EXTRA_TEMPLATE_IS_SAMPLE = "extra_template_is_sample"
        const val EXTRA_RESULT_CLIP_COUNT = "extra_result_clip_count"

        fun launchIntent(context: Context): Intent =
            Intent(context, GuidedCaptureActivity::class.java)
    }
}
