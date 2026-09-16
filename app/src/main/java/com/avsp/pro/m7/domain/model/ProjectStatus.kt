package com.avsp.pro.m7.domain.model

enum class ProjectStatus(val displayName: String) {
    IDEA("Idea / Concept"),
    RESEARCH("Source Research"),
    SCRIPT("Scripting"),
    VOICE("Voice Recording"),
    CAPTURE("Field Capture"),
    EDITING("Video Editing"),
    REVIEW("Review & Quality"),
    READY("Ready to Publish"),
    PUBLISHED("Published"),
    ANALYZING("Analytics & Insights");

    companion object {
        fun fromString(value: String): ProjectStatus {
            return entries.find { it.name.equals(value, ignoreCase = true) } ?: IDEA
        }
    }
}
