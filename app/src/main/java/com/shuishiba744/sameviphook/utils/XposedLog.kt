package com.shuishiba744.sameviphook.utils

import de.robv.android.xposed.XposedBridge

/**
 * Xposed 日志工具
 * 所有日志通过 XposedBridge.log 输出，可在 LSPosed Manager 日志中查看
 * 搜索关键词: "SameVipHook"
 */
object XposedLog {

    private const val TAG = "SameVipHook"

    /** 是否输出 DEBUG 级别日志（上线后可关闭以减少日志量） */
    @Volatile
    var debugEnabled: Boolean = true

    // ==================== 日志级别方法 ====================

    /**
     * INFO 级别日志 — 模块加载、Hook 成功等关键状态
     */
    fun i(msg: String) {
        XposedBridge.log("[$TAG-I] $msg")
    }

    /**
     * ERROR 级别日志 — Hook 失败、异常捕获（始终输出）
     */
    fun e(msg: String, throwable: Throwable? = null) {
        XposedBridge.log("[$TAG-E] $msg")
        throwable?.let { XposedBridge.log(it) }
    }

    /**
     * DEBUG 级别日志 — 方法调用细节、参数值等（可开关）
     */
    fun d(msg: String) {
        if (debugEnabled) {
            XposedBridge.log("[$TAG-D] $msg")
        }
    }

    /**
     * WARN 级别日志 — 非致命问题、降级处理等
     */
    fun w(msg: String) {
        XposedBridge.log("[$TAG-W] $msg")
    }
}
