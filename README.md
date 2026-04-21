# SameVipHook

**com.same.android LSPosed VIP 功能解锁模块** | 仅供学习研究使用

---

## 📋 前提条件

| 项目 | 要求 |
|------|------|
| Android 设备 | 已获取 Root 权限 |
| 框架 | 已安装 [LSPosed](https://github.com/LSPosed/LSPosed)（推荐 Zygisk 版本） |
| 目标应用 | com.same.android v6.2.2 已安装 |
| 编译环境 | 无需本地环境，使用 GitHub Actions 在线编译 |

---

## 🔨 编译（GitHub Actions）

### 方式一：自动编译（推荐）

1. 将本项目上传到你的 GitHub 仓库（Fork 或新建均可）
2. 进入仓库 → **Actions** 标签页
3. 选择 **Build SameVipHook APK** workflow
4. 点击 **Run workflow**
5. 等待构建完成（约 2-3 分钟）
6. 在构建结果页面下载 **SameVipHook-Debug.zip**，解压得到 APK

### 方式二：手动触发编译

1. 进入 Actions → Build SameVipHook APK
2. 点击 **Run workflow** → 选择分支 → 点击绿色的 **Run workflow** 按钮

### 可选：Release 签名编译

如果需要 Release 签名 APK，在仓库 **Settings → Secrets and variables → Actions** 中添加以下密钥：

| Secret 名称 | 说明 |
|-------------|------|
| `KEYSTORE_BASE64` | 将 `.jks` / `.keystore` 文件 Base64 编码后的字符串 |
| `KEYSTORE_PASSWORD` | Keystore 密码 |
| `KEY_ALIAS` | Key 别名 |
| `KEY_PASSWORD` | Key 密码 |

> 生成 Base64：`base64 -i your.keystore | pbcopy`（macOS）或 `certutil -encode your.keystore encoded.txt`（Windows）

---

## 📱 安装与激活

### 步骤一：安装 APK

将编译得到的 APK 安装到手机（Debug 签名即可正常使用于 LSPosed）

### 步骤二：激活模块

1. 打开 **LSPosed Manager**
2. 进入 **模块** 页面
3. 找到 **SameVipHook** → 开启开关
4. 在弹出的作用域选择中勾选 **com.same.android**
5. **强制停止** 目标应用（或在 LSPosed 中重启）
6. 重新打开 same 应用

### 步骤三：验证生效

1. 打开 LSPosed Manager → **日志** 页面
2. 搜索关键词 `SameVipHook`
3. 应能看到类似以下日志：
   ```
   [SameVipHook-I] ╔══════════════════════════════════════════════╗
   [SameVipHook-I] ║  SameVipHook v1.0.0 已加载                 ║
   [SameVipHook-I] ║  目标包名: com.same.android
   [SameVipHook-I] ╚══════════════════════════════════════════════╝
   [SameVipHook-I] ====== 开始 Hook VIP 状态判断方法 ======
   [SameVipHook-I] Hook 成功: com.same.android.utils.LocalUserInfoUtils$MyUserInfo.isVip
   [SameVipHook-I] Hook 成功: com.same.android.utils.LocalUserInfoUtils.getIsVip
   ...
   [SameVipHook-I] ✅ 所有 Hook 组加载完成，模块运行中
   ```

---

## 🎯 Hook 功能清单

### ★★★ VIP 状态解锁（10 个方法）

| # | Hook 目标 | 原始返回 | 替换为 | 对应报告位置 |
|---|-----------|---------|--------|-------------|
| 1 | `LocalUserInfoUtils$MyUserInfo.isVip()` | boolean | `true` | 4.1 位置1 |
| 2 | `LocalUserInfoUtils.getIsVip()` | int | `1` | 4.1 位置2 |
| 3 | `ProfileManager.isVip()` | boolean | `true` | 4.1 位置4 |
| 4 | `ProfileManager.isWwjVip()` | boolean | `true` | 4.1 位置5 |
| 5 | `WwjProfile.isVip()` | boolean | `true` | 4.1 位置6 |
| 6 | `UserInfo.isVip()` | boolean | `true` | 4.1 位置7 |
| 7 | `UserInfo.getIs_vip()` | int | `1` | 附录 |
| 8 | `SimpleUser.isVip()` | boolean | `true` | 4.1 位置8 |
| 9 | `User.isVip()` | int | `1` | 4.1 位置9 |
| 10 | `LoginUserDto.getIs_vip()` | int | `1` | 附录 |

### ★★ VIP 数据增强（2 个方法）

| # | Hook 目标 | 功能 | 对应报告位置 |
|---|-----------|------|-------------|
| 1 | `UserVipInfo.getLevel()` | 非会员时返回等级 1 | 4.2 |
| 2 | `UserVipInfo.getExpiresAt()` | 过期时延长至 1 年后 | 4.2 |

### ★ 其他（1 个方法）

| # | Hook 目标 | 功能 | 对应报告位置 |
|---|-----------|------|-------------|
| 1 | `ProductOrderDto.isPaid()` | 订单显示为已支付 | 4.4 |

---

## 📂 项目结构

```
SameVipHook/
├── app/src/main/
│   ├── AndroidManifest.xml          # LSPosed 模块声明
│   ├── assets/xposed_init           # Xposed 入口类声明
│   ├── res/xml/xposed_scope.xml     # 模块作用域配置
│   └── java/com/shuishiba744/sameviphook/
│       ├── MainHook.kt              # ★ 模块入口（进程过滤 + Hook 调度）
│       ├── hook/
│       │   ├── VipStatusHook.kt     # ★★★ VIP 状态方法群（10个 Hook）
│       │   ├── VipInfoHook.kt       # ★★ UserVipInfo 数据模型（2个 Hook）
│       │   └── MiscHook.kt          # ★ 其他功能（1个 Hook）
│       └── utils/
│           ├── HookHelper.kt        # Hook 操作安全封装（异常捕获）
│           └── XposedLog.kt         # 统一日志工具
├── .github/workflows/build.yml      # GitHub Actions CI 编译流程
├── build.gradle.kts                 # 项目级构建配置
├── app/build.gradle.kts             # 模块级构建配置
└── settings.gradle.kts              # 项目设置
```

---

## 🔧 维护指南

### 新增 Hook 点

在对应的 Hook 类中添加新的 Hook 方法即可。示例：

```kotlin
// 在 VipStatusHook.kt 的 hookDataModel() 方法中添加
HookHelper.findAndHookMethod(
    classLoader,
    "com.same.android.newpackage.NewClass",
    "newIsVipMethod",
    HookHelper.alwaysReturn(true, "NewClass.newIsVipMethod")
)
```

### 适配新版本应用

如果目标应用更新后 Hook 失败：

1. 查看 LSPosed 日志，确认哪些 Hook 点失败（搜索 `[SameVipHook-E]`）
2. 使用逆向工具重新分析新版本的混淆映射
3. 更新对应 Hook 类中的类名/方法名
4. 重新编译部署

### 版本兼容性

- **开发语言**: Kotlin
- **Xposed API**: 82（兼容 LSPosed API 93+）
- **最低 Android**: 6.0 (API 23)
- **目标 Android**: 15 (API 35)
- **目标应用版本**: com.same.android v6.2.2

---

## ⚠️ 已知限制

1. **服务端校验功能受限**: 仅修改客户端 VIP 状态，依赖服务端权限校验的功能（如私密频道、频道顶帖等实际服务端操作）可能仍然受限
2. **VIP 签名未修改**: `UserVipInfo.signState` 和 `libEncryptor.so` 相关逻辑未 Hook，如果应用存在本地签名校验，可能需要进一步分析
3. **版本绑定**: Hook 目标类名和方法名与 v6.2.2 版本绑定，应用更新后混淆映射可能变更

---

## ❓ 常见问题排查

### Q: 模块不生效？

1. 确认 LSPosed Manager 中模块已 **启用**
2. 确认作用域已勾选 **com.same.android**
3. **强制停止** same 应用后重新打开
4. **重启手机**（某些情况下需要重启才能加载模块）

### Q: Hook 失败，日志有 `[SameVipHook-E]`？

- 类名或方法名在新版本中已变更，需要重新逆向分析
- 检查目标应用版本是否为 v6.2.2

### Q: same 应用闪退？

1. 确认安装的是本项目的 APK（不是旧版或其他模块）
2. 查看 LSPosed 日志中的异常堆栈
3. 禁用模块后确认应用是否恢复正常（排除其他因素）

### Q: 部分功能仍显示未开通？

- 该功能可能在 **服务端** 做 VIP 校验，客户端 Hook 无法绕过
- 这是正常现象，属于已知限制

---

## ⚖️ 免责声明

本模块仅供 **Android 逆向工程学习研究** 使用，请勿用于任何非法用途。
使用者应确保仅对自己拥有合法所有权/授权的应用进行调试和分析。
使用本模块产生的一切后果由使用者自行承担。

---

*目标应用版本: com.same.android v6.2.2 (6202)*  
*模块版本: 1.0.0*  
*分析工具: androguard 4.1.3 + Python 3.10*
