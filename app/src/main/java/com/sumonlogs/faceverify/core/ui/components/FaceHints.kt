package com.sumonlogs.faceverify.core.ui.components

import androidx.annotation.StringRes
import com.sumonlogs.faceverify.R
import com.sumonlogs.faceverify.core.engine.QualityIssue

/** What to tell the person when a frame isn't usable. */
@StringRes
fun QualityIssue.hintRes(): Int = when (this) {
    QualityIssue.NoFace -> R.string.face_hint_no_face
    QualityIssue.MultipleFaces -> R.string.face_hint_multiple
    QualityIssue.TooFar -> R.string.face_hint_too_far
    QualityIssue.TooClose -> R.string.face_hint_too_close
    QualityIssue.NotCentred -> R.string.face_hint_not_centred
    QualityIssue.TurnedAway -> R.string.face_hint_turned
    QualityIssue.Tilted -> R.string.face_hint_tilted
    QualityIssue.EyesClosed -> R.string.face_hint_eyes
}
