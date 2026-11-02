package cn.ppps.smscodetontfy.xposed

import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.util.Log
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface

/**
 * LSPosed 模块入口（libxposed API 102）。
 *
 * 作用域：system server（scope.list 中的虚拟包名 "system"）。
 *
 * 做两件事：
 *  1) hook ClipboardService，绕过 Android 10+ 以及 MIUI/HyperOS/ColorOS 等国产 ROM
 *     对「非前台应用」写剪贴板的限制，使短信广播接收器在后台直接写剪贴板即可生效。
 *  2) hook ActivityManagerService / ProcessList 保活：
 *     - 杀不死：拦截系统对目标应用的「计划性」杀进程（forceStop / 清理后台 / 杀进程），
 *       让系统无法主动杀掉本应用进程；
 *     - 杀死自动重启：把目标进程标记为 persistent，即使被 lmkd 等内核层强杀（SIGKILL），
 *       system_server 检测到进程死亡后也会自动重新拉起。
 */
class XposedEntry : XposedModule() {

    companion object {
        private const val TAG = "smscode-xposed"
        private const val TARGET_PACKAGE = "cn.ppps.smscodetontfy"
    }

    private fun log(msg: String) {
        log(Log.INFO, TAG, msg)
    }

    override fun onSystemServerStarting(param: XposedModuleInterface.SystemServerStartingParam) {
        log("onSystemServerStarting")
        hookClipboard(param.classLoader)
        hookKeepAlive(param.classLoader)
    }

    // ---------- 后台写剪贴板 ----------

    private fun hookClipboard(classLoader: ClassLoader) {
        val cs = try {
            Class.forName("com.android.server.clipboard.ClipboardService", false, classLoader)
        } catch (t: Throwable) {
            log("ClipboardService 未找到：${t.message}")
            return
        }

        // 1) Android 11+：剪贴板访问门禁（读/写监听均经此判断），强制放行
        hookByName(cs, "clipboardAccessAllowed") { _ -> true }

        // 2) Android 13+：设备 ID 归零，绕过「无效设备/非主屏」限制
        hookByName(cs, "getIntendingDeviceId") { _ -> 0 }

        // 3) 国产 ROM 限制后台写剪贴板：把目标应用的写入伪装成默认输入法（默认输入法始终放行）
        hookByName(cs, "setPrimaryClip") { chain ->
            val newArgs = rewriteCaller(chain)
            if (newArgs != null) chain.proceed(newArgs) else chain.proceed()
        }
    }

    private fun rewriteCaller(chain: XposedInterface.Chain): Array<Any?>? {
        if (chain.args.size < 2) return null
        val caller = chain.args[1] as? String ?: return null
        if (caller != TARGET_PACKAGE) return null
        val ime = resolveDefaultImePackage(chain.thisObject)
        if (ime.isNullOrEmpty()) return null
        val newArgs = chain.args.toTypedArray()
        newArgs[1] = ime
        log("重写 setPrimaryClip 调用方：$caller → $ime")
        return newArgs
    }

    private fun resolveDefaultImePackage(service: Any?): String? {
        val context: Context? = try {
            val m = service?.javaClass?.getDeclaredMethod("getContext")
            m?.isAccessible = true
            m?.invoke(service) as? Context
        } catch (t: Throwable) {
            try {
                val f = service?.javaClass?.getDeclaredField("mContext")
                f?.isAccessible = true
                f?.get(service) as? Context
            } catch (t2: Throwable) {
                null
            }
        }
        return try {
            val resolver = context?.contentResolver ?: return null
            val ime = Settings.Secure.getString(resolver, Settings.Secure.DEFAULT_INPUT_METHOD)
            if (ime.isNullOrEmpty()) null else ComponentName.unflattenFromString(ime)?.packageName
        } catch (t: Throwable) {
            log("解析默认输入法失败：${t.message}")
            null
        }
    }

    // ---------- 后台保活：杀不死 + 被杀自动重启 ----------

    private fun hookKeepAlive(classLoader: ClassLoader) {
        val ams = try {
            Class.forName("com.android.server.am.ActivityManagerService", false, classLoader)
        } catch (t: Throwable) {
            log("ActivityManagerService 未找到：${t.message}")
            return
        }

        // 杀不死 —— 拦截系统对目标应用的各类「计划性」杀进程入口，直接放空，不让它杀。
        hookByName(ams, "forceStopPackage") { chain ->
            if (isTargetKill(chain.args)) { log("已拦截 forceStopPackage（杀不死）"); null }
            else chain.proceed()
        }
        hookByName(ams, "killBackgroundProcesses") { chain ->
            if (isTargetKill(chain.args)) { log("已拦截 killBackgroundProcesses（杀不死）"); null }
            else chain.proceed()
        }
        hookByName(ams, "killPackageProcessesLocked") { chain ->
            if (isTargetKill(chain.args)) { log("已拦截 killPackageProcessesLocked（杀不死）"); null }
            else chain.proceed()
        }
        hookByName(ams, "killApplicationProcess") { chain ->
            if (isTargetKill(chain.args)) { log("已拦截 killApplicationProcess（杀不死）"); null }
            else chain.proceed()
        }
        hookByName(ams, "killApplication") { chain ->
            if (isTargetKill(chain.args)) { log("已拦截 killApplication（杀不死）"); null }
            else chain.proceed()
        }

        // 杀死自动重启 —— 把目标进程标记为 persistent，进程死亡后 system_server 会自动重新拉起
        // （覆盖 killPackageProcessesLocked 等拦不住的内核层强杀，如 lmkd 内存回收）。
        val processList = try {
            Class.forName("com.android.server.am.ProcessList", false, classLoader)
        } catch (t: Throwable) {
            log("ProcessList 未找到：${t.message}")
            return
        }
        hookByName(processList, "startProcessLocked") { chain ->
            val result = chain.proceed()
            markPersistentIfTarget(result)
            result
        }
    }

    /** 判断某次 kill 调用是否针对本应用（packageName 或 processName，含子进程） */
    private fun isTargetKill(args: List<Any?>): Boolean {
        for (arg in args) {
            if (arg is String && (arg == TARGET_PACKAGE || arg.startsWith("$TARGET_PACKAGE:"))) {
                return true
            }
        }
        return false
    }

    /** 若进程属于本应用，标记 persistent，使被杀后由 system_server 自动重启 */
    private fun markPersistentIfTarget(processRecord: Any?) {
        if (processRecord == null) return
        try {
            val info = field(processRecord, "info") ?: return
            val pkg = field(info, "packageName") as? String ?: return
            if (pkg != TARGET_PACKAGE) return
            val f = processRecord.javaClass.getDeclaredField("persistent")
            f.isAccessible = true
            f.setBoolean(processRecord, true)
            log("已标记目标进程为 persistent（被杀自动重启）")
        } catch (t: Throwable) {
            log("标记 persistent 失败：${t.message}")
        }
    }

    /** 反射读取字段，沿类层次向上查找（字段可能在父类中） */
    private fun field(obj: Any, name: String): Any? {
        var c: Class<*>? = obj.javaClass
        while (c != null) {
            try {
                val f = c.getDeclaredField(name)
                f.isAccessible = true
                return f.get(obj)
            } catch (t: Throwable) {
                // 字段不在当前类，继续查父类
            }
            c = c.superclass
        }
        return null
    }

    /**
     * 按方法名 hook 一个类（含父类）中所有同名重载方法，等价于旧版 hookAllMethods。
     */
    private fun hookByName(clazz: Class<*>, name: String, block: (XposedInterface.Chain) -> Any?) {
        var c: Class<*>? = clazz
        var hooked = 0
        while (c != null) {
            for (method in c.declaredMethods) {
                if (method.name == name) {
                    try {
                        // 反优化以绕过 JIT 方法内联（hook System Framework 时尤其需要）
                        deoptimize(method)
                        hook(method).intercept { chain -> block(chain) }
                        hooked++
                    } catch (t: Throwable) {
                        log("hook $name 失败：${t.message}")
                    }
                }
            }
            c = c.superclass
        }
        log("已 hook $name（$hooked 个重载）")
    }
}
