package com.shuishiba744.sameviphook.hook

import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import com.shuishiba744.sameviphook.utils.HookHelper
import com.shuishiba744.sameviphook.utils.XposedLog
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Proxy

/**
 * VIP 特权 API 网络响应拦截 Hook
 *
 * 背景: "无痕浏览"等 VIP 特权功能不走 isVip()，通过服务端 API 校验。
 * 抓包: GET /member/privilege/config/list → {"code":403, ...}
 * 策略: 1. 拦截 OkHttp RealCall 响应替换为 Mock 数据
 *       2. Hook Privilege.getEnabled() 强制返回 1
 */
object PrivilegeApiHook {

    private const val PRIVILEGE_API_KEYWORD = "/member/privilege/"

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

    fun hook(classLoader: ClassLoader) {
        XposedLog.i("====== 开始 Hook 特权 API 拦截 ======")
        hookOkHttpResponses(classLoader)
        hookPrivilegeModel(classLoader)
        XposedLog.i("====== 特权 API Hook 完成 ======")
    }

    private fun hookOkHttpResponses(classLoader: ClassLoader) {
        XposedLog.i(">> Hook OkHttp 响应拦截 (RealCall)")

        val realCallClass = HookHelper.findClass(classLoader, "okhttp3.RealCall")
            ?: HookHelper.findClass(classLoader, "okhttp3.internal.connection.RealCall")

        if (realCallClass == null) {
            XposedLog.e("未找到 OkHttp RealCall 类，特权 API 拦截不可用")
            return
        }

        // 同步: RealCall.execute()
        HookHelper.findAndHookAllOverloads(
            classLoader, realCallClass.name, "execute",
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

        // 异步: RealCall.enqueue(Callback)
        HookHelper.findAndHookAllOverloads(
            classLoader, realCallClass.name, "enqueue",
            object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    try {
                        val originalCallback = param.args[0] ?: return
                        val callbackClass = HookHelper.findClass(classLoader, "okhttp3.Callback")
                            ?: return
                        param.args[0] = createCallbackProxy(
                            originalCallback, callbackClass, classLoader
                        )
                    } catch (t: Throwable) {
                        XposedLog.e("特权 API 异步拦截异常", t)
                    }
                }
            }
        )
    }

    private fun createCallbackProxy(
        original: Any,
        callbackIface: Class<*>,
        classLoader: ClassLoader
    ): Any {
        val handler = InvocationHandler { _, method, args ->
            if ("onResponse" == method.name && args != null && args.size >= 2) {
                val intercepted = tryReplacePrivilegeResponse(args[1], classLoader)
                method.invoke(original, args[0], intercepted)
            } else if (args != null) {
                method.invoke(original, *args)
            } else {
                method.invoke(original)
            }
        }
        return Proxy.newProxyInstance(
            callbackIface.classLoader,
            arrayOf(callbackIface),
            handler
        )
    }

    private fun tryReplacePrivilegeResponse(response: Any, classLoader: ClassLoader): Any {
        val request: Any
        try {
            request = XposedHelpers.callMethod(response, "request")
        } catch (t: Throwable) {
            return response
        }
        if (request == null) return response

        val url: String
        try {
            url = XposedHelpers.callMethod(request, "url")?.toString() ?: return response
        } catch (t: Throwable) {
            return response
        }

        if (!url.contains(PRIVILEGE_API_KEYWORD)) return response

        XposedLog.i("拦截特权 API 响应: $url")

        try {
            val body = XposedHelpers.callMethod(response, "body")
            val contentType = try {
                body?.let { XposedHelpers.callMethod(it, "contentType") }
            } catch (t: Throwable) { null }

            val responseBodyClass = XposedHelpers.findClass("okhttp3.ResponseBody", classLoader)
            val mockBody = XposedHelpers.callStaticMethod(
                responseBodyClass, "create", contentType, MOCK_PRIVILEGE_RESPONSE
            )

            val builder = XposedHelpers.callMethod(response, "newBuilder")
            XposedHelpers.callMethod(builder, "body", mockBody)
            val newResponse = XposedHelpers.callMethod(builder, "build")

            XposedLog.i("特权 API 响应已替换为 Mock 数据")
            return newResponse
        } catch (t: Throwable) {
            XposedLog.e("替换特权 API 响应失败", t)
            return response
        }
    }

    private fun hookPrivilegeModel(classLoader: ClassLoader) {
        XposedLog.i(">> Hook Privilege 模型 (双保险)")
        HookHelper.findAndHookMethod(
            classLoader,
            "com.same.latest.data.Privilege",
            "getEnabled",
            HookHelper.alwaysReturn(1, "Privilege.getEnabled")
        )
    }
}
