package com.sumonlogs.faceverify.harness

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException
import kotlin.math.roundToInt

/** What the app did against what was true, as the tester judged it after a check. */
enum class Outcome(val code: String) {
    /** The right person was let in. */
    Accepted("ta"),

    /** A stranger was turned away. */
    Refused("tr"),

    /** The wrong person was let in: a stranger, or another enrolled person. */
    WrongAccepted("fa"),

    /** The right person was turned away (no match, ambiguous, liveness or quality failure). */
    WrongRefused("fr"),
    ;

    val right: Boolean get() = this == Accepted || this == Refused

    companion object {
        /** [matched]: the app let someone in. [correct]: the tester says that was the right thing to do. */
        fun of(matched: Boolean, correct: Boolean): Outcome = when {
            matched && correct -> Accepted
            !matched && correct -> Refused
            matched -> WrongAccepted
            else -> WrongRefused
        }

        fun fromCode(code: String): Outcome? = entries.firstOrNull { it.code == code }
    }
}

data class Verdict(val modelId: String, val outcome: Outcome)

/** Right and wrong answers for one model. Pure. */
data class VerdictTally(
    val accepted: Int = 0,
    val refused: Int = 0,
    val wrongAccepted: Int = 0,
    val wrongRefused: Int = 0,
) {
    val right: Int get() = accepted + refused
    val wrong: Int get() = wrongAccepted + wrongRefused
    val total: Int get() = right + wrong

    /** Null before the first answer. */
    val rightPercent: Int? get() = if (total == 0) null else (right * 100f / total).roundToInt()

    companion object {
        fun of(verdicts: List<Verdict>, modelId: String): VerdictTally {
            val forModel = verdicts.filter { it.modelId == modelId }.map { it.outcome }
            return VerdictTally(
                accepted = forModel.count { it == Outcome.Accepted },
                refused = forModel.count { it == Outcome.Refused },
                wrongAccepted = forModel.count { it == Outcome.WrongAccepted },
                wrongRefused = forModel.count { it == Outcome.WrongRefused },
            )
        }
    }
}

/** Keeps the tester's right / wrong answers on this phone (harness only). */
class VerdictLog(private val context: Context) {
    private val key = stringPreferencesKey("verdicts")

    val verdicts: Flow<List<Verdict>> = context.harnessDataStore.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map { prefs -> prefs[key].orEmpty().split('|').mapNotNull(::parse) }

    suspend fun add(verdict: Verdict) {
        context.harnessDataStore.edit { prefs ->
            val kept = prefs[key].orEmpty().split('|').filter { it.isNotBlank() }.takeLast(MAX_VERDICTS - 1)
            prefs[key] = (kept + format(verdict)).joinToString("|")
        }
    }

    suspend fun clear(modelId: String) {
        context.harnessDataStore.edit { prefs ->
            prefs[key] = prefs[key].orEmpty().split('|').mapNotNull(::parse)
                .filterNot { it.modelId == modelId }
                .joinToString("|", transform = ::format)
        }
    }

    private fun format(v: Verdict) = "${v.modelId};${v.outcome.code}"

    private fun parse(line: String): Verdict? {
        val parts = line.split(';')
        if (parts.size != 2) return null
        return Outcome.fromCode(parts[1])?.let { Verdict(parts[0], it) }
    }

    private companion object {
        const val MAX_VERDICTS = 2_000
    }
}
