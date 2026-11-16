package com.sumonlogs.faceverify.core.engine

import org.junit.Assert.assertEquals
import org.junit.Test

class ExposureSteeringTest {
    private val config = FaceConfig()

    @Test
    fun `a very dark face gets a full stop brighter`() {
        assertEquals(1f, ExposureSteering(config).step(25f, nowMs = 0), 0f)
    }

    @Test
    fun `a somewhat dark face gets half a stop`() {
        assertEquals(0.5f, ExposureSteering(config).step(config.faceLumaLow - 5f, nowMs = 0), 0f)
    }

    @Test
    fun `a well lit face is left alone`() {
        assertEquals(0f, ExposureSteering(config).step(120f, nowMs = 0), 0f)
    }

    @Test
    fun `a washed out face is darkened`() {
        assertEquals(-0.5f, ExposureSteering(config).step(config.faceLumaHigh + 10f, nowMs = 0), 0f)
    }

    @Test
    fun `waits for a change to show before the next one`() {
        val steering = ExposureSteering(config)
        steering.step(25f, nowMs = 0)
        assertEquals(0f, steering.step(25f, nowMs = config.exposureStepIntervalMs - 1), 0f)
        assertEquals(1f, steering.step(25f, nowMs = config.exposureStepIntervalMs), 0f)
    }
}
