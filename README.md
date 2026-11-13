# smscode-to-ntfy

极简 Android 应用：把手机收到的短信（自动提取验证码）转发到 [ntfy](https://ntfy.sh)，支持 mTLS 双向认证、自动复制验证码到剪贴板、前台常驻。

## 功能

- 收到短信自动提取验证码并转发到 ntfy（仅含验证码关键词的短信才转发，只推 4 位/6 位验证码本身；积分、账单、营销等通知类短信不转发）
- 自动复制验证码到剪贴板
- 客户端证书（mTLS）加密存储，密码不落盘（Android Keystore）
- 前台常驻通知，可选开机自启
- 服务端证书宽松信任，兼容自签 / 内网域名 ntfy 服务器
- 纯框架实现（零 AndroidX 依赖），APK 体积小
- 单页配置：地址 / 主题 / 访问令牌 / mTLS

## 两种运行模式

| 模式 | 说明 |
|---|---|
| 普通模式 | 无需 Root。后台写剪贴板在部分国产 ROM 上可能受限，如复制失效需将「写入剪贴板」设为「始终允许」 |
| LSPosed 模式 | 需 Root + LSPosed。启用模块后后台写剪贴板直接生效、无需授权，并通过 Hook 保活：后台杀不死、被杀自动重启 |

### LSPosed 模式使用前提

1. 手机已 Root 并安装 LSPosed **2.2.0+**（基于 libxposed API 102；KernelSU / Magisk + Zygisk）。
2. 安装本 APK 后，在 LSPosed 管理器中启用本模块。
3. 作用域已通过静态作用域声明固定为「系统框架（System Framework）」，无需手动勾选。
4. 在 App 内切换到「LSPosed 模式」。
5. 重启设备后生效。Hook 日志 tag：`smscode-xposed`。

> 说明：模块 Hook 系统剪贴板服务以放行后台写入，并 Hook ActivityManagerService / ProcessList 保活——拦截系统对目标进程的「计划性」杀进程（杀不死），并把目标进程标记为 persistent 使其被杀后自动重启（含 lmkd 内核层强杀）。

## 使用

1. 打开 App，填写 ntfy 配置：
   - 地址：ntfy 服务器，如 `https://ntfy.sh`
   - 主题：topic 名称
   - 访问令牌：服务端开启了 Token 鉴权时填写（可选）
   - mTLS：自建服务器要求客户端证书时开启并导入 `.p12`
2. 点击「保存配置」；「发送测试」验证连通性。
3. 首次使用授予「短信」「通知」权限，并在系统电池设置里设为「不受限制」。

## 构建

- 本地：`bash ./gradlew assembleRelease`
- CI：打 `v*.*.*` tag 推送即触发 GitHub Actions 出包。

## 开源协议

MIT
