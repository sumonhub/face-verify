package com.banglatrac.workopolo.face.core.ui.reenroll

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.banglatrac.workopolo.face.R
import com.banglatrac.workopolo.face.core.api.FaceMismatchSummary
import com.banglatrac.workopolo.face.core.api.ReenrollReason
import com.banglatrac.workopolo.face.core.api.ReenrollRequest
import com.banglatrac.workopolo.face.core.ui.components.AiText
import com.banglatrac.workopolo.face.core.ui.components.DarkFaceBoard
import com.banglatrac.workopolo.face.core.ui.components.DarkHeader
import com.banglatrac.workopolo.face.core.ui.components.FaceButton
import com.banglatrac.workopolo.face.core.ui.components.FaceButtonStyle
import com.banglatrac.workopolo.face.core.ui.components.FaceGlyph
import com.banglatrac.workopolo.face.core.ui.components.FaceIcon
import com.banglatrac.workopolo.face.core.ui.components.FaceOval
import com.banglatrac.workopolo.face.core.ui.components.FacePill
import com.banglatrac.workopolo.face.core.ui.components.HeroRings
import com.banglatrac.workopolo.face.core.ui.components.LightHeader
import com.banglatrac.workopolo.face.core.ui.components.LivePulseDot
import com.banglatrac.workopolo.face.core.ui.components.highlighted
import com.banglatrac.workopolo.face.core.ui.theme.FaceColors
import com.banglatrac.workopolo.face.core.ui.theme.FaceTypography
import org.koin.androidx.compose.koinViewModel
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

// ---------------------------------------------------------------- Face didn't match · repeated

@Composable
fun FaceMismatchScreen(
    userName: String?,
    summary: FaceMismatchSummary,
    onRequestReenroll: () -> Unit,
    onTryAgain: () -> Unit,
    onClose: () -> Unit,
) {
    DarkFaceBoard {
        DarkHeader(stringResource(R.string.face_check_title), stringResource(R.string.face_cd_close), onClose)
        FaceOval(
            width = 230.dp,
            height = 300.dp,
            progress = 1f,
            ringColor = FaceColors.MismatchRed,
            overlay = FaceColors.MismatchTint,
            modifier = Modifier.padding(top = 36.dp),
        )
        Column(
            Modifier.padding(top = 26.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FacePill(
                text = stringResource(R.string.face_mismatch_pill),
                background = FaceColors.MismatchRed.copy(alpha = 0.16f),
                content = FaceColors.MismatchRedText,
                glyph = FaceGlyph.Alert,
                glyphSize = 14.dp,
                glyphStroke = 2.4f,
            )
            val title = if (userName.isNullOrBlank()) {
                stringResource(R.string.face_mismatch)
            } else {
                stringResource(R.string.face_mismatch_named, userName)
            }
            AiText(AnnotatedString(title), FaceTypography.HeadlineCompact, FaceColors.White, textAlign = TextAlign.Center)
            Text(
                text = highlighted(
                    stringResource(R.string.face_mismatch_body),
                    stringResource(R.string.face_mismatch_count, summary.mismatchDays, summary.windowDays),
                    FaceColors.White,
                ),
                style = FaceTypography.BodyRelaxed,
                color = FaceColors.white(0.72f),
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(max = 300.dp),
            )
        }
        Spacer(Modifier.weight(1f).height(24.dp))
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            FaceButton(stringResource(R.string.face_request_reenroll), onRequestReenroll, glyph = FaceGlyph.Person)
            FaceButton(stringResource(R.string.face_try_again), onTryAgain, style = FaceButtonStyle.Ghost)
            Text(
                stringResource(R.string.face_mismatch_note),
                style = FaceTypography.Caption,
                color = FaceColors.white(0.6f),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
            )
        }
    }
}

// ---------------------------------------------------------------- Request face re-enrollment

@Composable
fun ReenrollRequestRoute(
    summary: FaceMismatchSummary,
    onBack: () -> Unit,
    onSent: (ReenrollRequest) -> Unit,
    viewModel: ReenrollRequestViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val currentOnSent by rememberUpdatedState(onSent)
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is ReenrollRequestEffect.Sent -> currentOnSent(effect.request)
            }
        }
    }
    ReenrollRequestScreen(state = state, summary = summary, onBack = onBack, onIntent = viewModel::onIntent)
}

@Composable
fun ReenrollRequestScreen(
    state: ReenrollRequestState,
    summary: FaceMismatchSummary,
    onBack: () -> Unit,
    onIntent: (ReenrollRequestIntent) -> Unit,
) {
    LightBoard(
        title = stringResource(R.string.face_reenroll_title),
        onBack = onBack,
        footer = {
            FaceButton(
                text = stringResource(R.string.face_reenroll_send),
                onClick = { onIntent(ReenrollRequestIntent.Send) },
                enabled = !state.sending,
            )
        },
    ) {
        HeroCard(gap = 14.dp, padding = 18.dp) {
            AiText(
                text = highlighted(
                    stringResource(R.string.face_reenroll_ai),
                    stringResource(R.string.face_mismatch_count, summary.mismatchDays, summary.windowDays),
                    FaceColors.White,
                ),
                style = FaceTypography.AiMessageCompact,
                color = FaceColors.white(0.82f),
                logoSize = 28.sp,
                caretHeight = 17.sp,
            )
            if (summary.recentMismatches.isNotEmpty()) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .background(FaceColors.white(0.06f), RoundedCornerShape(16.dp))
                        .border(1.dp, FaceColors.white(0.10f), RoundedCornerShape(16.dp))
                        .padding(horizontal = 14.dp, vertical = 2.dp),
                ) {
                    summary.recentMismatches.forEachIndexed { index, at ->
                        if (index > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(FaceColors.white(0.10f)))
                        MismatchRow(at)
                    }
                }
            }
        }
        Column(
            Modifier
                .fillMaxWidth()
                .whiteCard()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(stringResource(R.string.face_reenroll_what_changed), style = FaceTypography.CardHeading, color = FaceColors.Navy)
            Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ReasonOption(R.string.face_reason_look, ReenrollReason.LookChanged, state.reason, onIntent)
                ReasonOption(R.string.face_reason_photo, ReenrollReason.PhotoOutdated, state.reason, onIntent)
                ReasonOption(R.string.face_reason_other, ReenrollReason.Other, state.reason, onIntent)
            }
            Text(
                stringResource(R.string.face_reenroll_note_label),
                style = FaceTypography.LabelSemiBold,
                color = FaceColors.TextMuted,
                modifier = Modifier.padding(top = 2.dp),
            )
            BasicTextField(
                value = state.note,
                onValueChange = { onIntent(ReenrollRequestIntent.NoteChanged(it)) },
                textStyle = FaceTypography.Body.copy(color = FaceColors.TextPrimary),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .border(1.dp, FaceColors.BorderDefault, RoundedCornerShape(14.dp))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            )
            if (state.sendFailed) {
                Text(stringResource(R.string.face_reenroll_send_failed), style = FaceTypography.Label, color = FaceColors.MismatchRed)
            }
        }
    }
}

@Composable
private fun MismatchRow(atMillis: Long) {
    val time = Instant.ofEpochMilli(atMillis).atZone(ZoneId.systemDefault())
    val locale = Locale.getDefault()
    Row(Modifier.fillMaxWidth().padding(vertical = 9.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(
            Modifier.size(34.dp).background(FaceColors.MismatchRed.copy(alpha = 0.14f), RoundedCornerShape(11.dp)),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(DateTimeFormatter.ofPattern("EEE", locale).format(time).uppercase(locale), style = FaceTypography.DayName, color = FaceColors.MismatchRedText)
            Text(DateTimeFormatter.ofPattern("d", locale).format(time), style = FaceTypography.DayNumber, color = FaceColors.MismatchRedText)
        }
        Text(stringResource(R.string.face_reenroll_row), style = FaceTypography.ListItem, color = FaceColors.White, modifier = Modifier.weight(1f))
        Text(formatTime(atMillis), style = FaceTypography.CaptionSmall, color = FaceColors.white(0.6f))
    }
}

@Composable
private fun ReasonOption(
    label: Int,
    reason: ReenrollReason,
    selected: ReenrollReason,
    onIntent: (ReenrollRequestIntent) -> Unit,
) {
    val isSelected = reason == selected
    val shape = RoundedCornerShape(14.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .border(if (isSelected) 2.dp else 1.dp, if (isSelected) FaceColors.ButtonBlue else FaceColors.BorderDefault, shape)
            .background(if (isSelected) FaceColors.SelectedBg else FaceColors.White, shape)
            .selectable(selected = isSelected, role = Role.RadioButton) { onIntent(ReenrollRequestIntent.SelectReason(reason)) }
            .padding(horizontal = 12.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            Modifier
                .size(18.dp)
                .background(FaceColors.White, CircleShape)
                .border(if (isSelected) 5.dp else 2.dp, if (isSelected) FaceColors.ButtonBlue else FaceColors.BorderDefault, CircleShape),
        )
        Text(
            stringResource(label),
            style = FaceTypography.BodySemiBold,
            color = if (isSelected) FaceColors.Navy else FaceColors.TextPrimary,
        )
    }
}

// ---------------------------------------------------------------- Re-enrollment requested

@Composable
fun ReenrollRequestedScreen(request: ReenrollRequest, onBack: () -> Unit) {
    LightBoard(title = stringResource(R.string.face_reenroll_title), onBack = onBack, contentBottom = 16.dp) {
        HeroCard(gap = 18.dp, padding = 20.dp) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.face_reenroll_request_id, request.id), style = FaceTypography.Meta, color = FaceColors.white(0.75f))
                Text("${formatDate(request.sentAtMillis)} · ${formatTime(request.sentAtMillis)}", style = FaceTypography.CaptionSmall, color = FaceColors.white(0.7f))
            }
            AiText(
                text = buildAnnotatedString {
                    withStyle(SpanStyle(color = FaceColors.White, fontWeight = FontWeight.Bold)) {
                        append(stringResource(R.string.face_reenroll_sent_bold))
                    }
                    append(" ")
                    append(stringResource(R.string.face_reenroll_sent_rest))
                },
                style = FaceTypography.AiMessage,
                color = FaceColors.white(0.82f),
                logoSize = 28.sp,
                caretHeight = 18.sp,
            )
            Column {
                TimelineItem(
                    title = stringResource(R.string.face_timeline_sent),
                    subtitle = stringResource(R.string.face_timeline_sent_at, formatDate(request.sentAtMillis), formatTime(request.sentAtMillis)),
                    titleColor = FaceColors.White,
                    connector = true,
                ) {
                    Box(Modifier.size(28.dp).background(FaceColors.SuccessBadge, CircleShape), contentAlignment = Alignment.Center) {
                        FaceIcon(FaceGlyph.Check, null, 14.dp, 3f, FaceColors.White)
                    }
                }
                TimelineItem(
                    title = stringResource(R.string.face_timeline_waiting),
                    subtitle = stringResource(R.string.face_timeline_waiting_sub),
                    titleColor = FaceColors.White,
                    connector = true,
                ) {
                    Box(Modifier.size(28.dp).border(2.dp, FaceColors.Amber, CircleShape), contentAlignment = Alignment.Center) {
                        LivePulseDot(10.dp, FaceColors.Amber)
                    }
                }
                TimelineItem(
                    title = stringResource(R.string.face_timeline_setup),
                    subtitle = stringResource(R.string.face_timeline_setup_sub),
                    titleColor = FaceColors.white(0.6f),
                    connector = false,
                ) {
                    Box(Modifier.size(28.dp).border(2.dp, FaceColors.white(0.25f), CircleShape))
                }
            }
        }
        Column(
            Modifier
                .fillMaxWidth()
                .whiteCard()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(stringResource(R.string.face_until_then), style = FaceTypography.CardHeading, color = FaceColors.Navy)
            UntilThenRow(FaceGlyph.Check, 2.6f, FaceColors.StateSuccess, R.string.face_until_then_attendance)
            UntilThenRow(FaceGlyph.Clock, 2.2f, FaceColors.StateWarning, R.string.face_until_then_approval)
        }
        Spacer(Modifier.weight(1f))
        FaceButton(stringResource(R.string.face_back_to_attendance), onBack, style = FaceButtonStyle.Outline)
    }
}

@Composable
private fun TimelineItem(
    title: String,
    subtitle: String,
    titleColor: Color,
    connector: Boolean,
    marker: @Composable () -> Unit,
) {
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.width(28.dp).fillMaxHeight()) {
            if (connector) {
                Box(
                    Modifier
                        .padding(top = 32.dp)
                        .offset(x = 13.dp)
                        .width(2.dp)
                        .fillMaxHeight()
                        .background(FaceColors.white(0.14f)),
                )
            }
            marker()
        }
        Column(
            Modifier.padding(top = 3.dp, bottom = if (connector) 16.dp else 0.dp),
            verticalArrangement = Arrangement.spacedBy(1.dp),
        ) {
            Text(title, style = FaceTypography.BodyStrong, color = titleColor)
            Text(subtitle, style = FaceTypography.CaptionSmall, color = FaceColors.white(0.6f))
        }
    }
}

@Composable
private fun UntilThenRow(glyph: FaceGlyph, stroke: Float, tint: Color, text: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        FaceIcon(glyph, null, 16.dp, stroke, tint, Modifier.padding(top = 1.dp))
        Text(stringResource(text), style = FaceTypography.MetaRelaxed, color = FaceColors.TextMuted)
    }
}

// ---------------------------------------------------------------- Light board pieces

@Composable
private fun LightBoard(
    title: String,
    onBack: () -> Unit,
    contentBottom: Dp = 12.dp,
    footer: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .background(FaceColors.BgBase)
            .systemBarsPadding()
            .imePadding(),
    ) {
        LightHeader(title, stringResource(R.string.face_cd_back), onBack)
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, bottom = contentBottom),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content,
        )
        if (footer != null) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .shadow(8.dp, ambientColor = FaceColors.Navy, spotColor = FaceColors.Navy)
                    .background(FaceColors.White)
                    .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 20.dp),
            ) { footer() }
        }
    }
}

@Composable
private fun HeroCard(gap: Dp, padding: Dp, content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(24.dp)
    Box(
        Modifier
            .fillMaxWidth()
            .shadow(16.dp, shape, ambientColor = FaceColors.Navy, spotColor = FaceColors.Navy)
            .background(FaceColors.Navy, shape),
    ) {
        Box(Modifier.matchParentSize().clip(shape)) {
            HeroRings(Modifier.align(Alignment.TopEnd).offset(x = 70.dp, y = (-70).dp))
        }
        Column(Modifier.padding(padding), verticalArrangement = Arrangement.spacedBy(gap), content = content)
    }
}

private fun Modifier.whiteCard(): Modifier {
    val shape = RoundedCornerShape(20.dp)
    return this
        .shadow(6.dp, shape, ambientColor = FaceColors.Navy, spotColor = FaceColors.Navy)
        .background(FaceColors.White, shape)
        .border(1.dp, FaceColors.BorderSubtle, shape)
}

private fun formatDate(millis: Long): String =
    DateTimeFormatter.ofPattern("EEE, d MMM", Locale.getDefault()).format(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()))

private fun formatTime(millis: Long): String =
    DateTimeFormatter.ofPattern("hh:mm a", Locale.getDefault())
        .format(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()))
        .lowercase(Locale.getDefault())

// ---------------------------------------------------------------- Previews

private val PreviewSummary = FaceMismatchSummary(
    mismatchDays = 3,
    windowDays = 5,
    recentMismatches = listOf(1_791_436_920_000L, 1_791_264_600_000L, 1_791_177_600_000L),
)

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun FaceMismatchPreview() {
    FaceMismatchScreen("Mazba", PreviewSummary, onRequestReenroll = {}, onTryAgain = {}, onClose = {})
}

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun ReenrollRequestPreview() {
    ReenrollRequestScreen(
        ReenrollRequestState(note = "I started wearing glasses this month."),
        PreviewSummary,
        onBack = {},
        onIntent = {},
    )
}

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun ReenrollRequestedPreview() {
    ReenrollRequestedScreen(ReenrollRequest("FR-1042", 1_791_437_640_000L), onBack = {})
}
