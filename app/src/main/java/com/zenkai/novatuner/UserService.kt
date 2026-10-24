package com.zenkai.novatuner

class UserService : IUserService.Stub() {

    override fun destroy() {
        System.exit(0)
    }

    override fun exec(command: String): String {
        return try {
            val p = ProcessBuilder("sh", "-c", command).redirectErrorStream(true).start()
            val out = p.inputStream.bufferedReader().readText()
            val code = p.waitFor()
            val trimmed = if (out.length > 200_000) out.takeLast(200_000) else out
            "$trimmed\n[exit $code]"
        } catch (e: Throwable) {
            "Error: ${e.message}"
        }
    }
}
