package com.banglatrac.workopolo.face.core.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FrameQualityTest {
    private val config = FaceConfig()

    private fun issue(vararg faces: FaceObservation, requireFront: Boolean = true) =
        (FrameQuality.check(faces.toList(), FRAME_W, FRAME_H, config, requireFront) as? QualityCheck.Bad)?.issue

    @Test
    fun `centred open-eyed front face is good`() {
        assertTrue(FrameQuality.check(listOf(face()), FRAME_W, FRAME_H, config) is QualityCheck.Good)
    }

    @Test
    fun `each problem is reported`() {
        assertEquals(QualityIssue.NoFace, issue())
        assertEquals(QualityIssue.MultipleFaces, issue(face(), face(cx = 100f, width = 150f)))
        assertEquals(QualityIssue.TooFar, issue(face(width = 80f)))
        assertEquals(QualityIssue.TooClose, issue(face(width = 460f)))
        assertEquals(QualityIssue.NotCentred, issue(face(cx = 380f)))
        assertEquals(QualityIssue.Tilted, issue(face(roll = 25f)))
        assertEquals(QualityIssue.TurnedAway, issue(face(yaw = 30f)))
        assertEquals(QualityIssue.EyesClosed, issue(face(eyes = 0.1f)))
    }

    @Test
    fun `small background face is ignored`() {
        assertTrue(FrameQuality.check(listOf(face(), face(cx = 60f, width = 30f)), FRAME_W, FRAME_H, config) is QualityCheck.Good)
    }

    @Test
    fun `side pose allowed when front is not required`() {
        assertEquals(null, issue(face(yaw = 30f, eyes = 0.1f), requireFront = false))
    }
}
