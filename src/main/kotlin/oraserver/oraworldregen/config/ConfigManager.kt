package oraserver.oraworldregen.config

import oraserver.oraworldregen.OraWorldRegen
import oraserver.oraworldregen.manager.CronParser
import oraserver.oraworldregen.model.GateConfig
import oraserver.oraworldregen.model.GateFacing
import oraserver.oraworldregen.model.ScheduleEntry
import oraserver.oraworldregen.model.WorldRegenConfig
import org.bukkit.World
import java.time.DayOfWeek

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

            val scheduleEntries = ws.getMapList("schedules").mapNotNull { map ->
                parseScheduleEntry(map, key)
            }

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
                scheduleEntries     = scheduleEntries,
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

            val worldName = gs.getString("world", "").orEmpty()
            if (worldName.isEmpty()) {
                plugin.logger.warning("[Gate] ゲート '$gateName': world が未設定です。スキップします。")
                return@forEach
            }

            val facing     = GateFacing.parse(gs.getString("facing", "south"))
            val frameBlock = gs.getString("frame-block",  "OBSIDIAN").orEmpty().ifBlank { "OBSIDIAN" }
            val portalBlock= gs.getString("portal-block", "WATER").orEmpty().ifBlank { "WATER" }
            val destination= gs.getString("destination",  "w:world").orEmpty().ifBlank { "w:world" }
            val enabled    = gs.getBoolean("enabled", true)
            val owner      = gs.getString("owner", "OraWorldRegen").orEmpty().ifBlank { "OraWorldRegen" }

            // size セクション
            val sizeSec = gs.getConfigurationSection("size")
            val width   = sizeSec?.getInt("width",  2) ?: 2
            val height  = sizeSec?.getInt("height", 3) ?: 3

            // offset セクション（省略可、デフォルト 0）
            val offsetSec = gs.getConfigurationSection("offset")
            val offsetX   = offsetSec?.getInt("x", 0) ?: 0
            val offsetY   = offsetSec?.getInt("y", 0) ?: 0
            val offsetZ   = offsetSec?.getInt("z", 0) ?: 0

            val gate = GateConfig(
                name        = gateName,
                worldName   = worldName,
                facing      = facing,
                width       = width,
                height      = height,
                offsetX     = offsetX,
                offsetY     = offsetY,
                offsetZ     = offsetZ,
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

    // =========================================================================
    // スケジュールエントリのパース
    // =========================================================================

    private fun parseScheduleEntry(map: Map<*, *>, worldKey: String): ScheduleEntry? {
        val cronStr = map["cron"]?.toString()
        if (cronStr != null) {
            return try {
                ScheduleEntry.Cron(CronParser(cronStr))
            } catch (e: Exception) {
                plugin.logger.warning("[Schedule] 不正な cron をスキップ [$worldKey]: $cronStr / ${e.message}")
                null
            }
        }

        val timeStr = map["time"]?.toString()
        if (timeStr != null) {
            return try {
                parseHumanEntry(timeStr, map, worldKey)
            } catch (e: Exception) {
                plugin.logger.warning("[Schedule] スケジュール設定をスキップ [$worldKey]: ${e.message}")
                null
            }
        }

        plugin.logger.warning("[Schedule] 不明なスケジュール形式をスキップ [$worldKey]: $map")
        return null
    }

    private fun parseHumanEntry(
        timeStr: String,
        map: Map<*, *>,
        worldKey: String
    ): ScheduleEntry.Human {
        val parts = timeStr.split(":")
        require(parts.size == 2) { "time は HH:mm 形式で指定してください: \"$timeStr\"" }
        val hour   = parts[0].trim().toInt()
        val minute = parts[1].trim().toInt()

        val dayOfWeek: DayOfWeek? = map["dayofweek"]?.toString()?.uppercase()?.let { dow ->
            try {
                DayOfWeek.valueOf(dow)
            } catch (e: IllegalArgumentException) {
                plugin.logger.warning(
                    "[Schedule] 不明な dayofweek をスキップ [$worldKey]: \"$dow\" " +
                    "(有効値: MONDAY, TUESDAY, WEDNESDAY, THURSDAY, FRIDAY, SATURDAY, SUNDAY)"
                )
                null
            }
        }

        val dayOfMonth: Int? = (map["day"] as? Number)?.toInt()

        return ScheduleEntry.Human(
            hour       = hour,
            minute     = minute,
            dayOfWeek  = dayOfWeek,
            dayOfMonth = dayOfMonth
        )
    }

    private fun parseEnv(s: String?) = when (s?.uppercase()) {
        "NETHER"  -> World.Environment.NETHER
        "THE_END" -> World.Environment.THE_END
        else      -> World.Environment.NORMAL
    }
}
