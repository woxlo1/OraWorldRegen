package oraserver.oraworldregen.scheduler

import oraserver.oraworldregen.OraWorldRegen
import oraserver.oraworldregen.model.ScheduleEntry
import oraserver.orapluginapi.scheduler.OraRepeatingTask
import oraserver.orapluginapi.scheduler.OraScheduler
import java.time.ZoneId

class ScheduleManager(private val plugin: OraWorldRegen) {

    /** worldName → スケジュールエントリ一覧 */
    private val schedules   = HashMap<String, List<ScheduleEntry>>()
    private var pollingTask: OraRepeatingTask? = null

    fun loadAll() {
        schedules.clear()

        plugin.configManager.worldConfigs.forEach { (worldName, config) ->
            if (!config.enabled) return@forEach
            if (config.scheduleEntries.isEmpty()) return@forEach

            schedules[worldName] = config.scheduleEntries
            config.scheduleEntries.forEach { entry ->
                plugin.logger.info("スケジュール登録: $worldName >> $entry")
            }
        }

        startPolling()
    }

    fun cancelAll() {
        pollingTask?.cancel()
        pollingTask = null
        schedules.clear()
    }

    private fun startPolling() {
        if (schedules.isEmpty()) {
            plugin.logger.info("有効なスケジュールなし。ポーリングを起動しません。")
            return
        }

        // OraScheduler.repeat で毎分（1200 ticks）チェック
        pollingTask = OraScheduler.repeat(
            period = 1200L,
            delay  = 0L,
            plugin = plugin
        ) {
            checkSchedules()
        }

        plugin.logger.info("スケジュールポーリング開始 (${schedules.size} ワールド)")
    }

    private fun checkSchedules() {
        val zone = try {
            ZoneId.of(plugin.configManager.timezone)
        } catch (e: Exception) {
            ZoneId.of("Asia/Tokyo")
        }

        schedules.forEach { (worldName, entries) ->
            if (plugin.regenManager.isRegenerating(worldName)) return@forEach

            entries.forEach { entry ->
                if (entry.matchesNow(zone)) {
                    plugin.logger.info("スケジュール発火: $worldName ($entry)")
                    plugin.regenManager.startRegen(worldName)
                    return@forEach // 同一ワールドは1分に1度
                }
            }
        }
    }
}