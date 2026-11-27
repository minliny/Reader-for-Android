package io.reader.rebuild.features.source

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.reader.core.protocol.M2RuleSubData
import com.reader.core.protocol.M2RulesMutationReceiptRuleSubPut
import com.reader.core.theme.ReaderDesignMetrics as Metrics
import io.reader.rebuild.design.LocalReaderColors
import io.reader.rebuild.design.readerTextStyle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
internal fun SubscriptionEditor(
    subscription: M2RuleSubData?,
    coordinator: SourceManagementCoordinator,
    onBack: () -> Unit,
) {
    val colors = LocalReaderColors.current
    val scope = rememberCoroutineScope()
    val unresolved by coordinator.unresolvedOperation.collectAsState()
    var name by remember(subscription?.id) { mutableStateOf(subscription?.name.orEmpty()) }
    var url by remember(subscription?.id) { mutableStateOf(subscription?.url.orEmpty()) }
    var saving by remember { mutableStateOf(false) }
    var issue by remember { mutableStateOf<String?>(null) }
    SourcePage(if (subscription == null) "新增规则订阅" else "编辑规则订阅", onBack) {
        Column(
            Modifier.sourcePageWidth().verticalScroll(rememberScrollState()).padding(Metrics.screenInset.dp),
            verticalArrangement = Arrangement.spacedBy(Metrics.spaceSmall.dp),
        ) {
            OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth().testTag("source.subscription.name"),
                label = { Text("订阅名称", style = readerTextStyle(Metrics.rowDetail)) },
                textStyle = readerTextStyle(Metrics.rowLabel), singleLine = true)
            OutlinedTextField(url, { url = it }, Modifier.fillMaxWidth().testTag("source.subscription.url"),
                label = { Text("规则包网址", style = readerTextStyle(Metrics.rowDetail)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                textStyle = readerTextStyle(Metrics.rowLabel), singleLine = true)
            issue?.let { Text(it, color = colors.danger, style = readerTextStyle(Metrics.rowDetail)) }
            Button(onClick = {
                saving = true
                issue = null
                scope.launch {
                    try {
                        check(coordinator.saveSubscription(name.trim(), url.trim(), subscription) is M2RulesMutationReceiptRuleSubPut)
                        onBack()
                    } catch (failure: CancellationException) {
                        throw failure
                    } catch (_: Exception) {
                        issue = "订阅保存未完成，已保留编辑内容。"
                    } finally {
                        saving = false
                    }
                }
            }, enabled = !saving && unresolved == null && name.isNotBlank() && validSourceDocumentUrl(url),
                modifier = Modifier.testTag("source.subscription.save")) {
                Text(if (saving) "保存中" else "保存", style = readerTextStyle(Metrics.rowLabel))
            }
        }
    }
}
