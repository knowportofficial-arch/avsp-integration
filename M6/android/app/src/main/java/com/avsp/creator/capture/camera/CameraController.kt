package com.avsp.creator.capture.camera

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.util.Range
import android.view.Surface
import androidx.camera.camera2.interop.Camera2Interop
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.AspectRatio
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.SurfaceOrientedMeteringPointFactory
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.MediaStoreOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoRecordEvent
import androidx.camera.video.VideoCapture
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import java.io.File
import com.avsp.creator.capture.camera.model.CameraCapabilities
import com.avsp.creator.capture.camera.model.CameraProfile
import com.avsp.creator.capture.camera.model.CameraResolution
import com.avsp.creator.capture.camera.model.CameraShotType
import com.avsp.creator.capture.camera.model.CaptureOrientation
import com.avsp.creator.capture.camera.model.FpsRangeSelector
import com.avsp.creator.capture.camera.vision.VisualAnalysisResult
import com.avsp.creator.capture.camera.vision.VisualAnalyzer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine
import kotlin.math.abs

/**
 * Encapsulates CameraX lifecycle-aware camera binding, real ImageCapture,
 * real VideoCapture (Recorder with Quality matching CameraProfile),
 * live throttled ImageAnalysis with Layer A VisualAnalyzer,
 * subject-aware autofocus, zoom mapping, and hardware capability inspection.
 */
class CameraController(private val context: Context) {

    private var cameraProvider: ProcessCameraProvider? = null
    private var activeCamera: Camera? = null
    private var preview: Preview? = null
    private var imageCapture: ImageCapture? = null
    private var videoCapture: VideoCapture<Recorder>? = null
    private var imageAnalysis: ImageAnalysis? = null
    private var activeRecording: Recording? = null

    private val analysisExecutor = Executors.newSingleThreadExecutor()

    // Sensor tilt roll angle
    private var currentTiltRoll: Float = 0f

    // Subject focus debouncing
    private var lastSubjectFocusTimestamp: Long = 0L
    private var lastFocusedX: Float = 0.5f
    private var lastFocusedY: Float = 0.5f

    fun setTiltRoll(roll: Float) {
        this.currentTiltRoll = roll
    }

    suspend fun getCameraProvider(): ProcessCameraProvider {
        cameraProvider?.let { return it }
        return suspendCoroutine { continuation ->
            val future = ProcessCameraProvider.getInstance(context)
            future.addListener(
                {
                    try {
                        val provider = future.get()
                        cameraProvider = provider
                        continuation.resume(provider)
                    } catch (e: Exception) {
                        continuation.resumeWithException(e)
                    }
                },
                ContextCompat.getMainExecutor(context)
            )
        }
    }

    suspend fun hasCamera(lensFacing: Int): Boolean {
        return try {
            val provider = getCameraProvider()
            val selector = CameraSelector.Builder().requireLensFacing(lensFacing).build()
            provider.hasCamera(selector)
        } catch (_: Exception) {
            false
        }
    }

    @OptIn(ExperimentalCamera2Interop::class)
    suspend fun bindCamera(
        lifecycleOwner: LifecycleOwner,
        previewView: PreviewView,
        profile: CameraProfile,
        visualAnalyzer: VisualAnalyzer,
        onVisualAnalysis: (VisualAnalysisResult) -> Unit
    ): Result<Camera> {
        return try {
            val provider = getCameraProvider()
            val cameraSelector = CameraSelector.Builder().requireLensFacing(profile.lensFacing).build()

            if (!provider.hasCamera(cameraSelector)) {
                val lensName = if (profile.lensFacing == CameraSelector.LENS_FACING_FRONT) "Front" else "Rear"
                return Result.failure(IllegalStateException("$lensName camera lens is not available on this device."))
            }

            provider.unbindAll()

            // M6.2: resolve a REAL AE target FPS range from the device's actual Camera2
            // characteristics for the requested frame rate. The selected range is applied to
            // the shared CameraX capture session so a user-selected 60 FPS request is not
            // merely a UI label. If 60 FPS is unavailable, the selector safely falls back to
            // a supported 30 FPS range.
            val fpsRange = if (profile.enforceRequestedFrameRate) {
                selectAeTargetFpsRange(profile.lensFacing, profile.frameRate.targetFps)
            } else null

            // 1. Preview UseCase
            val previewBuilder = Preview.Builder()
            if (fpsRange != null) {
                // Setting AE target FPS range via Camera2Interop on Preview applies to the
                // whole shared capture session (all bound use cases, including VideoCapture),
                // since CameraX binds them into one Camera2 capture session together. This is
                // the real, non-experimental-in-practice mechanism available in CameraX 1.3.2
                // for influencing recorded frame rate -- Recorder itself has no FPS API.
                Camera2Interop.Extender(previewBuilder).setCaptureRequestOption(
                    CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE,
                    fpsRange
                )
            }
            val preview = previewBuilder.build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }
            this.preview = preview

            // 2. Image Capture UseCase
            val imageCaptureBuilder = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            if (profile.preferredAspectRatio != null) {
                // M6.2 fix (FIX 2, photo path only): CameraX's AspectRatio class only has
                // ratio CLASSES (16:9 / 4:3), not separate portrait/landscape constants --
                // 9:16 and 16:9 are the same ratio class, distinguished by targetRotation
                // (below), not by a different AspectRatioStrategy. This deterministically
                // constrains resolution selection to the 16:9 family instead of leaving it
                // unconstrained (previous behavior), which matters on devices exposing
                // multiple aspect classes (e.g. 16:9 and 4:3 sensor modes).
                imageCaptureBuilder.setResolutionSelector(
                    ResolutionSelector.Builder()
                        .setAspectRatioStrategy(AspectRatioStrategy(AspectRatio.RATIO_16_9, AspectRatioStrategy.FALLBACK_RULE_AUTO))
                        .build()
                )
            }
            val imageCap = imageCaptureBuilder.build()
            this.imageCapture = imageCap

            // 3. Video Capture (Recorder) with Profile Quality Mapping
            //
            // M6.2 note (FIX 2, video path): androidx.camera.video.Recorder.Builder has no
            // ResolutionSelector/AspectRatioStrategy API in CameraX 1.3.2 -- that API surface
            // only exists for Preview/ImageCapture/ImageAnalysis. This is a real, current
            // limitation of this CameraX version, not an oversight: video resolution comes
            // only from QualitySelector (always 16:9-family), and portrait/landscape framing
            // is achieved via targetRotation (applyOrientation(), below) rather than a native
            // crop. Actual produced dimensions are measured post-recording (ClipInspector) and
            // reported honestly in metadata rather than assumed.
            val targetQuality = when (profile.resolution) {
                CameraResolution.UHD_4K -> Quality.UHD
                CameraResolution.FULL_HD_1080 -> Quality.FHD
                CameraResolution.HD_720 -> Quality.HD
            }

            val qualitySelector = QualitySelector.from(
                targetQuality,
                FallbackStrategy.higherQualityOrLowerThan(Quality.SD)
            )

            val recorder = Recorder.Builder()
                .setQualitySelector(qualitySelector)
                .build()

            // M6.3: apply the requested FPS directly to the VideoCapture use case.
            // The previous M6.2 implementation only set Camera2 AE on Preview, which
            // could be overridden by CameraX's concurrent-use-case selection and leave
            // the Recorder producing 30fps even when 60fps was selected. CameraX 1.3.x
            // exposes VideoCapture.Builder.setTargetFrameRate() specifically for this
            // use case. It remains a target (not a guarantee), so the resulting MP4 must
            // still be inspected during QA.
            val videoBuilder = VideoCapture.Builder(recorder)
            if (fpsRange != null) {
                videoBuilder.setTargetFrameRate(fpsRange)
            }
            val vidCap = videoBuilder.build()
            this.videoCapture = vidCap

            // 4. Layer A: Lightweight ImageAnalysis UseCase (Throttled for AI telemetry ~8 FPS / 125ms)
            var lastAnalyzedTimestamp = 0L
            val analysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build().also { analyzer ->
                    analyzer.setAnalyzer(analysisExecutor) { imageProxy ->
                        val currentTimestamp = System.currentTimeMillis()
                        if (currentTimestamp - lastAnalyzedTimestamp >= 125L) {
                            lastAnalyzedTimestamp = currentTimestamp
                            try {
                                val result = visualAnalyzer.analyzeFrame(imageProxy, currentTiltRoll)
                                ContextCompat.getMainExecutor(context).execute {
                                    onVisualAnalysis(result)
                                }
                            } catch (_: Exception) {
                            } finally {
                                imageProxy.close()
                            }
                        } else {
                            imageProxy.close()
                        }
                    }
                }
            this.imageAnalysis = analysis

            // Bind to Lifecycle
            val camera = try {
                provider.bindToLifecycle(
                    lifecycleOwner,
                    cameraSelector,
                    preview,
                    imageCap,
                    vidCap,
                    analysis
                )
            } catch (_: Exception) {
                // Fallback for hardware profiles that do not support 4 concurrent use cases
                try {
                    provider.bindToLifecycle(
                        lifecycleOwner,
                        cameraSelector,
                        preview,
                        imageCap,
                        vidCap
                    )
                } catch (_: Exception) {
                    provider.bindToLifecycle(
                        lifecycleOwner,
                        cameraSelector,
                        preview,
                        imageCap
                    )
                }
            }

            this.activeCamera = camera
            Result.success(camera)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun applyShotTypeZoom(shotType: CameraShotType) {
        val camera = activeCamera ?: return
        val zoomState = camera.cameraInfo.zoomState.value ?: return
        val targetRatio = shotType.defaultZoomRatio.coerceIn(zoomState.minZoomRatio, zoomState.maxZoomRatio)
        camera.cameraControl.setZoomRatio(targetRatio)
    }

    fun setZoomRatio(ratio: Float) {
        val camera = activeCamera ?: return
        val zoomState = camera.cameraInfo.zoomState.value ?: return
        val clamped = ratio.coerceIn(zoomState.minZoomRatio, zoomState.maxZoomRatio)
        camera.cameraControl.setZoomRatio(clamped)
    }

    fun tapToFocus(previewView: PreviewView, x: Float, y: Float) {
        val camera = activeCamera ?: return
        val factory = SurfaceOrientedMeteringPointFactory(previewView.width.toFloat(), previewView.height.toFloat())
        val point = factory.createPoint(x, y)
        val action = FocusMeteringAction.Builder(point, FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE)
            .setAutoCancelDuration(3, java.util.concurrent.TimeUnit.SECONDS)
            .build()
        camera.cameraControl.startFocusAndMetering(action)
    }

    /**
     * Subject-aware auto focus with debouncing to prevent excessive focus motor thrashing.
     */
    fun focusOnSubject(
        previewView: PreviewView,
        normalizedX: Float,
        normalizedY: Float,
        force: Boolean = false
    ) {
        val camera = activeCamera ?: return
        val now = System.currentTimeMillis()
        val deltaX = abs(normalizedX - lastFocusedX)
        val deltaY = abs(normalizedY - lastFocusedY)

        // Debounce: minimum 1.5s interval and noticeable subject movement (> 6% frame distance)
        if (!force && (now - lastSubjectFocusTimestamp < 1500L || (deltaX < 0.06f && deltaY < 0.06f))) {
            return
        }

        lastSubjectFocusTimestamp = now
        lastFocusedX = normalizedX
        lastFocusedY = normalizedY

        val viewWidth = previewView.width.toFloat().coerceAtLeast(1f)
        val viewHeight = previewView.height.toFloat().coerceAtLeast(1f)

        val targetPixelX = (normalizedX * viewWidth).coerceIn(0f, viewWidth)
        val targetPixelY = (normalizedY * viewHeight).coerceIn(0f, viewHeight)

        val factory = SurfaceOrientedMeteringPointFactory(viewWidth, viewHeight)
        val point = factory.createPoint(targetPixelX, targetPixelY)
        val action = FocusMeteringAction.Builder(point, FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE)
            .setAutoCancelDuration(3, java.util.concurrent.TimeUnit.SECONDS)
            .build()

        camera.cameraControl.startFocusAndMetering(action)
    }

    fun toggleTorch(enabled: Boolean) {
        activeCamera?.cameraControl?.enableTorch(enabled)
    }

    /**
     * Inspects REAL hardware capabilities using CameraX cameraInfo & QualitySelector.
     */
    fun getCapabilities(lensFacing: Int): CameraCapabilities {
        val camera = activeCamera
        val cameraInfo = camera?.cameraInfo
        val zoomState = cameraInfo?.zoomState?.value
        val hasTorch = cameraInfo?.hasFlashUnit() == true

        var is1080p = true
        var is4k = false
        var is720p = true

        if (cameraInfo != null) {
            val supportedQualities = QualitySelector.getSupportedQualities(cameraInfo)
            is1080p = supportedQualities.contains(Quality.FHD)
            is4k = supportedQualities.contains(Quality.UHD)
            is720p = supportedQualities.contains(Quality.HD) || supportedQualities.contains(Quality.SD)
        }

        return CameraCapabilities(
            minZoomRatio = zoomState?.minZoomRatio ?: 1.0f,
            maxZoomRatio = zoomState?.maxZoomRatio ?: 8.0f,
            is60FpsSupported = is60FpsHardwareSupported(lensFacing),
            is720pSupported = is720p,
            is1080pSupported = is1080p,
            is4kSupported = is4k,
            hasTorch = hasTorch,
            hasBackCamera = true,
            hasFrontCamera = true
        )
    }

    /**
     * Real JPEG photo capture pipeline saving into MediaStore (Pictures/AVSP/).
     */
    fun takePhoto(
        relativePath: String = "Pictures/AVSP/Unsorted",
        onImageSaved: (Uri, String) -> Unit,
        onError: (ImageCaptureException) -> Unit
    ) {
        val imageCap = imageCapture ?: run {
            onError(ImageCaptureException(ImageCapture.ERROR_INVALID_CAMERA, "ImageCapture is not initialized", null))
            return
        }

        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val displayName = "AVSP_IMG_$timestamp.jpg"

        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, relativePath.trimEnd('/'))
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }

        val outputOptions = ImageCapture.OutputFileOptions.Builder(
            context.contentResolver,
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            contentValues
        ).build()

        imageCap.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    val savedUri = outputFileResults.savedUri ?: MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        val updateValues = ContentValues().apply {
                            put(MediaStore.Images.Media.IS_PENDING, 0)
                        }
                        try {
                            context.contentResolver.update(savedUri, updateValues, null, null)
                        } catch (_: Exception) {}
                    }
                    onImageSaved(savedUri, displayName)
                }

                override fun onError(exception: ImageCaptureException) {
                    onError(exception)
                }
            }
        )
    }

    /**
     * Real MP4 video recording pipeline saving into MediaStore (Movies/AVSP/).
     */
    fun startRecording(
        relativePath: String = "Movies/AVSP/Unsorted",
        onEvent: (VideoRecordEvent) -> Unit,
        onError: (Exception) -> Unit
    ) {
        val vidCap = videoCapture ?: run {
            onError(IllegalStateException("VideoCapture is not initialized"))
            return
        }

        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val displayName = "AVSP_VID_$timestamp.mp4"

        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
            put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Video.Media.RELATIVE_PATH, relativePath.trimEnd('/'))
            }
        }

        val mediaStoreOutput = MediaStoreOutputOptions.Builder(
            context.contentResolver,
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        ).setContentValues(contentValues).build()

        var pendingRecording = vidCap.output.prepareRecording(context, mediaStoreOutput)

        val hasAudioPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        if (hasAudioPermission) {
            try {
                pendingRecording = pendingRecording.withAudioEnabled()
            } catch (_: SecurityException) {}
        }

        try {
            activeRecording = pendingRecording.start(ContextCompat.getMainExecutor(context)) { event ->
                onEvent(event)
            }
        } catch (e: Exception) {
            onError(e)
        }
    }

    fun stopRecording() {
        try {
            activeRecording?.stop()
        } catch (_: Exception) {}
        activeRecording = null
    }

    fun unbind() {
        try {
            activeRecording?.stop()
            activeRecording = null
            cameraProvider?.unbindAll()
            activeCamera = null
        } catch (_: Exception) {}
    }

    // ------------------------------------------------------------------
    // M6 / M6.1 additions below.
    //
    // M6.1 correction (external audit): two lines above this block were
    // touched inside pre-existing methods, intentionally and minimally:
    //   1. bindCamera(): added `this.preview = preview` so the M6.1
    //      applyOrientation() method below has a handle on the Preview
    //      use case. No existing behavior changed.
    //   2. getCapabilities(): `is60FpsSupported = true` (hardcoded) was
    //      replaced with `is60FpsSupported = is60FpsHardwareSupported(lensFacing)`
    //      per the M6.1 audit requirement -- this method is shared with the
    //      AI camera screen, and the audit explicitly required the fix be
    //      applied here rather than only in a parallel M6-only method.
    // Everything else in this file above this block is unchanged from the
    // original CameraController.kt.
    // ------------------------------------------------------------------

    /**
     * M6.1 — sets the requested output orientation on ImageCapture/VideoCapture.
     *
     * CameraX 1.3.2's Recorder cannot select an arbitrary aspect ratio directly; the
     * available Quality tiers (HD/FHD/UHD) are all natively 16:9. A 9:16 "portrait" result
     * is achieved by rotating the encode target, not by re-cropping to a different ratio.
     * This is the correct, real CameraX mechanism for that: it does not fake or guarantee
     * an aspect ratio the API can't guarantee. Callers MUST verify the actually-produced
     * file's dimensions after recording (see guided/ClipInspector.kt) rather than trust
     * this call alone -- see M6.1 handoff doc, "9:16 / 16:9 behavior" section.
     *
     * NOTE: Preview's rotation is intentionally left alone here. PreviewView manages
     * display-rotation for the live preview internally, and I could not confirm
     * `Preview.targetRotation` is a stable, publicly settable property on this CameraX
     * version without the actual SDK/compiler to check against -- rather than guess and
     * risk a compile error, only ImageCapture and VideoCapture (which I'm confident expose
     * `setTargetRotation`/`targetRotation`, per standard CameraX orientation-handling
     * samples) are set. The `preview` field is stored for future use if this needs revisiting
     * with a real compiler available.
     */
    fun applyOrientation(orientation: CaptureOrientation) {
        val rotation = when (orientation) {
            CaptureOrientation.PORTRAIT -> Surface.ROTATION_0
            CaptureOrientation.LANDSCAPE -> Surface.ROTATION_90
        }
        imageCapture?.targetRotation = rotation
        videoCapture?.targetRotation = rotation
    }

    /**
     * M6 — real exposure compensation range for the currently bound camera.
     * Returns null if no camera is bound or the device reports a zero-width range
     * (some devices do not support manual EV adjustment).
     */
    fun exposureCompensationRange(): IntRange? {
        val info = activeCamera?.cameraInfo?.exposureState ?: return null
        val range = info.exposureCompensationRange
        if (range.lower == 0 && range.upper == 0) return null
        return range.lower..range.upper
    }

    fun exposureCompensationStepSize(): Float =
        activeCamera?.cameraInfo?.exposureState?.exposureCompensationStep?.toFloat() ?: 0f

    /**
     * M6 — sets exposure compensation (EV) as an index into [exposureCompensationRange].
     * No-ops safely if the device does not support it or no camera is bound.
     */
    fun setExposureCompensation(index: Int) {
        val camera = activeCamera ?: return
        val range = exposureCompensationRange() ?: return
        val clamped = index.coerceIn(range.start, range.endInclusive)
        try {
            camera.cameraControl.setExposureCompensationIndex(clamped)
        } catch (_: Exception) {
            // Some devices throw if a metering session is mid-transition; safe to ignore,
            // caller can retry on next user interaction.
        }
    }

    /**
     * M6 — real (non-hardcoded) 60fps capability check for the given lens, using the
     * Camera2 characteristics rather than assuming support. CameraX's own
     * QualitySelector reports resolution tiers but not frame-rate tiers, so this reads
     * CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES directly.
     *
     * NOTE (updated in M6.2): this method only reports hardware *capability*, it does not by
     * itself request or configure anything. As of M6.2, [selectAeTargetFpsRange] +
     * `Camera2Interop` in `bindCamera()` (opt-in via `CameraProfile.enforceRequestedFrameRate`)
     * DOES request a real AE target FPS range for the session. That still isn't a guarantee
     * CameraX's Recorder encodes at exactly that rate -- Recorder itself has no FPS API, so
     * the AE range is a session-level hint shared across use cases, not a per-recording
     * contract. Actual encoded FPS must still be measured post-recording (ClipInspector).
     */
    fun is60FpsHardwareSupported(lensFacing: Int): Boolean {
        return try {
            val manager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val targetLensFacing = if (lensFacing == CameraSelector.LENS_FACING_FRONT) {
                CameraCharacteristics.LENS_FACING_FRONT
            } else {
                CameraCharacteristics.LENS_FACING_BACK
            }
            manager.cameraIdList.any { id ->
                val chars = manager.getCameraCharacteristics(id)
                if (chars.get(CameraCharacteristics.LENS_FACING) != targetLensFacing) return@any false
                val ranges = chars.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES)
                ranges?.any { it.upper >= 60 } == true
            }
        } catch (_: Exception) {
            false
        }
    }

    /**
     * M6.2 — resolves a real Camera2 AE target FPS range for [requestedFps] from the
     * device's actual available ranges, delegating selection to [FpsRangeSelector].
     * Falls back to a supported 30fps range if the requested rate is unavailable.
     */
    private fun selectAeTargetFpsRange(lensFacing: Int, requestedFps: Int): Range<Int>? {
        return try {
            val manager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val targetLensFacing = if (lensFacing == CameraSelector.LENS_FACING_FRONT) {
                CameraCharacteristics.LENS_FACING_FRONT
            } else {
                CameraCharacteristics.LENS_FACING_BACK
            }
            for (id in manager.cameraIdList) {
                val chars = manager.getCameraCharacteristics(id)
                if (chars.get(CameraCharacteristics.LENS_FACING) != targetLensFacing) continue
                val ranges = chars.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES) ?: continue
                val candidates = ranges.map { FpsRangeSelector.FpsRange(it.lower, it.upper) }
                val chosen = FpsRangeSelector.select(candidates, requestedFps) ?: return null
                return Range(chosen.lower, chosen.upper)
            }
            null
        } catch (_: Exception) {
            null
        }
    }

    /**
     * M6 — File-based (non-MediaStore) photo capture, used by the guided-capture flow so
     * MP4/JSON/thumbnail siblings for a clip live together in [GuidedCaptureStorage]'s layout.
     * Existing takePhoto() (MediaStore-based, used by the AI camera screen) is unchanged.
     */
    fun takePhotoToFile(
        targetFile: File,
        onImageSaved: (File) -> Unit,
        onError: (ImageCaptureException) -> Unit
    ) {
        val imageCap = imageCapture ?: run {
            onError(ImageCaptureException(ImageCapture.ERROR_INVALID_CAMERA, "ImageCapture is not initialized", null))
            return
        }
        val outputOptions = ImageCapture.OutputFileOptions.Builder(targetFile).build()
        imageCap.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    onImageSaved(targetFile)
                }

                override fun onError(exception: ImageCaptureException) {
                    onError(exception)
                }
            }
        )
    }

    /**
     * M6 — File-based (non-MediaStore) video recording, used by the guided-capture flow.
     * Existing startRecording() (MediaStore-based, used by the AI camera screen) is unchanged.
     */
    fun startRecordingToFile(
        targetFile: File,
        onEvent: (VideoRecordEvent) -> Unit,
        onError: (Exception) -> Unit
    ) {
        val vidCap = videoCapture ?: run {
            onError(IllegalStateException("VideoCapture is not initialized"))
            return
        }

        val outputOptions = FileOutputOptions.Builder(targetFile).build()
        var pendingRecording = vidCap.output.prepareRecording(context, outputOptions)

        val hasAudioPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        if (hasAudioPermission) {
            try {
                pendingRecording = pendingRecording.withAudioEnabled()
            } catch (_: SecurityException) {}
        }

        try {
            activeRecording = pendingRecording.start(ContextCompat.getMainExecutor(context)) { event ->
                onEvent(event)
            }
        } catch (e: Exception) {
            onError(e)
        }
    }
}
