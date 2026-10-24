package com.zenkai.novatuner

object Tuner {
    val MODE_LABELS = listOf("Off", "Standard", "Performance", "Battery")
    private val MODE_NAMES = listOf("", "standard", "performance", "battery")
    val DOWNSCALE_LABELS = listOf("Off", "90%", "80%", "70%", "60%", "50%")
    private val DOWNSCALE_VALUES = listOf("disable", "0.9", "0.8", "0.7", "0.6", "0.5")
    val FPS_LABELS = listOf("Off", "30", "45", "60", "90", "120")
    private val FPS_VALUES = listOf("disable", "30", "45", "60", "90", "120")
    val COMPILE_LABELS = listOf("Off", "speed-profile", "speed")
    private val COMPILE_VALUES = listOf("", "speed-profile", "speed")

    private class Script {
        val sb = StringBuilder()
        fun add(c: String) {
            sb.append("echo '\$ ").append(c).append("'\n").append(c).append("\n")
        }
        override fun toString() = sb.toString()
    }

    fun summary(p: Profile): String {
        val parts = mutableListOf<String>()
        val mode = effectiveMode(p)
        if (mode > 0) parts.add(MODE_LABELS[mode])
        if (p.downscale > 0) parts.add(DOWNSCALE_LABELS[p.downscale])
        if (p.fps > 0) parts.add("${FPS_LABELS[p.fps]} FPS")
        if (p.angle) parts.add("ANGLE")
        if (p.compile > 0) parts.add("AOT")
        return if (parts.isEmpty()) "No tweaks set" else parts.joinToString(" · ")
    }

    private fun overlayValue(mode: Int, p: Profile): String {
        val parts = mutableListOf("mode=$mode")
        if (p.downscale > 0) parts.add("downscaleFactor=${DOWNSCALE_VALUES[p.downscale]}")
        if (p.fps > 0) parts.add("fps=${FPS_VALUES[p.fps]}")
        if (p.angle) parts.add("useAngle=true")
        return parts.joinToString(",")
    }

    private fun effectiveMode(p: Profile): Int =
        if (p.gameMode == 0 && (p.downscale > 0 || p.fps > 0)) 2 else p.gameMode

    private fun angleLines(s: Script, all: List<Profile>) {
        val pkgs = all.filter { it.angle }.map { it.pkg }
        if (pkgs.isEmpty()) {
            s.add("settings delete global angle_gl_driver_selection_pkgs")
            s.add("settings delete global angle_gl_driver_selection_values")
        } else {
            s.add("settings put global angle_debug_package org.chromium.angle")
            s.add("settings put global angle_gl_driver_selection_pkgs " + pkgs.joinToString(","))
            s.add("settings put global angle_gl_driver_selection_values " + pkgs.joinToString(",") { "angle" })
        }
    }

    fun applyScript(p: Profile, all: List<Profile>, launch: Boolean): String {
        val s = Script()
        if (launch) s.add("am force-stop ${p.pkg}")
        val mode = effectiveMode(p)
        if (mode > 0) {
            if (p.downscale > 0 || p.fps > 0) {
                s.add("device_config put game_overlay ${p.pkg} ${overlayValue(mode, p)}")
                s.add("sleep 1")
            }
            var set = "cmd game set --mode ${MODE_NAMES[mode]}"
            if (p.downscale > 0) set += " --downscale ${DOWNSCALE_VALUES[p.downscale]}"
            if (p.fps > 0) set += " --fps ${FPS_VALUES[p.fps]}"
            if (p.downscale > 0 || p.fps > 0) s.add("$set ${p.pkg}")
            s.add("cmd game mode ${MODE_NAMES[mode]} ${p.pkg}")
        }
        angleLines(s, all)
        if (p.standby) s.add("am set-standby-bucket ${p.pkg} active")
        if (p.whitelist) s.add("cmd deviceidle whitelist +${p.pkg}")
        if (p.killBg) s.add("am kill-all")
        if (p.compile > 0) s.add("cmd package compile -m ${COMPILE_VALUES[p.compile]} -f ${p.pkg}")
        return s.toString()
    }

    fun restoreScript(p: Profile, others: List<Profile>): String {
        val s = Script()
        s.add("cmd game reset ${p.pkg}")
        s.add("device_config delete game_overlay ${p.pkg}")
        s.add("cmd game mode standard ${p.pkg}")
        angleLines(s, others)
        s.add("cmd deviceidle whitelist -${p.pkg}")
        if (p.standby) s.add("am set-standby-bucket ${p.pkg} working_set")
        if (p.compile > 0) s.add("cmd package compile --reset ${p.pkg}")
        return s.toString()
    }
}
