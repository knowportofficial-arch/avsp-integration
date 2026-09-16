package com.avsp.pro.m7.domain.model

data class Project(
    val id: String,
    val title: String,
    val topic: String,
    val category: String,
    val targetAudience: String,
    val targetPlatform: String,
    val targetLanguage: String,
    val status: ProjectStatus,
    val createdAt: Long,
    val updatedAt: Long
)
