package oraserver.oraworldregen.scheduler

import oraserver.oraworldregen.OraWorldRegen
import oraserver.oraworldregen.manager.CronParser
import org.bukkit.scheduler.BukkitTask
import java.time.ZoneId

class ScheduleManager(private val plugin: OraWorldRegen) {

    // worldName -> List<CronParser>
    private val parsedCrons = HashMap<String, List<CronParser>>()
    private var pollingTask: BukkitTask? = null

    fun loadAll() {
        parsedCrons.clear()

        plugin.configManager.worldConfigs.forEach { (worldName, config) ->
            if (!config.enabled) return@forEach

            val parsers = config.cronSchedules.mapNotNull { cron ->
                try {
                    CronParser(cron).also {
                        plugin.logger.info("スケジュール登録: $worldName >> $cron")
                    }
                } catch (e: Exception) {
                    plugin.logger.warning("不正な cron をスキップ [$worldName]: $cron / ${e.message}")
                    null
                }
            }
            if (parsers.isNotEmpty()) parsedCrons[worldName] = parsers
        }

        startPolling()
    }

    fun cancelAll() {
        pollingTask?.cancel()
        pollingTask = null
        parsedCrons.clear()
    }

    private fun startPolling() {
        if (parsedCrons.isEmpty()) {
            plugin.logger.info("有効なスケジュールなし。ポーリングを起動しません。")
            return
        }

        pollingTask = plugin.server.scheduler.runTaskTimer(plugin, Runnable {
            checkSchedules()
        }, 0L, 1200L) // 毎分

        plugin.logger.info("スケジュールポーリング開始 (${parsedCrons.size} ワールド)")
    }

    private fun checkSchedules() {
        val zone = try {
            ZoneId.of(plugin.configManager.timezone)
        } catch (e: Exception) {
            ZoneId.of("Asia/Tokyo")
        }

        parsedCrons.forEach { (worldName, crons) ->
            if (plugin.regenManager.isRegenerating(worldName)) return@forEach

            crons.forEach { cron ->
                if (cron.matchesNow(zone)) {
                    plugin.logger.info("スケジュール発火: $worldName ($cron)")
                    plugin.regenManager.startRegen(worldName)
                    return@forEach // 同一ワールドは1分に1度
                }
            }
        }
    }
}
