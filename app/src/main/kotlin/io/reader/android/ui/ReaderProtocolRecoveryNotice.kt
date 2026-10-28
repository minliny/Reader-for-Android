package io.reader.android.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import io.reader.android.runtime.CoreProtocolAction
import io.reader.android.runtime.CoreProtocolPresentation
import io.reader.android.runtime.CoreProtocolStage

/** Uses the existing Recovery category adapter, never a page-local message frame. */
@Composable
internal fun ReaderProtocolRecoveryNotice(
    presentation: CoreProtocolPresentation,
    onAction: (String) -> Unit,
) = key(presentation.presentationId) {
    val message = when (presentation.stage) {
        CoreProtocolStage.CONSUMED_ACK_FAILED -> "原操作记录已保留，确认尚未完成。"
        CoreProtocolStage.RESOLVED_FAULTS_AWAIT_ACCEPTANCE -> "原记录已核对，请确认恢复后续操作。"
        CoreProtocolStage.HOST_NON_ADMISSION_UNRECORDED -> "本次请求未被接受，原操作记录仍保留，请核对本次未接纳记录。"
        CoreProtocolStage.HOST_NON_ADMISSION_RECORDED -> "本次未接纳记录已保存，原操作记录仍保留。可确认恢复后续操作。"
        CoreProtocolStage.HOST_CONTROL_ERROR_RETAINED -> "原操作响应有一条错误待核对，原操作记录仍保留。"
        CoreProtocolStage.CONSUMER_QUALIFICATION_REQUIRED -> "原事件的保存状态尚未核清，原操作和记录继续保留。"
        CoreProtocolStage.OWNER_QUARANTINED -> "暂时无法继续新操作，原记录继续保留。"
        CoreProtocolStage.ORIGINAL_UNCERTAIN -> "原操作尚未确认，原记录继续保留。"
    }
    val label = when (presentation.action) {
        CoreProtocolAction.ACK_SAME_DELIVERY -> "核对并继续"
        CoreProtocolAction.ACCEPT_RESOLVED_FAULTS -> "确认恢复后续操作"
        CoreProtocolAction.RECORD_CONTROL_ERROR -> "核对原错误"
        CoreProtocolAction.RECORD_NON_ADMISSION -> "核对未接纳记录"
        null -> ""
    }
    ReaderRecoveryNotice(
        message = if (presentation.actionFailed) "$message 本次核对未完成，原记录继续保留。" else message,
        busy = presentation.busy,
        onReconcile = if (presentation.action != null) ({ onAction(presentation.presentationId) }) else null,
        actionLabel = label,
        actionTag = "reader_protocol_recovery_${presentation.presentationId}",
    )
    // Hiding/unmounting this notice has no cancel/retry/retire side effect.
}
