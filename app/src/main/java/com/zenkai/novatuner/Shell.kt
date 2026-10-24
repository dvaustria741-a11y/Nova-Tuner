package com.zenkai.novatuner

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import rikka.shizuku.Shizuku

object Shell {
    @Volatile
    var service: IUserService? = null
    var onChange: (() -> Unit)? = null
    private var binding = false

    private val conn = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            service = IUserService.Stub.asInterface(binder)
            binding = false
            onChange?.invoke()
        }

        override fun onServiceDisconnected(name: ComponentName) {
            service = null
            binding = false
            onChange?.invoke()
        }
    }

    fun running(): Boolean = try {
        Shizuku.pingBinder()
    } catch (e: Throwable) {
        false
    }

    fun granted(): Boolean = try {
        running() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    } catch (e: Throwable) {
        false
    }

    fun requestPermission() {
        try {
            Shizuku.requestPermission(100)
        } catch (e: Throwable) {
        }
    }

    fun bind(ctx: Context) {
        if (service != null || binding || !granted()) return
        try {
            binding = true
            val args = Shizuku.UserServiceArgs(ComponentName(ctx.packageName, UserService::class.java.name))
                .processNameSuffix("shell")
                .daemon(false)
                .version(1)
            Shizuku.bindUserService(args, conn)
        } catch (e: Throwable) {
            binding = false
        }
    }

    fun exec(cmd: String, cb: (String) -> Unit) {
        Thread {
            val out = try {
                service?.exec(cmd) ?: "Shizuku service not connected"
            } catch (e: Throwable) {
                "Error: ${e.message}"
            }
            Handler(Looper.getMainLooper()).post { cb(out) }
        }.start()
    }
}
