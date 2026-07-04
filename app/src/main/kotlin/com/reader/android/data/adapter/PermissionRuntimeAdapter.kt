package com.reader.android.data.adapter

import com.reader.ui.shell.PermissionKind
import com.reader.ui.shell.PermissionStatus

/**
 * P3: Unified permission runtime adapter.
 *
 * The settings "系统权限" section needs to read the current state of each permission
 * (notifications / file access / battery optimization) and dispatch system permission
 * flows. Previously only scattered primitives existed
 * (`AndroidNotificationRuntimeAdapter.permissionState()` for notifications,
 * `AndroidContentUriLocalBookAdapter.takePersistableReadPermission()` for SAF URIs).
 *
 * This interface ties those primitives into a single adapter the UI layer can query,
 * so `SettingsGeneralScreen` can render real permission badges and the reducer can
 * record `PermissionGranted` / `PermissionDenied` outcomes.
 *
 * Production wiring delegates to the existing platform primitives; tests inject a fake.
 */
fun interface PermissionRuntimeAdapter {
    /** Returns the current status of [kind]. */
    fun query(kind: PermissionKind): PermissionStatus
}

/**
 * Fake [PermissionRuntimeAdapter] for tests. Returns a fixed status per kind
 * (default [PermissionStatus.UNKNOWN] for all).
 */
class FakePermissionRuntimeAdapter(
    private val statuses: Map<PermissionKind, PermissionStatus> = emptyMap()
) : PermissionRuntimeAdapter {
    override fun query(kind: PermissionKind): PermissionStatus =
        statuses[kind] ?: PermissionStatus.UNKNOWN
}
