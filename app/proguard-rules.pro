# 极简 ntfy 转发 App（纯框架实现）
# 保留本应用全部类与成员，避免误删
-keepattributes *Annotation*
-keep class cn.ppps.smscodetontfy.** { *; }

# 清单组件（Activity/Receiver/Service）R8 会自动保留，这里兜底
-keep public class * extends android.app.Activity
-keep public class * extends android.content.BroadcastReceiver
-keep public class * extends android.app.Service

# libxposed API 官方规则：保留 XposedModule 入口类并在混淆后同步修正 java_init.list
-dontwarn io.github.libxposed.annotation.**
-adaptresourcefilecontents META-INF/xposed/java_init.list
-keep,allowoptimization,allowobfuscation public class * extends io.github.libxposed.api.XposedModule {
    public <init>();
}

# libxposed API 为 compileOnly，运行时由 LSPosed 框架提供，忽略缺失告警
-dontwarn io.github.libxposed.**
