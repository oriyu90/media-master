package com.example.desktop

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.hardware.display.DisplayManager
import android.os.Build
import android.view.InputDevice

/**
 * v1.7.0: unified desktop/PC-mode detection for 8 vendor shells + AOSP.
 *
 * Covered (Sept 2026 behaviour):
 * - Samsung DeX (classic UiMode DESK + One UI 8 / Android 16 native desktop windowing)
 * - Android Desktop Windowing (AOSP freeform, API 35+ tablet + Android 16 phone external display)
 * - Motorola Smart Connect / Ready For (Mobile Desktop)
 * - Huawei EMUI Desktop Mode / Easy Projection
 * - HONOR Desktop Mode (MagicOS tablet PC Mode)
 * - Xiaomi HyperOS Desktop / Workstation Mode
 * - OPPO ColorOS PC Mode / PC Connect (no on-device native desktop; relies on AOSP signals)
 * - Lenovo Tab PC Mode / Productivity Mode (ZUI)
 *
 * Design constraints (must keep):
 * - minSdk 24 compatible: no API call above 24 without version guard + runCatching.
 * - Crash-safe: every reflection / system-service lookup is runCatching + null-safe.
 * - Memory-safe: stateless object, no listeners, no bitmaps, no cached Activity refs.
 * - Compatible: AUTO mode keeps v1.4.0-v1.6.0 signals (desk uiMode, caption bar,
 *   multi-window) and only ORs additional best-effort signals, so existing
 *   behaviour can never regress to "phone UI while in DeX".
 * - The width gate (>= 600dp) stays in MainNavigation, not here.
 */
object DesktopMode {

    /** Manual override stored in DataStore (SettingsRepository.DESKTOP_MODE_OVERRIDE). */
    const val OVERRIDE_AUTO = 0
    const val OVERRIDE_FORCE_DESKTOP = 1
    const val OVERRIDE_FORCE_TOUCH = 2

    enum class Vendor {
        SAMSUNG_DEX,
        ANDROID_DESKTOP,
        MOTOROLA,
        HUAWEI,
        HONOR,
        XIAOMI,
        OPPO,
        LENOVO,
        GENERIC,
    }

    data class Signals(
        val legacyDeskUiMode: Boolean = false,
        val captionBarVisible: Boolean = false,
        val multiWindow: Boolean = false,
        val freeformWindowing: Boolean = false,
        val samsungDexReflection: Boolean = false,
        /** Lenovo ZUI PC-mode heuristic (vendor-gated, see below). */
        val oemDesktopHeuristic: Boolean = false,
        val hasKeyboardOrMouse: Boolean = false,
        val hasExternalDisplay: Boolean = false,
    ) {
        /** Core triggers that alone are enough to enter desktop UI (AUTO). */
        fun hasCoreTrigger(): Boolean =
            legacyDeskUiMode || captionBarVisible || multiWindow ||
                freeformWindowing || samsungDexReflection || oemDesktopHeuristic
    }

    /** Pure, unit-testable resolution. No Android framework calls. */
    fun resolve(signals: Signals, override: Int): Boolean = when (override) {
        OVERRIDE_FORCE_DESKTOP -> true
        OVERRIDE_FORCE_TOUCH -> false
        else -> signals.hasCoreTrigger()
    }

    /** Pure, unit-testable vendor mapping from Build.MANUFACTURER. */
    fun vendorForManufacturer(manufacturer: String?): Vendor {
        return when (manufacturer?.lowercase()) {
            "samsung" -> Vendor.SAMSUNG_DEX
            "motorola", "moto" -> Vendor.MOTOROLA
            "huawei" -> Vendor.HUAWEI
            "honor" -> Vendor.HONOR
            "xiaomi", "redmi", "poco" -> Vendor.XIAOMI
            "oppo", "oneplus", "realme" -> Vendor.OPPO
            "lenovo" -> Vendor.LENOVO
            null -> Vendor.GENERIC
            else -> if (manufacturer.contains("lenovo", ignoreCase = true)) {
                Vendor.LENOVO
            } else {
                Vendor.GENERIC
            }
        }
    }

    fun currentVendor(): Vendor = runCatching { vendorForManufacturer(Build.MANUFACTURER) }
        .getOrDefault(Vendor.GENERIC)

    fun activityOrNull(context: Context): Activity? {
        var c: Context? = context
        var guard = 0
        while (c != null && guard++ < 8) {
            if (c is Activity) return c
            c = (c as? ContextWrapper)?.baseContext
        }
        return null
    }

    /** Best-effort Samsung DeX check via reflection (no Samsung SDK dependency). */
    fun isSamsungDesktopModeReflection(activity: Activity): Boolean = runCatching {
        // com.samsung.android.sdk.dex.SemDesktopModeManager#getInstance / isDesktopMode
        val clazz = Class.forName("com.samsung.android.sdk.dex.SemDesktopModeManager")
        val getInstance = clazz.getMethod("getInstance")
        val manager = getInstance.invoke(null) ?: return false
        val isDesktop = clazz.getMethod("isDesktopMode")
        (isDesktop.invoke(manager) as? Boolean) == true
    }.getOrDefault(false)

    /** Best-effort freeform check (WINDOWING_MODE_FREEFORM = 5, API 28+). */
    fun isFreeformWindowing(activity: Activity): Boolean = runCatching {
        if (Build.VERSION.SDK_INT < 28) return false
        val method = Activity::class.java.getMethod("getWindowingMode")
        val mode = (method.invoke(activity) as? Int) ?: return false
        mode == 5 // ActivityInfo.WINDOWING_MODE_FREEFORM
    }.getOrDefault(false)

    fun hasKeyboardOrMouse(context: Context): Boolean = runCatching {
        val ids: IntArray = InputDevice.getDeviceIds()
        var found = false
        for (id: Int in ids) {
            val dev: InputDevice = runCatching { InputDevice.getDevice(id) }.getOrNull() ?: continue
            val sources: Int = dev.sources
            if ((sources and InputDevice.SOURCE_MOUSE) == InputDevice.SOURCE_MOUSE ||
                (sources and InputDevice.SOURCE_TRACKBALL) == InputDevice.SOURCE_TRACKBALL ||
                (sources and InputDevice.SOURCE_TOUCHPAD) == InputDevice.SOURCE_TOUCHPAD ||
                dev.keyboardType != InputDevice.KEYBOARD_TYPE_NONE
            ) {
                found = true
                break
            }
        }
        found
    }.getOrDefault(false)

    fun hasExternalDisplay(context: Context): Boolean = runCatching {
        val dm = context.getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager
            ?: return false
        (dm.displays?.size ?: 0) > 1
    }.getOrDefault(false)

    fun isInMultiWindow(activity: Activity): Boolean =
        runCatching { activity.isInMultiWindowMode }.getOrDefault(false)

    /**
     * Best-effort Lenovo ZUI PC-mode / Productivity-mode probe.
     *
     * Lenovo publishes no detection API and ZUI's PC mode does not reliably set
     * desk uiMode, a caption bar, or multi-window/freeform flags, so AUTO mode
     * otherwise stays in the phone UI on Lenovo Tabs (v1.7.0 user report).
     * Two supporting signals are checked, both crash-safe and permission-free:
     * 1. System settings keys that ZUI-style shells use for the PC-mode toggle
     *    (probed across Global/Secure/System; missing keys simply read null).
     * 2. Physical keyboard / mouse presence — Lenovo's own docs state PC mode
     *    auto-activates on keyboard attach, so on Lenovo hardware this mirrors
     *    the platform's behaviour.
     *
     * The keyboard branch is deliberately gated to Lenovo hardware only, so
     * keyboard-attached tablets from other vendors never change behaviour.
     * Returns false for non-Lenovo devices unless the settings flag matches.
     */
    fun isLenovoPcModeSettingEnabled(context: Context): Boolean = runCatching {
        val candidates = listOf(
            "pc_mode",
            "lenovo_pc_mode",
            "zui_pc_mode",
            "productivity_mode",
            "desktop_mode",
            "lenovo_desktop_mode",
        )
        val resolvers: List<(String) -> String?> = listOf(
            { k -> android.provider.Settings.Global.getString(context.contentResolver, k) },
            { k -> android.provider.Settings.Secure.getString(context.contentResolver, k) },
            { k -> android.provider.Settings.System.getString(context.contentResolver, k) },
        )
        candidates.any { key ->
            resolvers.any { read ->
                val v = runCatching { read(key) }.getOrNull()?.trim()?.lowercase()
                v == "1" || v == "true" || v == "on" || v == "enable" || v == "enabled"
            }
        }
    }.getOrDefault(false)

    fun isLenovoDesktopHeuristic(context: Context, activity: Activity?): Boolean = runCatching {
        val isLenovo = vendorForManufacturer(Build.MANUFACTURER) == Vendor.LENOVO ||
            Build.BRAND.equals("lenovo", ignoreCase = true)
        if (isLenovo && hasKeyboardOrMouse(context)) return true
        if (isLenovoPcModeSettingEnabled(context)) return true
        false
    }.getOrDefault(false)
}
