package com.avsp.pro.m9

import java.util.UUID

enum class M9Platform { YOUTUBE, FACEBOOK, INSTAGRAM, TELEGRAM, WEB }
enum class M9Status { DRAFT, QUEUED, VALIDATING, READY, UPLOADING, PROCESSING, PUBLISHED, SCHEDULED, FAILED, RETRYING, CANCELLED, PARTIAL }

data class M9Job(
    val jobId: String = UUID.randomUUID().toString(),
    val projectId: String,
    val videoPath: String,
    val title: String,
    val platforms: Set<M9Platform>,
    var status: M9Status = M9Status.DRAFT,
    var attempt: Int = 0,
    val maxAttempts: Int = 3
)

object M9StateMachine {
    private val allowed = mapOf(
        M9Status.DRAFT to setOf(M9Status.QUEUED, M9Status.CANCELLED),
        M9Status.QUEUED to setOf(M9Status.VALIDATING, M9Status.CANCELLED),
        M9Status.VALIDATING to setOf(M9Status.READY, M9Status.FAILED, M9Status.CANCELLED),
        M9Status.READY to setOf(M9Status.UPLOADING, M9Status.SCHEDULED, M9Status.CANCELLED),
        M9Status.SCHEDULED to setOf(M9Status.UPLOADING, M9Status.CANCELLED),
        M9Status.UPLOADING to setOf(M9Status.PROCESSING, M9Status.FAILED, M9Status.RETRYING),
        M9Status.PROCESSING to setOf(M9Status.PUBLISHED, M9Status.PARTIAL, M9Status.FAILED, M9Status.RETRYING),
        M9Status.RETRYING to setOf(M9Status.UPLOADING, M9Status.FAILED, M9Status.CANCELLED),
        M9Status.PARTIAL to setOf(M9Status.RETRYING, M9Status.CANCELLED),
        M9Status.FAILED to setOf(M9Status.RETRYING, M9Status.CANCELLED),
        M9Status.PUBLISHED to emptySet(), M9Status.CANCELLED to emptySet()
    )
    fun canTransition(from: M9Status, to: M9Status) = allowed[from].orEmpty().contains(to)
    fun transition(job: M9Job, to: M9Status) { require(canTransition(job.status, to)) { "Invalid transition ${job.status} -> $to" }; job.status = to }
}
