package com.sumonlogs.faceverify.core.engine

/**
 * Keeps a hint from flickering. A problem is only reported once it has lasted [minFrames] frames in a
 * row, so a single dark frame from the camera's auto-exposure doesn't flash "find more light". The
 * frame itself is still rejected by the caller; this only decides what the user is told.
 */
class IssueDebounce(private val minFrames: Int) {
    private var current: QualityIssue? = null
    private var streak = 0

    /** Returns [issue] once it has been seen [minFrames] times in a row, otherwise null. */
    fun report(issue: QualityIssue?): QualityIssue? {
        if (issue == null) {
            reset()
            return null
        }
        if (issue == current) streak++ else {
            current = issue
            streak = 1
        }
        return if (streak >= minFrames) issue else null
    }

    fun reset() {
        current = null
        streak = 0
    }
}
