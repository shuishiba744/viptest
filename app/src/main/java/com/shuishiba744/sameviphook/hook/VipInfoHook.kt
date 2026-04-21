package com.shuishiba744.sameviphook.hook

import de.robv.android.xposed.XC_MethodHook
import com.shuishiba744.sameviphook.utils.HookHelper
import com.shuishiba744.sameviphook.utils.XposedLog

/**
 * VIP 数据模型字段 Hook
 *
 * 对应逆向报告: 第四章 4.2 VIP 数据模型 — UserVipInfo
 *
 * UserVipInfo 字段说明:
 *   - level: int       → VIP 等级 (0=非会员, 1=普通会员, 2+=高级会员)
 *   - expiresAt: long  → VIP 过期时间戳（毫秒）
 *   - signState: String → VIP 签名状态（可能用于服务端校验，暂不修改）
 *
 * Hook 策略:
 *   - level: 非正值时替换为 1
 *   - expiresAt: 已过期时延长至 1 年后
 *   - signState: 不 Hook（避免签名校验异常）
 */
object VipInfoHook {

    /** VIP 延期时长: 1年（毫秒） */
    private const val ONE_YEAR_MS = 365L * 24 * 60 * 60 * 1000

    /** 判断 VIP 是否快过期（阈值: 1天） */
    private const val EXPIRY_BUFFER_MS = 24L * 60 * 60 * 1000

    /**
     * 执行所有 UserVipInfo 数据模型 Hook
     * @param classLoader 目标应用的类加载器
     */
    fun hook(classLoader: ClassLoader) {
        XposedLog.i("====== 开始 Hook VIP 数据模型 ======")

        hookVipLevel(classLoader)
        hookVipExpiry(classLoader)

        XposedLog.i("====== VIP 数据模型 Hook 完成 ======")
    }

    // ==================== VIP 等级 ====================

    /**
     * Hook UserVipInfo.getLevel() → int
     *
     * 对应逆向报告 4.2: level 字段
     * VIP 等级可能影响不同的会员权益
     *
     * 策略: 仅在原始值 <= 0（非会员）时替换为 1
     * 如果用户本身就是高等级会员，不覆盖
     */
    private fun hookVipLevel(classLoader: ClassLoader) {
        HookHelper.findAndHookMethod(
            classLoader,
            "com.same.latest.data.UserVipInfo",
            "getLevel",
            object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val original = param.result as? Int ?: return
                    if (original <= 0) {
                        param.result = 1
                        XposedLog.d("UserVipInfo.getLevel: $original → 1")
                    }
                }
            }
        )
    }

    // ==================== VIP 过期时间 ====================

    /**
     * Hook UserVipInfo.getExpiresAt() → long
     *
     * 对应逆向报告 4.2: expiresAt 字段
     * 客户端可能用 System.currentTimeMillis() > expiresAt 判断 VIP 是否过期
     *
     * 策略: 如果 VIP 已过期或即将过期（不足1天），延长至当前时间 + 1年
     * 不修改未过期的合法 VIP 时间
     */
    private fun hookVipExpiry(classLoader: ClassLoader) {
        HookHelper.findAndHookMethod(
            classLoader,
            "com.same.latest.data.UserVipInfo",
            "getExpiresAt",
            object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val expiresAt = param.result as? Long ?: return
                    val now = System.currentTimeMillis()

                    // 如果 VIP 已过期或即将过期（不足1天），延长到1年后
                    if (expiresAt - now < EXPIRY_BUFFER_MS) {
                        val newExpiry = now + ONE_YEAR_MS
                        param.result = newExpiry
                        XposedLog.d("UserVipInfo.getExpiresAt: 已过期 → 延长至 ${newExpiry}")
                    }
                }
            }
        )
    }
}
