package oraserver.oraworldregen.manager

import oraserver.oraworldregen.OraWorldRegen
import oraserver.oraworldregen.model.RegenHistory
import oraserver.orapluginapi.scheduler.OraScheduler
import org.bukkit.configuration.file.YamlConfiguration
import java.io.File
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * 再生成履歴を history.yml とログファイルに保存・読み込みするマネージャー
 */
class HistoryManager(private val plugin: OraWorldRegen) {

    private val historyFile = File(plugin.dataFolder, "history.yml")
    private val logFile     = File(plugin.dataFolder, "regen.log")
    private val maxHistory  = 200

    private val histories = ArrayDeque<RegenHistory>()

    // -------------------------------------------------------------------------
    // 初期化
    // -------------------------------------------------------------------------

    fun load() {
        histories.clear()
        if (!historyFile.exists()) return

        val yaml = YamlConfiguration.loadConfiguration(historyFile)
        val list = yaml.getMapList("history")
        list.mapNotNull { RegenHistory.fromMap(it) }
            .forEach { histories.addLast(it) }

        plugin.logger.info("再生成履歴 ${histories.size} 件を読み込みました。")
    }

    // -------------------------------------------------------------------------
    // 追加
    // -------------------------------------------------------------------------

    fun add(history: RegenHistory) {
        histories.addLast(history)
        while (histories.size > maxHistory) histories.removeFirst()
        saveAsync(history)
    }

    // -------------------------------------------------------------------------
    // 取得
    // -------------------------------------------------------------------------

    fun getAll(): List<RegenHistory> = histories.toList().reversed()

    fun getByWorld(worldName: String): List<RegenHistory> =
        histories.filter { it.worldName == worldName }.reversed()

    fun getRecent(n: Int = 10): List<RegenHistory> = getAll().take(n)

    // -------------------------------------------------------------------------
    // 保存 — OraScheduler.async を使用
    // -------------------------------------------------------------------------

    private fun saveAsync(latest: RegenHistory) {
        OraScheduler.async(plugin) {
            try {
                val yaml = YamlConfiguration()
                yaml.set("history", histories.map { it.toMap() })
                historyFile.parentFile?.mkdirs()
                yaml.save(historyFile)
            } catch (e: Exception) {
                plugin.logger.warning("履歴YAMLの保存に失敗: ${e.message}")
            }

            try {
                logFile.parentFile?.mkdirs()
                logFile.appendText(latest.toConfigString() + "\n")
            } catch (e: Exception) {
                plugin.logger.warning("ログファイルの書き込みに失敗: ${e.message}")
            }
        }
    }

    // -------------------------------------------------------------------------
    // ヒューマンリーダブル変換
    // -------------------------------------------------------------------------

    fun formatHistory(h: RegenHistory): List<String> {
        val fmt = DateTimeFormatter.ofPattern("MM/dd HH:mm").withZone(ZoneId.of("Asia/Tokyo"))
        val lines = mutableListOf<String>()
        lines += "§e${fmt.format(h.startTime)}  §f${h.worldName}"
        lines += "  §7時間: §f${h.durationSeconds}秒  トリガー: §f${h.triggeredBy}"
        if (h.success) {
            lines += "  §a✔ 成功"
        } else {
            lines += "  §c✘ 失敗: ${h.failReason ?: "不明"}"
        }
        h.backupFile?.let { lines += "  §7バックアップ: §f$it" }
        return lines
    }
}