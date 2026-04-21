package com.shuishiba744.sameviphook

import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.callbacks.XC_LoadPackage
import com.shuishiba744.sameviphook.hook.VipStatusHook
import com.shuishiba744.sameviphook.hook.VipInfoHook
import com.shuishiba744.sameviphook.hook.MiscHook
import com.shuishiba744.sameviphook.utils.XposedLog

/**
 * SameVipHook 模块入口类
 *
 * 实现 IXposedHookLoadPackage 接口，在目标应用加载时注入 Hook 逻辑
 *
 * 目标应用: com.same.android (same 社交应用)
 * 适配版本: v6.2.2 (versionCode 6202)
 *
 * Hook 架构:
 *   ┌─────────────────────────────────────────────┐
 *   │           MainHook.handleLoadPackage()       │
 *   │  ┌─────────────┬────────────┬─────────────┐ │
 *   │  │ VipStatus   │ VipInfo    │ Misc        │ │
 *   │  │ Hook (★★★)  │ Hook (★★)  │ Hook (★)    │ │
 *   │  │ 10个isVip   │ level+     │ isPaid      │ │
 *   │  │ 方法群      │ expiresAt  │ 订单状态    │ │
 *   │  └─────────────┴────────────┴─────────────┘ │
 *   └─────────────────────────────────────────────┘
 *
 * 进程过滤: 仅在 com.same.android 的主进程中执行 Hook，
 * 严格避免 Hook 系统进程和其他无关应用进程
 */
class MainHook : IXposedHookLoadPackage {

    companion object {
        /** 目标应用包名 */
        private const val TARGET_PACKAGE = "com.same.android"

        /** 模块版本（用于日志追踪） */
        private const val MODULE_VERSION = "1.0.0"
    }

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        // ========== 1. 进程过滤 ==========
        // 仅在目标应用的进程中执行 Hook，防止影响系统和其他应用
        if (lpparam.packageName != TARGET_PACKAGE) return

        // 额外过滤: 排除可能存在的子进程（如推送、IM等独立进程）
        // 仅在主进程和目标应用进程中执行
        val processName = lpparam.processName ?: return
        if (!processName.startsWith(TARGET_PACKAGE)) return

        // ========== 2. 模块初始化日志 ==========
        XposedLog.i("╔══════════════════════════════════════════════╗")
        XposedLog.i("║  SameVipHook v$MODULE_VERSION 已加载                 ║")
        XposedLog.i("║  目标包名: $TARGET_PACKAGE")
        XposedLog.i("║  进程名:   $processName")
        XposedLog.i("╚══════════════════════════════════════════════╝")

        val classLoader = lpparam.classLoader

        // ========== 3. 执行 Hook（每组独立异常隔离） ==========

        // ★★★ VIP 状态判断方法群（10个方法，最高优先级）
        try {
            VipStatusHook.hook(classLoader)
        } catch (t: Throwable) {
            XposedLog.e("VIP 状态 Hook 组执行异常（已隔离，不影响其他 Hook）", t)
        }

        // ★★ VIP 数据模型字段（UserVipInfo）
        try {
            VipInfoHook.hook(classLoader)
        } catch (t: Throwable) {
            XposedLog.e("VIP 数据模型 Hook 组执行异常（已隔离，不影响其他 Hook）", t)
        }

        // ★ 其他 VIP 相关方法（订单状态等）
        try {
            MiscHook.hook(classLoader)
        } catch (t: Throwable) {
            XposedLog.e("其他 Hook 组执行异常（已隔离，不影响其他 Hook）", t)
        }

        // ========== 4. 加载完成 ==========
        XposedLog.i("✅ 所有 Hook 组加载完成，模块运行中")
    }
}
