package com.shuishiba744.sameviphook.hook

import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import com.shuishiba744.sameviphook.utils.XposedLog
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.lang.reflect.Proxy

/**
 * VIP 特权 API 网络响应拦截 Hook
 *
 * 背景: "无痕浏览"等 VIP 特权功能不走 isVip()，通过服务端 API 校验。
 * 抓包: GET /member/privilege/config/list -> {"code":403, ...}
 *
 * 策略:
 *   1. 拦截 OkHttp RealCall 响应替换为 Mock 数据
 *   2. Hook Privilege.getEnabled() 强制返回 1
 */
object PrivilegeApiHook {

    private const val PRIVILEGE_KEYWORD = "/member/privilege/"

    private val MOCK_RESPONSE = """{"code":200,"message":"success","data":[{"memberPrivilegeId":1,"enabled":1},{"memberPrivilegeId":2,"enabled":1},{"memberPrivilegeId":3,"enabled":1},{"memberPrivilegeId":4,"enabled":1},{"memberPrivilegeId":5,"enabled":1},{"memberPrivilegeId":6,"enabled":1},{"memberPrivilegeId":7,"enabled":1}]}"""

    fun hook(classLoader: ClassLoader) {
        XposedLog.i("====== 开始 Hook 特权 API 拦截 ======")

        // 尝试多种 RealCall 路径
        val realCallClass = tryFindClass(classLoader,
            "okhttp3.RealCall",
            "okhttp3.internal.connection.RealCall"
        )

        if (realCallClass != null) {
            XposedLog.i("找到 RealCall 类: ${realCallClass.name}")
            hookSyncExecute(realCallClass, classLoader)
            hookAsyncEnqueue(realCallClass, classLoader)
        } else {
            XposedLog.e("未找到 OkHttp RealCall 类，尝试备用方案")
            hookResponseBodyString(classLoader)
        }

        hookPrivilegeModel(classLoader)
        XposedLog.i("====== 特权 API Hook 完成 ======")
    }

    // ==================== OkHttp RealCall 拦截 ====================

    private fun hookSyncExecute(realCallClass: Class<*>, classLoader: ClassLoader) {
        try {
            val methods = realCallClass.declaredMethods.filter { it.name == "execute" }
            if (methods.isEmpty()) {
                XposedLog.e("RealCall.execute() 方法未找到")
                return
            }
            for (method in methods) {
                XposedBridge.hookMethod(method, object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        try {
                            val response = param.result ?: return
                            val url = getUrlFromResponse(response)
                            if (!url.contains(PRIVILEGE_KEYWORD)) return

                            XposedLog.i("[同步] 拦截特权 API: $url")
                            val newResponse = buildMockResponse(response, classLoader)
                            if (newResponse != null) {
                                param.result = newResponse
                                XposedLog.i("[同步] 响应已替换为 Mock 数据")
                            } else {
                                XposedLog.e("[同步] Mock Response 构建失败")
                            }
                        } catch (t: Throwable) {
                            XposedLog.e("[同步] 拦截异常", t)
                        }
                    }
                })
            }
            XposedLog.i("Hook RealCall.execute() 成功 (${methods.size}个重载)")
        } catch (t: Throwable) {
            XposedLog.e("Hook RealCall.execute() 失败", t)
        }
    }

    private fun hookAsyncEnqueue(realCallClass: Class<*>, classLoader: ClassLoader) {
        try {
            val callbackIface = tryFindClass(classLoader, "okhttp3.Callback")
            if (callbackIface == null) {
                XposedLog.e("未找到 okhttp3.Callback 接口")
                return
            }

            val methods = realCallClass.declaredMethods.filter { it.name == "enqueue" }
            if (methods.isEmpty()) {
                XposedLog.e("RealCall.enqueue() 方法未找到")
                return
            }
            for (method in methods) {
                XposedBridge.hookMethod(method, object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        try {
                            // 先检查 URL，只对特权 API 请求做代理包装
                            val url = getUrlFromCall(param.thisObject)
                            if (!url.contains(PRIVILEGE_KEYWORD)) return

                            XposedLog.i("[异步] 拦截特权 API: $url")
                            val originalCallback = param.args[0] ?: return
                            param.args[0] = createCallbackProxy(
                                originalCallback, callbackIface, classLoader
                            )
                        } catch (t: Throwable) {
                            XposedLog.e("[异步] 拦截异常", t)
                        }
                    }
                })
            }
            XposedLog.i("Hook RealCall.enqueue() 成功 (${methods.size}个重载)")
        } catch (t: Throwable) {
            XposedLog.e("Hook RealCall.enqueue() 失败", t)
        }
    }

    // ==================== 备用方案: Hook ResponseBody.string() ====================

    private fun hookResponseBodyString(classLoader: ClassLoader) {
        try {
            val responseBodyClass = XposedHelpers.findClass("okhttp3.ResponseBody", classLoader)
            XposedBridge.hookAllMethods(responseBodyClass, "string", object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val result = param.result as? String ?: return
                    // 检查是否包含 403 码的特权响应
                    if ((result.contains("\"code\":403") || result.contains("\"code\": 403"))
                        && result.contains("privilege")) {
                        XposedLog.i("[备用] ResponseBody.string() 检测到特权 403，替换为 Mock")
                        param.result = MOCK_RESPONSE
                    }
                }
            })
            XposedLog.i("备用方案: Hook ResponseBody.string() 成功")
        } catch (t: Throwable) {
            XposedLog.e("备用方案 Hook 失败", t)
        }
    }

    // ==================== 响应构建 ====================

    private fun getUrlFromCall(call: Any): String {
        return try {
            val request = XposedHelpers.getObjectField(call, "originalRequest")
                ?: XposedHelpers.callMethod(call, "request")
            XposedHelpers.callMethod(request, "url").toString()
        } catch (t: Throwable) {
            XposedLog.e("getUrlFromCall 失败: ${t.message}")
            ""
        }
    }

    private fun getUrlFromResponse(response: Any): String {
        return try {
            val request = XposedHelpers.callMethod(response, "request")
            XposedHelpers.callMethod(request, "url").toString()
        } catch (t: Throwable) {
            XposedLog.e("getUrlFromResponse 失败: ${t.message}")
            ""
        }
    }

    /**
     * 构建 Mock Response — 替换原始 Response 的 body
     *
     * 使用直接 Java 反射调用，避免 XposedHelpers.callStaticMethod
     * 在参数类型模糊匹配时（如 null contentType）找不到方法的问题。
     */
    private fun buildMockResponse(originalResponse: Any, classLoader: ClassLoader): Any? {
        try {
            // 1. 构建 MediaType "application/json"
            val mediaTypeClass = XposedHelpers.findClass("okhttp3.MediaType", classLoader)
            val parseMethod = mediaTypeClass.getDeclaredMethod("parse", String::class.java)
            val jsonMediaType = parseMethod.invoke(null, "application/json; charset=utf-8")

            // 2. 调用 ResponseBody.create(MediaType, String) 创建 Mock Body
            val responseBodyClass = XposedHelpers.findClass("okhttp3.ResponseBody", classLoader)
            val createMethod = responseBodyClass.getDeclaredMethod("create", mediaTypeClass, String::class.java)
            val mockBody = createMethod.invoke(null, jsonMediaType, MOCK_RESPONSE)

            XposedLog.d("Mock ResponseBody 创建成功")

            // 3. 用 Response.newBuilder() 替换 body
            val builder = XposedHelpers.callMethod(originalResponse, "newBuilder")
            XposedHelpers.callMethod(builder, "body", mockBody)
            return XposedHelpers.callMethod(builder, "build")
        } catch (t: Throwable) {
            XposedLog.e("buildMockResponse 失败", t)
            // 尝试 Companion 路径 (OkHttp 4.x Kotlin)
            return tryCompanionCreate(originalResponse, classLoader)
        }
    }

    private fun tryCompanionCreate(originalResponse: Any, classLoader: ClassLoader): Any? {
        return try {
            val mediaTypeClass = XposedHelpers.findClass("okhttp3.MediaType", classLoader)
            val parseMethod = mediaTypeClass.getDeclaredMethod("parse", String::class.java)
            val jsonMediaType = parseMethod.invoke(null, "application/json; charset=utf-8")

            val companionClass = Class.forName("okhttp3.ResponseBody\$Companion", false, classLoader)
            val createMethod = companionClass.getDeclaredMethod("create", mediaTypeClass, String::class.java)
            val mockBody = createMethod.invoke(companionClass.getDeclaredConstructor().newInstance(), jsonMediaType, MOCK_RESPONSE)

            val builder = XposedHelpers.callMethod(originalResponse, "newBuilder")
            XposedHelpers.callMethod(builder, "body", mockBody)
            XposedHelpers.callMethod(builder, "build")
        } catch (t: Throwable) {
            XposedLog.e("tryCompanionCreate 也失败了", t)
            null
        }
    }

    // ==================== 异步回调代理 ====================

    private fun createCallbackProxy(
        original: Any,
        callbackIface: Class<*>,
        classLoader: ClassLoader
    ): Any {
        return Proxy.newProxyInstance(
            callbackIface.classLoader,
            arrayOf(callbackIface),
            object : InvocationHandler {
                override fun invoke(proxy: Any, method: Method, args: Array<out Any>?): Any? {
                    return when (method.name) {
                        "onResponse" -> {
                            if (args != null && args.size >= 2) {
                                val response = args[1]
                                val newResponse = buildMockResponse(response, classLoader)
                                val finalResponse = newResponse ?: response
                                method.invoke(original, args[0], finalResponse)
                                XposedLog.i("[异步] onResponse 响应已替换")
                            } else {
                                null
                            }
                        }
                        "onFailure" -> {
                            if (args != null) method.invoke(original, *args) else null
                        }
                        "toString" -> original.toString()
                        "hashCode" -> original.hashCode()
                        "equals" -> if (args != null && args.isNotEmpty()) original == args[0] else false
                        else -> {
                            if (args != null) method.invoke(original, *args)
                            else method.invoke(original)
                        }
                    }
                }
            }
        )
    }

    // ==================== Privilege 模型 Hook (双保险) ====================

    private fun hookPrivilegeModel(classLoader: ClassLoader) {
        XposedLog.i(">> Hook Privilege 模型 (双保险)")
        try {
            val privilegeClass = XposedHelpers.findClass("com.same.latest.data.Privilege", classLoader)
            XposedBridge.hookAllMethods(privilegeClass, "getEnabled", object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    param.result = 1
                    XposedLog.d("Privilege.getEnabled() -> 1")
                }
            })
            XposedLog.i("Hook Privilege.getEnabled() 成功")
        } catch (t: Throwable) {
            XposedLog.e("Hook Privilege.getEnabled() 失败", t)
        }
    }

    // ==================== 工具方法 ====================

    private fun tryFindClass(classLoader: ClassLoader, vararg classNames: String): Class<*>? {
        for (name in classNames) {
            try {
                return XposedHelpers.findClass(name, classLoader)
            } catch (_: Throwable) {
                // 继续尝试下一个
            }
        }
        return null
    }
}
