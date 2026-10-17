package com.sumonlogs.faceverify.core.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LivenessCheckerTest {
    private val config = FaceConfig()

    @Test
    fun `open closed open passes the blink challenge`() {
        val checker = LivenessChecker(LivenessChallenge.Blink, config)
        checker.onFace(face(eyes = 0.95f), 0)
        checker.onFace(face(eyes = 0.05f), 100)
        assertEquals(LivenessStatus.Passed, checker.onFace(face(eyes = 0.9f), 200))
    }

    @Test
    fun `static photo never blinks and times out`() {
        val checker = LivenessChecker(LivenessChallenge.Blink, config)
        var t = 0L
        var status: LivenessStatus = LivenessStatus.InProgress(0f)
        while (t <= config.livenessTimeoutMs + 100) {
            status = checker.onFace(face(eyes = 0.95f), t)
            t += 66
        }
        assertEquals(LivenessStatus.TimedOut, status)
    }

    @Test
    fun `eyes already closed at start must open first`() {
        val checker = LivenessChecker(LivenessChallenge.Blink, config)
        checker.onFace(face(eyes = 0.05f), 0)
        val status = checker.onFace(face(eyes = 0.95f), 100)
        assertTrue(status is LivenessStatus.InProgress)
    }

    @Test
    fun `turn left needs front then a positive yaw`() {
        val checker = LivenessChecker(LivenessChallenge.TurnLeft, config)
        checker.onFace(face(yaw = 0f), 0)
        assertTrue(checker.onFace(face(yaw = -30f), 100) is LivenessStatus.InProgress)
        assertEquals(LivenessStatus.Passed, checker.onFace(face(yaw = 25f), 200))
    }

    @Test
    fun `photo held turned away from the start does not pass`() {
        val checker = LivenessChecker(LivenessChallenge.TurnRight, config)
        repeat(20) { assertTrue(checker.onFace(face(yaw = -30f), it * 66L) is LivenessStatus.InProgress) }
    }
}
