package com.avsp.pro.m9

import org.junit.Assert.*
import org.junit.Test

class M9PublishingTest {
    @Test fun stateMachineAllowsExpectedFlow() {
        val j = M9Job("id", "p1", "final.mp4", "Title", setOf(M9Platform.YOUTUBE))
        M9StateMachine.transition(j, M9Status.QUEUED)
        M9StateMachine.transition(j, M9Status.VALIDATING)
        M9StateMachine.transition(j, M9Status.READY)
        assertEquals(M9Status.READY, j.status)
        assertFalse(M9StateMachine.canTransition(j.status, M9Status.PUBLISHED))
    }
}
