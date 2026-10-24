package com.zenkai.novatuner

class Preset(
    val name: String,
    val desc: String,
    val mode: Int,
    val downscale: Int,
    val fps: Int,
    val angle: Boolean,
    val compile: Int,
    val standby: Boolean,
    val whitelist: Boolean,
    val killBg: Boolean
) {
    fun applyTo(p: Profile) {
        p.gameMode = mode
        p.downscale = downscale
        p.fps = fps
        p.angle = angle
        p.compile = compile
        p.standby = standby
        p.whitelist = whitelist
        p.killBg = killBg
    }
}

object Presets {
    val BATTERY = Preset("Battery Saver", "30 FPS, 70% scale", 3, 3, 1, false, 0, false, false, false)
    val BALANCED = Preset("Balanced", "60 FPS, native scale", 1, 0, 3, false, 1, true, false, false)
    val COMPETITIVE = Preset("Competitive", "90 FPS, 90% scale, RAM cleanup", 2, 1, 4, false, 1, true, true, true)
    val MAXFPS = Preset("Max FPS", "120 FPS, 80% scale", 2, 2, 5, false, 1, true, true, true)
    val LOWEND = Preset("Smooth Low-End", "60 FPS, 70% scale", 2, 3, 3, false, 1, true, false, true)

    val ALL = listOf(BATTERY, BALANCED, COMPETITIVE, MAXFPS, LOWEND)
    val QUICK = listOf(BATTERY, BALANCED, COMPETITIVE, MAXFPS)

    fun byName(n: String): Preset? = ALL.firstOrNull { it.name == n }
}

class Game(val label: String, val preset: String)

object KnownGames {
    val MAP: Map<String, Game> = mapOf(
        "com.activision.callofduty.shooter" to Game("Call of Duty: Mobile", "Competitive"),
        "com.garena.game.codm" to Game("Call of Duty: Mobile (Garena)", "Competitive"),
        "com.mobile.legends" to Game("Mobile Legends: Bang Bang", "Competitive"),
        "com.tencent.ig" to Game("PUBG Mobile", "Competitive"),
        "com.pubg.imobile" to Game("Battlegrounds Mobile India", "Competitive"),
        "com.riotgames.league.wildrift" to Game("League of Legends: Wild Rift", "Competitive"),
        "com.dts.freefireth" to Game("Free Fire", "Max FPS"),
        "com.dts.freefiremax" to Game("Free Fire MAX", "Max FPS"),
        "com.miHoYo.GenshinImpact" to Game("Genshin Impact", "Smooth Low-End"),
        "com.HoYoverse.hkrpgoversea" to Game("Honkai: Star Rail", "Smooth Low-End"),
        "com.HoYoverse.Nap" to Game("Zenless Zone Zero", "Smooth Low-End"),
        "com.mojang.minecraftpe" to Game("Minecraft", "Balanced"),
        "com.roblox.client" to Game("Roblox", "Balanced"),
        "com.supercell.brawlstars" to Game("Brawl Stars", "Balanced"),
        "com.supercell.clashroyale" to Game("Clash Royale", "Balanced"),
        "com.gameloft.android.ANMP.GloftA9HM" to Game("Asphalt 9", "Smooth Low-End")
    )
}
