package com.zenkai.novatuner

import java.util.Locale

class StatsResult(val front: String?, val fps: Int?, val tempC: Float?, val freeGb: Float?)

object Stats {
    fun script(front: String?): String {
        val p = front ?: ""
        return "P='" + p + "'\n" +
            "echo @@F\n" +
            "dumpsys activity activities | grep -E 'ResumedActivity' | head -n 2\n" +
            "echo @@T\n" +
            "dumpsys battery | grep temperature\n" +
            "echo @@M\n" +
            "grep -E 'MemAvailable' /proc/meminfo\n" +
            "echo @@L\n" +
            "if [ -n \"\$P\" ]; then\n" +
            "L=\$(dumpsys SurfaceFlinger --list | grep -F \"\$P\" | grep -F SurfaceView | tail -n 1)\n" +
            "[ -z \"\$L\" ] && L=\$(dumpsys SurfaceFlinger --list | grep -F \"\$P\" | tail -n 1)\n" +
            "[ -n \"\$L\" ] && dumpsys SurfaceFlinger --latency \"\$L\"\n" +
            "fi\n"
    }

    fun parse(out: String): StatsResult {
        val sec = mutableMapOf<String, String>()
        var cur: String? = null
        val sb = StringBuilder()
        for (line in out.lines()) {
            if (line.startsWith("@@")) {
                val c = cur
                if (c != null) sec[c] = sb.toString()
                cur = line.substring(2).trim()
                sb.setLength(0)
            } else {
                sb.append(line).append('\n')
            }
        }
        val last = cur
        if (last != null) sec[last] = sb.toString()

        val pkg = Regex("u\\d+ ([A-Za-z0-9_.]+)/").find(sec["F"] ?: "")?.groupValues?.get(1)

        val temp = Regex("temperature:\\s*(\\d+)").find(sec["T"] ?: "")?.groupValues?.get(1)?.toFloatOrNull()?.div(10f)

        val kb = Regex("MemAvailable:\\s*(\\d+)").find(sec["M"] ?: "")?.groupValues?.get(1)?.toFloatOrNull()
        val free = kb?.let { it / 1048576f }

        var fps: Int? = null
        val times = mutableListOf<Long>()
        val lines = (sec["L"] ?: "").lines()
        for (i in 1 until lines.size) {
            val parts = lines[i].trim().split(Regex("\\s+"))
            if (parts.size >= 3) {
                val v = parts[1].toLongOrNull()
                if (v != null && v > 0L && v < 9_000_000_000_000_000_000L) times.add(v)
            }
        }
        if (times.size >= 8) {
            times.sort()
            val span = (times.last() - times.first()) / 1_000_000_000.0
            if (span > 0.2) fps = Math.round((times.size - 1) / span).toInt()
        }
        return StatsResult(pkg, fps, temp, free)
    }

    fun format(s: StatsResult): String {
        val f = s.fps?.toString() ?: "--"
        val t = if (s.tempC != null) String.format(Locale.US, "%.1fC", s.tempC) else "--"
        val m = if (s.freeGb != null) String.format(Locale.US, "%.1fG free", s.freeGb) else "--"
        return "$f FPS  $t  $m"
    }
}
