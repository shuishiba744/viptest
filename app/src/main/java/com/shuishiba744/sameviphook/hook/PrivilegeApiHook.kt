package com.shuishiba744.sameviphook.hook

import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import com.shuishiba744.sameviphook.utils.HookHelper
import com.shuishiba744.sameviphook.utils.XposedLog
import java.lang.reflect.Proxy

/**
 * VIP 特权 API 网络响应拦截 Hook
 *
 * 背景:
 *   "无痕浏览"等 VIP 特权功能不走 isVip() 判断，而是通过独立的服务端 API 校验。
 *   抓包发现: GET /member/privilege/config/list → {"code":403, ...}
 *   服务端根据真实 VIP 状态拒绝请求，纯客户端 isVip() Hook 无法绕过。
 *
 * 对应逆向报告: 第四章 4.3 VIP 特权系统
 *   Privilege: { memberPrivilegeId: int, enabled: int(0/1) }
 *   由 SameViewModel.fetchPrivilegeList() 从服务端拉取
 *   由 SameViewModel.setPrivilegeEnabled() 控制开关
 *
 * Hook 策略:
 *   1. 拦截 OkHttp RealCall 的 execute/enqueue，对 /member/privilege/ 请求替换响应体
 *   2. Hook Privilege.getEnabled() 强制返回 1（双保险）
 *
 * 特权列表 (来自 VipPrivilege$Companion.getAllPrivileges()):
 *   1=头像挂件, 2=私密频道, 3=频道改名,
 *   4=帖子编辑, 5=小尾巴, 6=无痕浏览, 7=频道顶帖
 */
object PrivilegeApiHook {

    /** 需要拦截的特权 API 路径关键词 */
    private const val PRIVILEGE_API_KEYWORD = "/member/privilege/"

    /**
     * Mock 成功响应 — 7 个 VIP 特权全部 enabled=1
     * 字段名来自逆向报告 Privilege 模型: memberPrivilegeId, enabled
     */
    private val MOCK_PRIVILEGE_RESPONSE =
        """{"code":200,"message":"success","data":["""
            + """{"memberPrivilegeId":1,"enabled":1},"""
            + """{"memberPrivilegeId":2,"enabled":1},"""
            + """{"memberPrivilegeId":3,"enabled":1},"""
            + """{"memberPrivilegeId":4,"enabled":1},"""
            + """{"memberPrivilegeId":5,"enabled":1},"""
            + """{"memberPrivilegeId":6,"enabled":1},"""
            + """{"memberPrivilegeId":7,"enabled":1}"""
            + """]}"""

    /**
     * 执行特权 API 拦截 Hook
     * @param classLoader 目标应用的类加载器
     */
    fun hook(classLoader: ClassLoader) {
        XposedLog.i("====== 开始 Hook 特权 API 拦截 ======")

        hookOkHttpResponses(classLoader)
        hookPrivilegeModel(classLoader)

        XposedLog.i("====== 特权 API Hook 完成 ======")
    }

    // ==================== OkHttp 响应拦截 ====================

    /**
     * Hook OkHttp RealCall 的同步/异步请求，拦截特权 API 响应并替换为 Mock 数据
     *
     * 技术细节:
     *   - 同步: hook RealCall.execute() → afterHookedMethod 替换返回值
     *   - 异步: hook RealCall.enqueue(Callback) → beforeHookedMethod 用动态代理包装 Callback
     *   - 兼容 OkHttp 3.x (okhttp3.RealCall) 和 4.x (okhttp3.RealCall / okhttp3.internal.connection.RealCall)
     */
    private fun hookOkHttpResponses(classLoader: ClassLoader) {
        XposedLog.i(">> Hook OkHttp 响应拦截 (RealCall)")

        // 兼容 OkHttp 3.x 和 4.x 的 RealCall 类路径
        val realCallClass = HookHelper.findClass(classLoader, "okhttp3.RealCall")
            ?: HookHelper.findClass(classLoader, "okhttp3.internal.connection.RealCall")

        if (realCallClass == null) {
            XposedLog.e("未找到 OkHttp RealCall 类，特权 API 拦截不可用")
            return
        }

        // ---------- 1. 同步请求拦截: RealCall.execute() ----------
        val syncMethods = XposedBridge.hookAllMethods(realCallClass, "execute",
            object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    try {
                        val response = param.result ?: return
                        val replaced = tryReplacePrivilegeResponse(response, classLoader)
                        if (replaced !== response) {
                            param.result = replaced
                        }
                    } catch (t: Throwable) {
                        XposedLog.e("特权 API 同步拦截异常", t)
                    }
                }
            }
        )
        if (syncMethods.isNotEmpty()) {
            XposedLog.i("Hook 成功: RealCall.execute (同步请求拦截, ${syncMethods.size}个方法)")
        }

        // ---------- 2. 异步请求拦截: RealCall.enqueue(Callback) ----------
        val callbackClass = HookHelper.findClass(classLoader, "okhttp3.Callback")
        if (callbackClass != null) {
            val asyncMethods = XposedBridge.hookAllMethods(realCallClass, "enqueue",
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        try {
                            val originalCallback = param.args[0] ?: return
                            param.args[0] = wrapCallbackWithInterceptor(
                                originalCallback, callbackClass, classLoader
                            )
                        } catch (t: Throwable) {
                            XposedLog.e("特权 API 异步拦截异常", t)
                        }
                    }
                }
            )
            if (asyncMethods.isNotEmpty()) {
                XposedLog.i("Hook 成功: RealCall.enqueue (异步请求拦截, ${asyncMethods.size}个方法)")
            }
        } else {
            XposedLog.w("未找到 okhttp3.Callback 类，异步拦截不可用")
        }
    }

    /**
     * 用 Java 动态代理包装原始 Callback，在 onResponse 时替换特权 API 的响应
     *
     * @param original     原始 Callback 实例
     * @param callbackIface okhttp3.Callback 接口 Class
     * @param classLoader  目标应用的类加载器
     * @return 代理后的 Callback 实例
     */
    @Suppress("UNCHECKED_CAST")
    private fun wrapCallbackWithInterceptor(
        original: Any,
        callbackIface: Class<*>,
        classLoader: ClassLoader
    ): Any {
        return Proxy.newProxyInstance(
            callbackIface.classLoader,
            arrayOf(callbackIface)
        ) { _, method, args ->
            // 拦截 onResponse(Call, Response)，替换 Response
            if (method.name == "onResponse" && args != null && args.size >= 2) {
                val call = args[0]
                val response = args[1]
                val intercepted = tryReplacePrivilegeResponse(response, classLoader)
                return@newProxyInstance method.invoke(original, call, intercepted)
            }
            // onFailure 不修改，透传
            method.invoke(original, *(args ?: emptyArray()))
        }
    }

    /**
     * 检查 Response 对应的请求 URL，如果匹配特权 API 则替换响应体为 Mock 数据
     *
     * @param response    原始 OkHttp Response 对象
     * @param classLoader 目标应用的类加载器
     * @return 替换后的 Response（不匹配时返回原始 response）
     */
    private fun tryReplacePrivilegeResponse(response: Any, classLoader: ClassLoader): Any {
        // 获取请求 URL
        val request: Any = try {
            XposedHelpers.callMethod(response, "request") ?: return response
        } catch (t: Throwable) {
            XposedLog.e("获取 request 失败", t)
            return response
        }

        val url: String = try {
            XposedHelpers.callMethod(request, "url")?.toString() ?: return response
        } catch (t: Throwable) {
            return response
        }

        // 仅拦截 /member/privilege/ 路径的请求
        if (!url.contains(PRIVILEGE_API_KEYWORD)) return response

        XposedLog.i("拦截特权 API 响应: $url")

        try {
            // 获取原始响应的 ContentType（保持一致）
            val originalBody = XposedHelpers.callMethod(response, "body")
            val contentType = try {
                originalBody?.let { XposedHelpers.callMethod(it, "contentType") }
            } catch (_: Throwable) {
                null
            }

            // 通过 ResponseBody.create() 创建 Mock 响应体
            val responseBodyClass = XposedHelpers.findClass("okhttp3.ResponseBody", classLoader)
            val mockBody = XposedHelpers.callStaticMethod(
                responseBodyClass,
                "create",
                contentType,
                MOCK_PRIVILEGE_RESPONSE
            )

            // 通过 Response.newBuilder() 构建新 Response
            val builder = XposedHelpers.callMethod(response, "newBuilder")
            XposedHelpers.callMethod(builder, "body", mockBody)
            val newResponse = XposedHelpers.callMethod(builder, "build")

            XposedLog.i("特权 API 响应已替换 → Mock (7个特权全部 enabled=1)")
            return newResponse

        } catch (t: Throwable) {
            XposedLog.e("替换特权 API 响应失败，返回原始响应", t)
            return response
        }
    }

    // ==================== 特权模型 Hook（双保险） ====================

    /**
     * Hook Privilege.getEnabled() → 强制返回 1
     *
     * 即使 API Mock 因格式不匹配而失败，只要应用创建了 Privilege 对象，
     * getEnabled() 就会返回 1（已启用），确保客户端 UI 和逻辑放行。
     */
    private fun hookPrivilegeModel(classLoader: ClassLoader) {
        XposedLog.i(">> Hook Privilege 数据模型 (双保险)")

        HookHelper.findAndHookMethod(
            classLoader,
            "com.same.latest.data.Privilege",
            "getEnabled",
            HookHelper.alwaysReturn(1, "Privilege.getEnabled")
        )
    }
}
