package com.shuishiba744.sameviphook.hook

import de.robv.android.xposed.XC_MethodHook
import com.shuishiba744.sameviphook.utils.HookHelper
import com.shuishiba744.sameviphook.utils.XposedLog

/**
 * VIP 状态判断方法 Hook 组
 *
 * 对应逆向报告: 第四章 4.1 核心验证入口 — isVip() 方法群
 *
 * Hook 策略:
 *   - 所有返回 boolean 的 isVip() → 强制返回 true
 *   - 所有返回 int 的 getIsVip()/isVip() → 强制返回 1
 *
 * 这些方法覆盖了 VIP 状态的完整读取链路：
 *   SharedPreferences 层 → ProfileManager 代理层 → DB 层 → 数据模型层
 *   确保无论应用从哪个入口读取 VIP 状态，都返回"已开通"
 */
object VipStatusHook {

    /**
     * 执行所有 VIP 状态相关 Hook
     * @param classLoader 目标应用的类加载器
     */
    fun hook(classLoader: ClassLoader) {
        XposedLog.i("====== 开始 Hook VIP 状态判断方法 ======")

        hookLocalStorage(classLoader)
        hookProfileManager(classLoader)
        hookDatabaseLayer(classLoader)
        hookDataModel(classLoader)

        XposedLog.i("====== VIP 状态 Hook 完成 ======")
    }

    // ==================== 1. 本地存储层 (SharedPreferences) ====================

    /**
     * Hook SharedPreferences 层的 VIP 状态读写
     * 这是最底层的 VIP 状态存储，所有上层方法最终都引用这里
     */
    private fun hookLocalStorage(classLoader: ClassLoader) {
        XposedLog.i(">> Hook 本地存储层 (LocalUserInfoUtils)")

        // ★★★ 位置1: LocalUserInfoUtils$MyUserInfo.isVip() → boolean
        // 最底层 VIP 状态读取，从 SharedPreferences 读取 is_vip 字段
        // 指令数: 6 | 混淆前: 本地用户信息内部类的 VIP 状态属性
        HookHelper.findAndHookMethod(
            classLoader,
            "com.same.android.utils.LocalUserInfoUtils\$MyUserInfo",
            "isVip",
            HookHelper.alwaysReturn(true, "MyUserInfo.isVip")
        )

        // ★★★ 位置2: LocalUserInfoUtils.getIsVip() → int
        // 读取 SP 中的 is_vip 整型值 (0=非会员, 1=普通会员)
        // 字符串常量: "is_vip" | 指令数: 12
        HookHelper.findAndHookMethod(
            classLoader,
            "com.same.android.utils.LocalUserInfoUtils",
            "getIsVip",
            HookHelper.alwaysReturn(1, "LocalUserInfoUtils.getIsVip")
        )
    }

    // ==================== 2. ProfileManager 代理层 ====================

    /**
     * Hook ProfileManager（用户档案管理器）的 VIP 状态判断
     * 这是多数业务逻辑的 VIP 判断主入口
     */
    private fun hookProfileManager(classLoader: ClassLoader) {
        XposedLog.i(">> Hook ProfileManager 代理层")

        // ★★★ 位置4: ProfileManager.isVip() → boolean
        // VIP 状态判断主入口，多数业务逻辑通过此方法判断
        // 指令数: 4 | 最终调用 LocalUserInfoUtils 的方法
        HookHelper.findAndHookMethod(
            classLoader,
            "com.same.android.v2.manager.ProfileManager",
            "isVip",
            HookHelper.alwaysReturn(true, "ProfileManager.isVip")
        )

        // ★★ 位置5: ProfileManager.isWwjVip() → boolean
        // Wwj 子模块 VIP 状态判断（Wwj = same 应用内的子功能模块）
        // 说明: 应用存在多套 VIP 体系
        HookHelper.findAndHookMethod(
            classLoader,
            "com.same.android.v2.manager.ProfileManager",
            "isWwjVip",
            HookHelper.alwaysReturn(true, "ProfileManager.isWwjVip")
        )

        // ★★ 位置6: WwjProfile.isVip() → boolean
        // Wwj 子模块独立 VIP 状态判断
        // 指令数: 2
        HookHelper.findAndHookMethod(
            classLoader,
            "com.same.android.v2.manager.WwjProfile",
            "isVip",
            HookHelper.alwaysReturn(true, "WwjProfile.isVip")
        )
    }

    // ==================== 3. 数据库层 (SQLite) ====================

    /**
     * Hook 数据库层的 VIP 状态读取
     * VIP 状态同时存储在 SP 和本地 SQLite 数据库中
     */
    private fun hookDatabaseLayer(classLoader: ClassLoader) {
        XposedLog.i(">> Hook 数据库层 (UserInfo)")

        // ★★ 位置7: UserInfo.isVip() → boolean
        // 数据库层的 VIP 状态判断（Boolean 类型）
        // 指令数: 6
        HookHelper.findAndHookMethod(
            classLoader,
            "com.same.android.db.UserInfo",
            "isVip",
            HookHelper.alwaysReturn(true, "UserInfo.isVip")
        )

        // ★★ UserInfo.getIs_vip() → int
        // 数据库层读取 VIP 等级（Int 类型，Kotlin 生成的 getter）
        // 返回值与 getIsVip() 一致
        HookHelper.findAndHookMethod(
            classLoader,
            "com.same.android.db.UserInfo",
            "getIs_vip",
            HookHelper.alwaysReturn(1, "UserInfo.getIs_vip")
        )
    }

    // ==================== 4. 数据模型层 ====================

    /**
     * Hook 数据模型类的 VIP 状态属性
     * 这些类用于网络数据传输和内存中的用户信息缓存
     */
    private fun hookDataModel(classLoader: ClassLoader) {
        XposedLog.i(">> Hook 数据模型层 (SimpleUser / User / LoginUserDto)")

        // ★★ 位置8: SimpleUser.isVip() → boolean
        // 轻量用户数据类的 VIP 状态，直接返回对象内的布尔字段
        // 指令数: 2
        HookHelper.findAndHookMethod(
            classLoader,
            "com.same.latest.data.SimpleUser",
            "isVip",
            HookHelper.alwaysReturn(true, "SimpleUser.isVip")
        )

        // ★★ 位置9: User.isVip() → int
        // 完整用户数据类的 VIP 等级（注意返回 int 而非 boolean）
        // 与 LocalUserInfoUtils.getIsVip() 一致，存在等级划分
        // 指令数: 2
        HookHelper.findAndHookMethod(
            classLoader,
            "com.same.latest.data.User",
            "isVip",
            HookHelper.alwaysReturn(1, "User.isVip")
        )

        // ★ LoginUserDto.getIs_vip() → int
        // 登录用户数据传输对象的 VIP 等级
        // 用于登录后从服务端获取用户信息时的 VIP 状态
        HookHelper.findAndHookMethod(
            classLoader,
            "com.same.android.bean.LoginUserDto",
            "getIs_vip",
            HookHelper.alwaysReturn(1, "LoginUserDto.getIs_vip")
        )
    }
}
