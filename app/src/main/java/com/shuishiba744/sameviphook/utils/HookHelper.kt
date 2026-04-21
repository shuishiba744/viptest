package com.shuishiba744.sameviphook.utils

import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers

/**
 * Hook 操作安全封装工具
 * 所有 Hook 操作通过此类执行，内置异常捕获与日志记录，
 * 确保 Hook 失败不会导致目标应用或系统崩溃。
 */
object HookHelper {

    // ==================== 核心 Hook 方法 ====================

    /**
     * 安全地查找并 Hook 指定类的指定方法
     *
     * @param classLoader   目标应用的类加载器（lpparam.classLoader）
     * @param className     目标类的全限定名（内部类用 $ 分隔，需转义为 \$）
     * @param methodName    目标方法名
     * @param paramTypes    方法参数类型（用于区分重载方法，无参可不传）
     * @param callback      Xposed Hook 回调
     * @return true=Hook 成功, false=Hook 失败（已记录日志）
     *
     * 使用示例:
     *   HookHelper.findAndHookMethod(
     *       classLoader,
     *       "com.example.TargetClass\$InnerClass",
     *       "targetMethod",
     *       String::class.java,
     *       Int::class.javaPrimitiveType,
     *       object : XC_MethodHook() { ... }
     *   )
     */
    fun findAndHookMethod(
        classLoader: ClassLoader,
        className: String,
        methodName: String,
        vararg args: Any
    ): Boolean {
        return try {
            XposedHelpers.findAndHookMethod(className, classLoader, methodName, *args)
            XposedLog.i("Hook 成功: $className.$methodName")
            true
        } catch (t: Throwable) {
            XposedLog.e("Hook 失败: $className.$methodName | ${t.javaClass.simpleName}: ${t.message}", t)
            false
        }
    }

    /**
     * 安全地 Hook 指定类的所有同名重载方法
     * 适用于不确定方法签名的场景，会 Hook 所有同名方法
     *
     * @param classLoader   目标应用的类加载器
     * @param className     目标类的全限定名
     * @param methodName    目标方法名
     * @param callback      Xposed Hook 回调（统一应用到所有重载方法）
     * @return true=至少成功 Hook 一个方法, false=全部失败
     */
    fun findAndHookAllOverloads(
        classLoader: ClassLoader,
        className: String,
        methodName: String,
        callback: XC_MethodHook
    ): Boolean {
        return try {
            val clazz = XposedHelpers.findClass(className, classLoader)
            var hookedCount = 0

            for (method in clazz.declaredMethods) {
                if (method.name == methodName) {
                    XposedBridge.hookMethod(method, callback)
                    hookedCount++
                    XposedLog.d("Hook 重载方法: $className.$methodName(${method.parameterTypes.joinToString { it.simpleName }})")
                }
            }

            if (hookedCount > 0) {
                XposedLog.i("Hook 成功: $className.$methodName (共 $hookedCount 个重载)")
                true
            } else {
                XposedLog.w("未找到方法: $className.$methodName")
                false
            }
        } catch (t: Throwable) {
            XposedLog.e("Hook 失败(重载扫描): $className.$methodName | ${t.message}", t)
            false
        }
    }

    // ==================== 辅助方法 ====================

    /**
     * 安全地查找目标类（不进行 Hook）
     * 用于检查类是否存在，或获取 Class 对象进行后续操作
     *
     * @return 找到的 Class 对象，未找到返回 null
     */
    fun findClass(classLoader: ClassLoader, className: String): Class<*>? {
        return try {
            XposedHelpers.findClass(className, classLoader)
        } catch (t: Throwable) {
            XposedLog.e("查找类失败: $className | ${t.message}")
            null
        }
    }

    /**
     * 创建一个统一替换返回值的 Hook 回调（afterHooked 模式）
     * 适用于所有 "强制返回固定值" 的场景
     *
     * @param value      要替换的返回值
     * @param logEnabled 是否在每次替换时输出 DEBUG 日志
     * @return XC_MethodHook 实例
     *
     * 使用示例:
     *   HookHelper.returnValueHook(true) { param.result = true }
     *   // 或更简洁:
     *   HookHelper.findAndHookMethod(cl, "SomeClass", "isVip", HookHelper.alwaysReturn(true))
     */
    fun alwaysReturn(value: Any?, logTag: String = ""): XC_MethodHook {
        return object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                param.result = value
                if (logTag.isNotEmpty()) {
                    XposedLog.d("$logTag → $value")
                }
            }
        }
    }
}
