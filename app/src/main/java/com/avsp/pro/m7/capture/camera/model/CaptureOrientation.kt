package com.avsp.pro.m7.capture.camera.model

/**
 * M6 — requested device/output orientation for a guided-capture clip.
 */
enum class CaptureOrientation(val displayName: String, val degrees: Int) {
    PORTRAIT("Portrait", 0),
    LANDSCAPE("Landscape", 90);

    companion object {
        /** Maps a sensor/device rotation reading (0/90/180/270) to a bucketed orientation. */
        fun fromDeviceRotationDegrees(rotation: Int): CaptureOrientation {
            val normalized = ((rotation % 360) + 360) % 360
            return if (normalized == 90 || normalized == 270) LANDSCAPE else PORTRAIT
        }
    }
}
