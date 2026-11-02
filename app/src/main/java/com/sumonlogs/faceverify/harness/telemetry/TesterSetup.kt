package com.sumonlogs.faceverify.harness.telemetry

/** Who or what is in front of the camera for the next checks, as the tester says. */
enum class TestScenario(val code: String) {
    /** The tester is an enrolled person trying to get recognised. */
    Me("me"),

    /** Somebody who is not the enrolled person tries to pass as them. */
    SomeoneElse("other"),

    /** A printed or on-screen photo of somebody. */
    PhotoAttack("photo"),

    /** A video of somebody played on another screen. */
    ScreenReplay("replay"),
}

enum class Lighting(val code: String) {
    Bright("bright"),
    Normal("normal"),
    Dim("dim"),
    Backlit("backlit"),
    Outdoor("outdoor"),
}

/**
 * What the tester is about to try. Each check is saved with this, so the data says what the right
 * answer was without the tester labelling every single check.
 */
data class TesterSetup(
    val scenario: TestScenario = TestScenario.Me,
    /** The enrolled person the tester is, when it can't be guessed (several people enrolled). */
    val expectedFaceId: String? = null,
    val lighting: Lighting = Lighting.Normal,
    val glasses: Boolean = false,
    val faceCovered: Boolean = false,
)
