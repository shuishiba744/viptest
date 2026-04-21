package com.shuishiba744.sameviphook.hook

import de.robv.android.xposed.XC_MethodHook
import com.shuishiba744.sameviphook.utils.HookHelper
import com.shuishiba744.sameviphook.utils.XposedLog

/**
 * 其他 VIP 相关方法 Hook
 *
 * 对应逆向报告:
 *   - 4.4 VIP 购买/下单流程
 *   - 第七章 附录: VIP 相关方法列表
 *
 * 包含非核心但有助于完整体验的 Hook 点
 */
object MiscHook {

    /**
     * 执行其他 VIP 相关 Hook
     * @param classLoader 目标应用的类加载器
     */
    fun hook(classLoader: ClassLoader) {
        XposedLog.i("====== 开始 Hook 其他 VIP 相关方法 ======")

        hookOrderPaidStatus(classLoader)

        XposedLog.i("====== 其他 Hook 完成 ======")
    }

    // ==================== 订单支付状态 ====================

    /**
     * Hook ProductOrderDto.isPaid() → boolean
     *
     * 对应逆向报告 4.4: 订单支付状态
     * 原始逻辑: 使用正则 "(^|.*[^a-z0-9])paid([^a-z0-9].*|$)" 匹配订单状态字符串
     *
     * 策略: 强制返回 true，使所有订单显示为"已支付"状态
     * 注意: 这仅影响客户端显示，不影响服务端订单记录
     */
    private fun hookOrderPaidStatus(classLoader: ClassLoader) {
        HookHelper.findAndHookMethod(
            classLoader,
            "com.same.android.bean.ProductOrderDto",
            "isPaid",
            HookHelper.alwaysReturn(true, "ProductOrderDto.isPaid")
        )
    }
}
