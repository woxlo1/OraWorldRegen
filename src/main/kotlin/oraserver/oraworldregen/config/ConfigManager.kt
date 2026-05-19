package oraserver.oraworldregen.config

import oraserver.oraworldregen.OraWorldRegen
import oraserver.oraworldregen.model.WorldRegenConfig
import org.bukkit.World

class ConfigManager(private val plugin: OraWorldRegen) {

    val worldConfigs = LinkedHashMap<String, WorldRegenConfig>()
    var notifyAtSeconds: List<Int> = listOf(300, 120, 60, 30, 10, 5, 4, 3, 2, 1)
    var timezone: String = "Asia/Tokyo"
    var debug: Boolean = false

    fun load() {
        worldConfigs.clear()
        plugin.reloadConfig()

        val worldsSec = plugin.config.getConfigurationSection("worlds")
        worldsSec?.getKeys(false)?.forEach { key ->
            val ws = worldsSec.getConfigurationSection(key) ?: return@forEach

            val crons = ws.getMapList("schedules")
                .mapNotNull { it["cron"]?.toString() }

            worldConfigs[key] = WorldRegenConfig(
                worldName           = key,
                multiverseWorldName = ws.getString("multiverse-world-name", key)!!,
                environment         = parseEnv(ws.getString("environment", "NORMAL")),
                worldType           = ws.getString("world-type", "NORMAL")!!,
                seed                = ws.getString("seed", "")!!,
                generator           = ws.getString("generator", "")!!,
                cronSchedules       = crons,
                countdownSeconds    = ws.getInt("countdown-seconds", 300),
                fallbackWorld       = ws.getString("fallback-world", "world")!!,
                enabled             = ws.getBoolean("enabled", true)
            )
        }

        val settings = plugin.config.getConfigurationSection("settings")
        if (settings != null) {
            notifyAtSeconds = settings.getList("notify-at-seconds")
                ?.filterIsInstance<Number>()
                ?.map { it.toInt() }
                ?: notifyAtSeconds
            timezone = settings.getString("timezone", "Asia/Tokyo")!!
            debug    = settings.getBoolean("debug", false)
        }

        plugin.logger.info("${worldConfigs.size} ワールド設定を読み込みました。")
    }

    private fun parseEnv(s: String?) = when (s?.uppercase()) {
        "NETHER"  -> World.Environment.NETHER
        "THE_END" -> World.Environment.THE_END
        else      -> World.Environment.NORMAL
    }
}
