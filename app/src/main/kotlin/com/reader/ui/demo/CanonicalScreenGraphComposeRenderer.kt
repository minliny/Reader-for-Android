package com.reader.ui.demo

import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.reader.android.R
import com.reader.ui.shell.DemoBackBar
import com.reader.ui.shell.DemoBottomNav
import com.reader.ui.shell.DemoTopBar
import com.reader.ui.theme.ReaderTextStyles
import com.reader.ui.theme.ReaderThemeResolver
import com.reader.ui.theme.readerExtraColors
import io.reader.ui.contract.ScreenGraphComponentNode
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull

private val canonicalPlanner by lazy(CanonicalScreenGraphPlanner::loadCanonical)
private val canonicalAdapters by lazy(AndroidCanonicalComponentAdapterRegistry::loadCanonical)

/**
 * Real generated-contract renderer entry. It is intentionally not production
 * authority yet: [DemoRouteScreen] observes the same plan in Shadow while the
 * existing route renderer stays live.
 */
@Composable
internal fun CanonicalScreenGraphRouteRenderer(
    query: CanonicalScreenGraphQuery,
    onBack: () -> Unit,
    onNavigate: (String) -> Unit,
    onAction: (CanonicalComponentAction) -> Unit
) {
    val plan = remember(query) { canonicalPlanner.plan(query) }
    when (plan) {
        is CanonicalRouteRenderPlan.VisibleFailure -> CanonicalRendererFailureSurface(
            code = plan.code,
            message = plan.message,
            identity = plan.routeId
        )
        is CanonicalRouteRenderPlan.Ready -> Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            plan.variant.actionGaps.forEach { gap ->
                CanonicalRendererFailureSurface(
                    code = "CANONICAL_ACTION_BINDING_GAP",
                    message = "${gap.property}: ${gap.reason}",
                    identity = gap.componentId
                )
            }
            plan.variant.components.forEach { node ->
                CanonicalComponentNodeRenderer(
                    routeId = plan.query.routeId,
                    variantId = plan.variant.variantId,
                    routeTitle = plan.requestedRoute.title,
                    node = node,
                    onBack = onBack,
                    onNavigate = onNavigate,
                    onAction = onAction
                )
            }
        }
    }
}

@Composable
private fun CanonicalComponentNodeRenderer(
    routeId: String,
    variantId: String,
    routeTitle: String,
    node: ScreenGraphComponentNode,
    onBack: () -> Unit,
    onNavigate: (String) -> Unit,
    onAction: (CanonicalComponentAction) -> Unit
) {
    val model = CanonicalComponentRenderModel(routeId, variantId, node, routeTitle)
    val accessibility = model.accessibility()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("canonical-screen-graph:${node.id}")
            .semantics(mergeDescendants = false) {
                contentDescription = accessibility.contentDescription
                accessibility.stateDescription?.let { stateDescription = it }
                if (accessibility.enabled == false) disabled()
                accessibility.selected?.let { selected = it }
                accessibility.progress?.let {
                    progressBarRangeInfo = ProgressBarRangeInfo(it.toFloat().coerceIn(0f, 1f), 0f..1f)
                }
                if (node.type == io.reader.ui.contract.ComponentType.Button) role = Role.Button
            }
    ) {
    when (val entry = canonicalAdapters.entry(node.type)) {
        is AndroidCanonicalComponentAdapterEntry.VisibleFailure -> {
            CanonicalRendererFailureSurface(entry.code, entry.reason, node.id)
            CanonicalChildren(model, onBack, onNavigate, onAction)
        }
        is AndroidCanonicalComponentAdapterEntry.Supported -> when (entry.kind) {
            AndroidCanonicalComponentAdapterKind.AppTopBar -> {
                val title = model.requiredString("title")
                if (title == null) model.invalidProp("title") else DemoTopBar(title)
                CanonicalChildren(model, onBack, onNavigate, onAction)
            }
            AndroidCanonicalComponentAdapterKind.BackTopBar -> {
                val title = model.requiredString("title")
                if (title == null) model.invalidProp("title") else DemoBackBar(title, onBack)
                CanonicalChildren(model, onBack, onNavigate, onAction)
            }
            AndroidCanonicalComponentAdapterKind.BottomNav -> {
                val selected = model.requiredString("selected")
                if (selected == null) model.invalidProp("selected")
                else DemoBottomNav(activeRoute = selected, onNavigate = onNavigate)
                CanonicalChildren(model, onBack, onNavigate, onAction)
            }
            AndroidCanonicalComponentAdapterKind.BookCard -> {
                CanonicalBookCard(model)
                CanonicalChildren(model, onBack, onNavigate, onAction)
            }
            AndroidCanonicalComponentAdapterKind.BookshelfSection -> CanonicalContainer(
                model = model,
                title = model.optionalString("title"),
                onBack = onBack,
                onNavigate = onNavigate,
                onAction = onAction
            )
            AndroidCanonicalComponentAdapterKind.ShelfSectionHeader -> {
                val title = model.requiredString("title")
                if (title == null) model.invalidProp("title") else Text(
                    text = title,
                    style = ReaderTextStyles.sectionTitle,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)
                )
                CanonicalChildren(model, onBack, onNavigate, onAction)
            }
            AndroidCanonicalComponentAdapterKind.Loading -> {
                DemoLoadingSkeleton(lines = 4)
                CanonicalChildren(model, onBack, onNavigate, onAction)
            }
            AndroidCanonicalComponentAdapterKind.Empty -> {
                CanonicalStateBlock(model, R.drawable.reader_ic_info, false)
                CanonicalChildren(model, onBack, onNavigate, onAction)
            }
            AndroidCanonicalComponentAdapterKind.Error -> {
                CanonicalStateBlock(model, R.drawable.reader_ic_warning, true)
                CanonicalChildren(model, onBack, onNavigate, onAction)
            }
            AndroidCanonicalComponentAdapterKind.Offline -> {
                CanonicalStateBlock(model, R.drawable.reader_ic_offline, true)
                CanonicalChildren(model, onBack, onNavigate, onAction)
            }
            AndroidCanonicalComponentAdapterKind.Permission -> {
                CanonicalStateBlock(model, R.drawable.reader_ic_warning, false)
                CanonicalChildren(model, onBack, onNavigate, onAction)
            }
            AndroidCanonicalComponentAdapterKind.Toast -> {
                val message = model.optionalString("message") ?: model.optionalString("title")
                if (message == null) model.invalidProp("message|title") else DemoToast(message)
                CanonicalChildren(model, onBack, onNavigate, onAction)
            }
            AndroidCanonicalComponentAdapterKind.List,
            AndroidCanonicalComponentAdapterKind.FormSection,
            AndroidCanonicalComponentAdapterKind.Content -> CanonicalContainer(
                model = model,
                title = model.optionalString("title"),
                onBack = onBack,
                onNavigate = onNavigate,
                onAction = onAction
            )
            AndroidCanonicalComponentAdapterKind.ListRow -> {
                CanonicalListRow(model)
                CanonicalChildren(model, onBack, onNavigate, onAction)
            }
            AndroidCanonicalComponentAdapterKind.Button -> {
                CanonicalBoundButton(model, onAction)
                CanonicalChildren(model, onBack, onNavigate, onAction)
            }
            AndroidCanonicalComponentAdapterKind.GenericStructure,
            AndroidCanonicalComponentAdapterKind.GenericText -> CanonicalGenericSurface(
                model = model,
                family = entry.kind,
                onBack = onBack,
                onNavigate = onNavigate,
                onAction = onAction
            )
            AndroidCanonicalComponentAdapterKind.GenericState -> CanonicalGenericStateSurface(
                model = model,
                onBack = onBack,
                onNavigate = onNavigate,
                onAction = onAction
            )
            AndroidCanonicalComponentAdapterKind.GenericContinueCard -> CanonicalContinueReadingCard(
                model = model,
                onBack = onBack,
                onNavigate = onNavigate,
                onAction = onAction
            )
            AndroidCanonicalComponentAdapterKind.GenericGrid -> CanonicalBookGrid(
                model = model,
                onBack = onBack,
                onNavigate = onNavigate,
                onAction = onAction
            )
            AndroidCanonicalComponentAdapterKind.GenericProgress -> CanonicalProgressSurface(
                model = model,
                onBack = onBack,
                onNavigate = onNavigate,
                onAction = onAction
            )
            AndroidCanonicalComponentAdapterKind.GenericControl -> CanonicalStaticControlSurface(
                model = model,
                onBack = onBack,
                onNavigate = onNavigate,
                onAction = onAction
            )
            AndroidCanonicalComponentAdapterKind.GenericBackground -> CanonicalBackgroundSurface(model)
            AndroidCanonicalComponentAdapterKind.GenericDialog -> CanonicalDialogSurface(
                model = model,
                onBack = onBack,
                onNavigate = onNavigate,
                onAction = onAction
            )
        }
    }
        CanonicalEventEvidence(model)
    }
}

@Composable
private fun CanonicalContainer(
    model: CanonicalComponentRenderModel,
    title: String?,
    onBack: () -> Unit,
    onNavigate: (String) -> Unit,
    onAction: (CanonicalComponentAction) -> Unit
) {
    val message = model.optionalString("message")
    if (model.node.children.isEmpty() && title == null && message == null) {
        CanonicalRendererFailureSurface(
            code = "CANONICAL_STRUCTURAL_CONTENT_MISSING",
            message = "Structural adapter has neither children nor canonical text props",
            identity = model.node.id
        )
        return
    }
    DemoSectionCard(title = title) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (message != null && message != title) {
                Text(message, style = MaterialTheme.typography.bodyMedium)
            }
            CanonicalChildren(model, onBack, onNavigate, onAction)
        }
    }
}

/** Stable diagnostic family used for canonical types without a dedicated primitive. */
@Composable
private fun CanonicalGenericSurface(
    model: CanonicalComponentRenderModel,
    family: AndroidCanonicalComponentAdapterKind,
    onBack: () -> Unit,
    onNavigate: (String) -> Unit,
    onAction: (CanonicalComponentAction) -> Unit
) {
    val descriptor = model.genericDescriptor()
    DemoSectionCard(title = descriptor.title) {
        Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Text(
                text = "${descriptor.identity} · ${family.name.removePrefix("Generic")}",
                style = MaterialTheme.typography.labelSmall,
                color = readerExtraColors().muted
            )
            descriptor.props.forEach { (key, value) ->
                if (key !in setOf("title", "label", "message")) {
                    CanonicalPropRow(key, value)
                }
            }
            val message = descriptor.message
            if (message != null && message != descriptor.title) {
                Text(message, style = MaterialTheme.typography.bodyMedium)
            }
            CanonicalActionButtons(model, onAction)
            CanonicalChildren(model, onBack, onNavigate, onAction)
        }
    }
}

@Composable
private fun CanonicalGenericStateSurface(
    model: CanonicalComponentRenderModel,
    onBack: () -> Unit,
    onNavigate: (String) -> Unit,
    onAction: (CanonicalComponentAction) -> Unit
) {
    val title = model.optionalString("title") ?: model.routeTitle
    val message = model.optionalString("message") ?: "${model.variantId} · ${model.node.id}"
    DemoStateBlock(
        iconRes = when (model.optionalString("variant")) {
            "error" -> R.drawable.reader_ic_warning
            "offline" -> R.drawable.reader_ic_offline
            else -> R.drawable.reader_ic_info
        },
        title = title,
        body = message
    )
    CanonicalGenericSurface(
        model = model,
        family = AndroidCanonicalComponentAdapterKind.GenericState,
        onBack = onBack,
        onNavigate = onNavigate,
        onAction = onAction
    )
}

@Composable
private fun CanonicalContinueReadingCard(
    model: CanonicalComponentRenderModel,
    onBack: () -> Unit,
    onNavigate: (String) -> Unit,
    onAction: (CanonicalComponentAction) -> Unit
) {
    CanonicalBookCard(model)
    CanonicalChildren(model, onBack, onNavigate, onAction)
}

@Composable
private fun CanonicalBookGrid(
    model: CanonicalComponentRenderModel,
    onBack: () -> Unit,
    onNavigate: (String) -> Unit,
    onAction: (CanonicalComponentAction) -> Unit
) {
    val viewMode = model.optionalString("viewMode")
    if (viewMode != null && viewMode !in setOf("cover", "list")) {
        model.invalidProp("viewMode=cover|list")
        return
    }
    val columns = if (viewMode == "list") 1 else 3
    DemoSectionCard {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            model.node.children.chunked(columns).forEach { rowChildren ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    rowChildren.forEach { child ->
                        Box(Modifier.weight(1f)) {
                            CanonicalComponentNodeRenderer(
                                routeId = model.routeId,
                                variantId = model.variantId,
                                routeTitle = model.routeTitle,
                                node = child,
                                onBack = onBack,
                                onNavigate = onNavigate,
                                onAction = onAction
                            )
                        }
                    }
                    repeat(columns - rowChildren.size) { Box(Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
private fun CanonicalProgressSurface(
    model: CanonicalComponentRenderModel,
    onBack: () -> Unit,
    onNavigate: (String) -> Unit,
    onAction: (CanonicalComponentAction) -> Unit
) {
    val progress = (model.props["progress"] as? JsonPrimitive)?.doubleOrNull
    val chapter = model.optionalString("chapterTitle")
    val title = model.optionalString("title")
    if (progress == null && chapter == null) {
        model.invalidProp("progress|chapterTitle")
        return
    }
    DemoSectionCard(title = title) {
        if (progress != null) {
            DemoReaderProgressBar(progress.toFloat(), chapter.orEmpty())
        } else {
            Text(chapter.orEmpty(), style = ReaderTextStyles.infoLayer)
        }
        CanonicalChildren(model, onBack, onNavigate, onAction)
    }
}

@Composable
private fun CanonicalStaticControlSurface(
    model: CanonicalComponentRenderModel,
    onBack: () -> Unit,
    onNavigate: (String) -> Unit,
    onAction: (CanonicalComponentAction) -> Unit
) {
    val title = model.requiredString("title")
    if (title == null) {
        model.invalidProp("title")
        return
    }
    DemoSectionCard(title = title) {
        CanonicalChildren(model, onBack, onNavigate, onAction)
    }
}

@Composable
private fun CanonicalBackgroundSurface(model: CanonicalComponentRenderModel) {
    val theme = model.requiredString("theme")
    if (theme == null || ReaderThemeResolver.THEMES.none { it.id == theme }) {
        model.invalidProp("known theme")
        return
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .background(ReaderThemeResolver.swatchColor(theme)),
        contentAlignment = Alignment.Center
    ) {
        Text(theme, style = ReaderTextStyles.infoLayer)
    }
}

@Composable
private fun CanonicalDialogSurface(
    model: CanonicalComponentRenderModel,
    onBack: () -> Unit,
    onNavigate: (String) -> Unit,
    onAction: (CanonicalComponentAction) -> Unit
) {
    val title = model.requiredString("title")
    if (title == null) {
        model.invalidProp("title")
        return
    }
    DemoSectionCard(title = title) {
        model.optionalString("message")?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium)
        }
        CanonicalChildren(model, onBack, onNavigate, onAction)
    }
}

@Composable
private fun CanonicalActionButtons(
    model: CanonicalComponentRenderModel,
    onAction: (CanonicalComponentAction) -> Unit
) {
    model.triggerableActions().forEachIndexed { index, action ->
        val label = if (index == 0) {
            model.optionalString("label") ?: model.optionalString("action") ?: action.binding.event.name
        } else {
            action.binding.event.name
        }
        DemoButton(label = label, compact = true, onClick = { onAction(action) })
    }
}

@Composable
private fun CanonicalEventEvidence(model: CanonicalComponentRenderModel) {
    model.bindingObservations().forEach { observation ->
        Text(
            text = "executable binding · ${observation.binding.trigger} · " +
                "${observation.binding.event.name}: ${observation.binding.payload}",
            style = MaterialTheme.typography.labelSmall,
            color = readerExtraColors().muted
        )
    }
    model.stateEventObservations().forEach { observation ->
        Text(
            text = "state evidence only · ${observation.evidence.classification} · " +
                "${observation.evidence.event.name}: ${observation.evidence.payload}",
            style = MaterialTheme.typography.labelSmall,
            color = readerExtraColors().muted
        )
    }
}

@Composable
private fun CanonicalPropRow(key: String, value: JsonElement) {
    val rendered = when (value) {
        JsonNull -> "null"
        is JsonPrimitive -> value.content
        else -> value.toString()
    }.let { if (it.length <= 220) it else it.take(217) + "..." }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = key,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(0.32f)
        )
        Text(
            text = rendered,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.weight(0.68f)
        )
    }
}

@Composable
private fun CanonicalBookCard(model: CanonicalComponentRenderModel) {
    val title = model.requiredString("title")
    val author = model.requiredString("author")
    val coverKey = model.requiredString("coverKey")
    if (title == null || author == null || coverKey == null) {
        model.invalidProp("title|author|coverKey")
        return
    }
    DemoSectionCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            DemoBookCover(coverKey)
            Column {
                Text(title, style = ReaderTextStyles.bookTitle, color = MaterialTheme.colorScheme.onBackground)
                Text(author, style = ReaderTextStyles.bookAuthor, color = readerExtraColors().muted)
            }
        }
    }
}

@Composable
private fun CanonicalListRow(model: CanonicalComponentRenderModel) {
    val title = model.requiredString("title")
    if (title == null) {
        model.invalidProp("title")
        return
    }
    DemoListItem(
        title = title,
        subtitle = model.optionalString("subtitle") ?: model.optionalString("author")
    )
}

@Composable
private fun CanonicalBoundButton(
    model: CanonicalComponentRenderModel,
    onAction: (CanonicalComponentAction) -> Unit
) {
    val label = model.requiredString("label")
    val actions = model.triggerableActions()
    when {
        label == null -> model.invalidProp("label")
        actions.size != 1 -> CanonicalRendererFailureSurface(
            code = "CANONICAL_BUTTON_BINDING_MISSING",
            message = "Button requires exactly one typed ScreenGraphActionBinding; found ${actions.size}",
            identity = model.node.id
        )
        else -> DemoButton(label = label, onClick = { onAction(actions.single()) })
    }
}

@Composable
private fun CanonicalStateBlock(
    model: CanonicalComponentRenderModel,
    iconRes: Int,
    fallbackTitleFromMessage: Boolean
) {
    val message = model.optionalString("message")
    val title = model.optionalString("title") ?: message.takeIf { fallbackTitleFromMessage }
    if (title == null || message == null) {
        model.invalidProp("title|message")
        return
    }
    DemoStateBlock(iconRes = iconRes, title = title, body = message)
}

@Composable
private fun CanonicalChildren(
    model: CanonicalComponentRenderModel,
    onBack: () -> Unit,
    onNavigate: (String) -> Unit,
    onAction: (CanonicalComponentAction) -> Unit
) {
    model.node.children.forEach { child ->
        CanonicalComponentNodeRenderer(
            routeId = model.routeId,
            variantId = model.variantId,
            routeTitle = model.routeTitle,
            node = child,
            onBack = onBack,
            onNavigate = onNavigate,
            onAction = onAction
        )
    }
}

@Composable
private fun CanonicalComponentRenderModel.invalidProp(name: String) {
    CanonicalRendererFailureSurface(
        code = "CANONICAL_PROP_INVALID",
        message = "Required typed prop is absent or has the wrong JSON type: $name",
        identity = node.id
    )
}

private fun CanonicalComponentRenderModel.requiredString(name: String): String? =
    optionalString(name)?.takeIf { it.isNotBlank() }

private fun CanonicalComponentRenderModel.optionalString(name: String): String? =
    (props[name] as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull

@Composable
internal fun CanonicalRendererFailureSurface(
    code: String,
    message: String,
    identity: String
) {
    DemoErrorState(
        title = code,
        body = "$identity: $message",
        modifier = Modifier.fillMaxWidth().padding(8.dp)
    )
}
