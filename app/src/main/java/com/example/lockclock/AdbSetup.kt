package com.example.lockclock

import android.content.Context

object AdbSetup {
    private const val GRANT =
        "pm grant com.example.lockclock android.permission.WRITE_SECURE_SETTINGS"

    /** Pairs with this phone's own Wireless debugging (pairing code dialog). */
    fun pair(ctx: Context, port: Int?, code: String): Result<String> = runCatching {
        requireNotNull(port) { "Pairing port not detected. Type it in manually." }
        check(code.length == 6) { "The pairing code is 6 digits." }
        val ok = AdbManager.get(ctx).pair("127.0.0.1", port, code)
        check(ok) { "Pairing failed. Keep the 'Pair with pairing code' dialog open and re-check the code." }
        "Paired."
    }

    /** Connects to Wireless debugging and grants WRITE_SECURE_SETTINGS to this app. */
    fun connectAndGrant(ctx: Context): Result<String> = runCatching {
        val mgr = AdbManager.get(ctx)
        try {
            var connected = false
            repeat(3) {
                if (!connected) {
                    connected = runCatching { mgr.connectTls(ctx, 10_000L) }.getOrDefault(false)
                    if (!connected) Thread.sleep(1500)
                }
            }
            check(connected) {
                "Couldn't connect. Make sure Wi-Fi is on, Wireless debugging is enabled, and you've paired once."
            }
            val output = mgr.openStream("shell:$GRANT").use { s ->
                s.openInputStream().bufferedReader().readText()
            }
            check(ClockSettings.canWrite(ctx)) { "Grant didn't take effect. ADB said: ${output.trim()}" }
            "Permission granted. You can turn Wireless debugging off now."
        } finally {
            runCatching { mgr.disconnect() }
        }
    }

    fun pairAndGrant(ctx: Context, port: Int?, code: String): Result<String> {
        val p = pair(ctx, port, code)
        if (p.isFailure) return p
        return connectAndGrant(ctx)
    }
}
