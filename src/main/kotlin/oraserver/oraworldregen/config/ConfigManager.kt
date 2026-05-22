package oraserver.oraworldregen.config

import oraserver.oraworldregen.OraWorldRegen
import oraserver.oraworldregen.model.GateConfig
import oraserver.oraworldregen.model.WorldRegenConfig
import org.bukkit.World

class ConfigManager(private val plugin: OraWorldRegen) {

    val worldConfigs = LinkedHashMap<String, WorldRegenConfig>()

    /** worldName → そのワールドに生成するゲート一覧 */
    val gateConfigs  = LinkedHashMap<String, MutableList<GateConfig>>()

    var notifyAtSeconds: List<Int> = listOf(300, 120, 60, 30, 10, 5, 4, 3, 2, 1)
    var timezone: String = "Asia/Tokyo"
    var debug: Boolean = false

    fun load() {
        worldConfigs.clear()
        gateConfigs.clear()
        plugin.reloadConfig()

        // ── ワールド設定 ────────────────────────────────────────────────
        val worldsSec = plugin.config.getConfigurationSection("worlds")
        worldsSec?.getKeys(false)?.forEach { key ->
            val ws = worldsSec.getConfigurationSection(key) ?: return@forEach

            val crons = ws.getMapList("schedules")
                .mapNotNull { it["cron"]?.toString() }

            val postCmds = ws.getStringList("post-regen-commands")

            val backupSec      = ws.getConfigurationSection("backup")
            val backupEnabled  = backupSec?.getBoolean("enabled", false) ?: false
            val backupDir      = backupSec?.getString("directory", "backups") ?: "backups"
            val backupMaxCount = backupSec?.getInt("max-count", 5) ?: 5

            val borderSec     = ws.getConfigurationSection("world-border")
            val borderEnabled = borderSec?.getBoolean("enabled", false) ?: false
            val borderSize    = borderSec?.getDouble("size", 2000.0) ?: 2000.0
            val borderCX      = borderSec?.getDouble("center-x", 0.0) ?: 0.0
            val borderCZ      = borderSec?.getDouble("center-z", 0.0) ?: 0.0
            val borderDmgAmt  = borderSec?.getDouble("damage-amount", 0.2) ?: 0.2
            val borderDmgBuf  = borderSec?.getDouble("damage-buffer", 5.0) ?: 5.0
            val borderWarnDis = borderSec?.getInt("warning-distance", 5) ?: 5
            val borderWarnTim = borderSec?.getInt("warning-time", 15) ?: 15

            val returnSec     = ws.getConfigurationSection("return-players")
            val returnEnabled = returnSec?.getBoolean("enabled", true) ?: true
            val returnDelay   = returnSec?.getLong("delay-seconds", 60L) ?: 60L

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
                enabled             = ws.getBoolean("enabled", true),
                backupEnabled       = backupEnabled,
                backupDirectory     = backupDir,
                backupMaxCount      = backupMaxCount,
                postRegenCommands   = postCmds,
                borderEnabled         = borderEnabled,
                borderSize            = borderSize,
                borderCenterX         = borderCX,
                borderCenterZ         = borderCZ,
                borderDamageAmount    = borderDmgAmt,
                borderDamageBuffer    = borderDmgBuf,
                borderWarningDistance = borderWarnDis,
                borderWarningTime     = borderWarnTim,
                returnPlayersAfterRegen = returnEnabled,
                returnDelay             = returnDelay
            )
        }

        // ── ゲート設定 ──────────────────────────────────────────────────
        val gatesSec = plugin.config.getConfigurationSection("gates")
        gatesSec?.getKeys(false)?.forEach { gateName ->
            val gs = gatesSec.getConfigurationSection(gateName) ?: return@forEach

            val worldName   = gs.getString("world", "").orEmpty()
            if (worldName.isEmpty()) {
                plugin.logger.warning("[Gate] ゲート '$gateName': world が未設定です。スキップします。")
                return@forEach
            }

            val frameBlock  = gs.getString("frame-block",  "OBSIDIAN").orEmpty().ifBlank { "OBSIDIAN" }
            val portalBlock = gs.getString("portal-block", "WATER").orEmpty().ifBlank { "WATER" }
            val destination = gs.getString("destination",  "w:world").orEmpty().ifBlank { "w:world" }
            val enabled     = gs.getBoolean("enabled", true)
            val owner       = gs.getString("owner", "OraWorldRegen").orEmpty().ifBlank { "OraWorldRegen" }

            val locSec = gs.getConfigurationSection("location")
            if (locSec == null) {
                plugin.logger.warning("[Gate] ゲート '$gateName': location セクションがありません。スキップします。")
                return@forEach
            }

            val gate = GateConfig(
                name        = gateName,
                worldName   = worldName,
                x1          = locSec.getInt("x1"),
                y1          = locSec.getInt("y1"),
                z1          = locSec.getInt("z1"),
                x2          = locSec.getInt("x2"),
                y2          = locSec.getInt("y2"),
                z2          = locSec.getInt("z2"),
                frameBlock  = frameBlock,
                portalBlock = portalBlock,
                destination = destination,
                enabled     = enabled,
                owner       = owner
            )

            gateConfigs.getOrPut(worldName) { mutableListOf() }.add(gate)
        }

        // ── グローバル設定 ───────────────────────────────────────────────
        val settings = plugin.config.getConfigurationSection("settings")
        if (settings != null) {
            notifyAtSeconds = settings.getList("notify-at-seconds")
                ?.filterIsInstance<Number>()
                ?.map { it.toInt() }
                ?: notifyAtSeconds
            timezone = settings.getString("timezone", "Asia/Tokyo")!!
            debug    = settings.getBoolean("debug", false)
        }

        val totalGates = gateConfigs.values.sumOf { it.size }
        plugin.logger.info("${worldConfigs.size} ワールド設定、${totalGates} ゲート設定を読み込みました。")
    }

    private fun parseEnv(s: String?) = when (s?.uppercase()) {
        "NETHER"  -> World.Environment.NETHER
        "THE_END" -> World.Environment.THE_END
        else      -> World.Environment.NORMAL
    }
}
