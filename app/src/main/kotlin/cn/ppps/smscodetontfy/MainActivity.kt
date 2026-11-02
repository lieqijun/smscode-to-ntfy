package cn.ppps.smscodetontfy

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Base64
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import cn.ppps.smscodetontfy.data.AppSettings
import cn.ppps.smscodetontfy.data.ConfigStore
import cn.ppps.smscodetontfy.data.Mode
import cn.ppps.smscodetontfy.data.NtfyConfig
import cn.ppps.smscodetontfy.databinding.ActivityMainBinding
import cn.ppps.smscodetontfy.service.KeepAliveService
import cn.ppps.smscodetontfy.util.ClientCertCrypto
import cn.ppps.smscodetontfy.util.ClientCertUtils
import cn.ppps.smscodetontfy.util.NtfySender
import java.io.ByteArrayOutputStream

class MainActivity : Activity() {
    private lateinit var binding: ActivityMainBinding
    private var clientCertEnc: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 加载配置
        ConfigStore.load(this)?.let { cfg ->
            binding.etServer.setText(cfg.server)
            binding.etTopic.setText(cfg.topic)
            binding.etToken.setText(cfg.token)
            clientCertEnc = cfg.clientCert
            binding.swMtls.isChecked = cfg.clientCert.isNotEmpty()
            updateCertStatus()
        }

        // 加载设置
        binding.swAutoCopy.isChecked = AppSettings.autoCopySmsCode
        binding.swKeepAlive.isChecked = AppSettings.foregroundKeepAlive
        binding.swBoot.isChecked = AppSettings.bootStart

        // 模式切换
        binding.btnModeNormal.setOnClickListener {
            AppSettings.mode = Mode.NORMAL
            updateModeButtons()
            syncKeepAlive()
        }
        binding.btnModeLsposed.setOnClickListener {
            AppSettings.mode = Mode.LSPOSED
            updateModeButtons()
            syncKeepAlive()
        }
        updateModeButtons()

        // 设置开关
        binding.swAutoCopy.setOnCheckedChangeListener { _, isChecked ->
            AppSettings.autoCopySmsCode = isChecked
        }
        binding.swKeepAlive.setOnCheckedChangeListener { _, isChecked ->
            AppSettings.foregroundKeepAlive = isChecked
            syncKeepAlive()
        }
        binding.swBoot.setOnCheckedChangeListener { _, isChecked ->
            AppSettings.bootStart = isChecked
        }

        // mTLS 证书
        binding.swMtls.setOnCheckedChangeListener { _, isChecked ->
            binding.btnImportCert.isEnabled = isChecked
            if (!isChecked) {
                clientCertEnc = ""
                updateCertStatus()
            }
        }
        binding.btnImportCert.isEnabled = binding.swMtls.isChecked
        binding.btnImportCert.setOnClickListener { launchCertPicker() }

        // 保存 / 测试
        binding.btnSave.setOnClickListener { save() }
        binding.btnTest.setOnClickListener { testSend() }

        // 应用设置引导
        binding.btnGuide.setOnClickListener {
            try {
                startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", packageName, null)
                })
            } catch (e: Exception) {
                startActivity(Intent(Settings.ACTION_SETTINGS))
            }
        }

        requestNeededPermissions()
        syncKeepAlive()
    }

    private fun updateModeButtons() {
        val lsposed = AppSettings.mode == Mode.LSPOSED
        setModeButtonStyle(binding.btnModeNormal, !lsposed)
        setModeButtonStyle(binding.btnModeLsposed, lsposed)
        binding.tvModeHint.text =
            if (lsposed) getString(R.string.mode_lsposed_hint) else getString(R.string.mode_normal_hint)
        // LSPosed 模式：进程保活由 Xposed hook 实现，前台保活（常驻通知）可选，不强制
    }

    private fun setModeButtonStyle(v: TextView, selected: Boolean) {
        if (selected) {
            v.setBackgroundColor(color(R.color.purple_500))
            v.setTextColor(color(R.color.white))
        } else {
            v.setBackgroundColor(color(R.color.card_bg))
            v.setTextColor(color(R.color.black))
        }
    }

    private fun syncKeepAlive() {
        // 前台服务只由「前台保活」开关控制；LSPosed 模式下保活靠 hook，前台服务可选
        if (AppSettings.foregroundKeepAlive) KeepAliveService.start(this) else KeepAliveService.stop(this)
    }

    private fun color(id: Int): Int =
        if (Build.VERSION.SDK_INT >= 23) getColor(id)
        else @Suppress("DEPRECATION") resources.getColor(id)

    // ---------- 证书导入 ----------

    private fun launchCertPicker() {
        startActivityForResult(
            Intent(Intent.ACTION_GET_CONTENT).apply {
                type = "*/*"
                addCategory(Intent.CATEGORY_OPENABLE)
            },
            REQ_PICK_CERT
        )
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_PICK_CERT && resultCode == RESULT_OK) {
            data?.data?.let { promptCertPassword(it) }
        }
    }

    private fun updateCertStatus() {
        binding.tvCertStatus.text =
            if (clientCertEnc.isNotEmpty()) "已导入客户端证书" else "未导入证书"
    }

    private fun promptCertPassword(uri: Uri) {
        val input = EditText(this)
        input.hint = "输入 P12 密码"
        AlertDialog.Builder(this)
            .setTitle("导入 mTLS 证书")
            .setView(input)
            .setPositiveButton("确定") { _, _ -> importCert(uri, input.text.toString()) }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun importCert(uri: Uri, pwd: String) {
        Thread {
            try {
                val bytes = contentResolver.openInputStream(uri)?.use { readBytes(it) }
                    ?: throw Exception("无法读取文件")
                ClientCertUtils.verifyP12(bytes, pwd)
                val newPwd = ClientCertCrypto.deriveBksPassword()
                val pkcs12 = ClientCertUtils.p12BytesToPkcs12Bytes(bytes, pwd, newPwd)
                val enc = ClientCertCrypto.encrypt(pkcs12)
                clientCertEnc = Base64.encodeToString(enc, Base64.NO_WRAP)
                runOnUiThread {
                    updateCertStatus()
                    Toast.makeText(this, "证书导入成功", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(this, "证书导入失败: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }.start()
    }

    private fun readBytes(input: java.io.InputStream): ByteArray {
        val buf = ByteArrayOutputStream()
        val tmp = ByteArray(4096)
        var n: Int
        while (input.read(tmp).also { n = it } > 0) buf.write(tmp, 0, n)
        return buf.toByteArray()
    }

    // ---------- 保存 / 测试 ----------

    private fun buildConfig(): NtfyConfig? {
        val server = binding.etServer.text.toString().trim()
        val topic = binding.etTopic.text.toString().trim()
        val token = binding.etToken.text.toString().trim()
        if (server.isEmpty() || topic.isEmpty()) {
            Toast.makeText(this, "地址和主题不能为空", Toast.LENGTH_SHORT).show()
            return null
        }
        if (binding.swMtls.isChecked && clientCertEnc.isEmpty()) {
            Toast.makeText(this, "请先导入 mTLS 证书", Toast.LENGTH_SHORT).show()
            return null
        }
        return NtfyConfig(
            server = server,
            topic = topic,
            token = token,
            clientCert = if (binding.swMtls.isChecked) clientCertEnc else "",
            enabled = true
        )
    }

    private fun save() {
        val cfg = buildConfig() ?: return
        ConfigStore.save(this, cfg)
        Toast.makeText(this, "已保存", Toast.LENGTH_SHORT).show()
    }

    private fun testSend() {
        val cfg = buildConfig() ?: return
        Thread {
            val ok = NtfySender.send(cfg, "这是一条来自 SMS→ntfy 的测试消息")
            runOnUiThread {
                Toast.makeText(
                    this, if (ok) "测试发送成功" else "测试发送失败", Toast.LENGTH_SHORT
                ).show()
            }
        }.start()
    }

    // ---------- 权限 ----------

    private fun requestNeededPermissions() {
        if (Build.VERSION.SDK_INT < 23) return
        val needed = mutableListOf<String>()
        if (checkSelfPermission(Manifest.permission.RECEIVE_SMS) != PackageManager.PERMISSION_GRANTED) {
            needed.add(Manifest.permission.RECEIVE_SMS)
        }
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            needed.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (needed.isNotEmpty()) requestPermissions(needed.toTypedArray(), REQ_PERM)
    }

    companion object {
        private const val REQ_PERM = 100
        private const val REQ_PICK_CERT = 200
    }
}
