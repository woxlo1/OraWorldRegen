package oraserver.oraworldregen

import oraserver.oraworldregen.command.OraWorldRegenCommand
import oraserver.oraworldregen.config.ConfigManager
import oraserver.oraworldregen.manager.BackupManager
import oraserver.oraworldregen.manager.HistoryManager
import oraserver.oraworldregen.manager.MultiverseHook
import oraserver.oraworldregen.manager.RegenManager
import oraserver.oraworldregen.manager.WhitelistManager
import oraserver.oraworldregen.scheduler.ScheduleManager
import oraserver.orapluginapi.OraPlugin

/**
 * OraWorldRegen - メインクラス
 * Paper/Spigot + Multiverse-Core 向けワールド自動再生成プラグイン
 */
class OraWorldRegen : OraPlugin() {

    companion object {
        const val PREFIX = "§e§l[§6§lOraWorldRegen§e§l] §r"

        lateinit var instance: OraWorldRegen
            private set
    }

    lateinit var configManager: ConfigManager
        private set
    lateinit var multiverseHook: MultiverseHook
        private set
    lateinit var regenManager: RegenManager
        private set
    lateinit var whitelistManager: WhitelistManager
        private set
    lateinit var scheduleManager: ScheduleManager
        private set
    lateinit var historyManager: HistoryManager
        private set
    lateinit var backupManager: BackupManager
        private set

    override fun requiredPlugins() = listOf("Multiverse-Core")

    override fun onStart() {
        instance = this
        saveDefaultConfig()

        // マネージャー初期化
        configManager    = ConfigManager(this)
        multiverseHook   = MultiverseHook(this)
        regenManager     = RegenManager(this)
        whitelistManager = WhitelistManager(this)
        scheduleManager  = ScheduleManager(this)
        historyManager   = HistoryManager(this)
        backupManager    = BackupManager(this)

        configManager.load()
        historyManager.load()

        // Multiverse 確認
        if (!multiverseHook.isAvailable) {
            logger.severe("Multiverse-Core が見つかりません！プラグインを無効化します。")
            server.pluginManager.disablePlugin(this)
            return
        }
        logger.info("Multiverse-Core との連携に成功しました。")

        // スケジュール起動
        scheduleManager.loadAll()

        // コマンド登録
        OraWorldRegenCommand(this).register()

        logger.info("OraWorldRegen v${pluginMeta.version} が有効化されました！")
    }

    override fun onEnd() {
        scheduleManager.cancelAll()
        regenManager.shutdown()
        whitelistManager.shutdown()
        logger.info("OraWorldRegen が無効化されました。")
    }

    override fun onReload() {
        configManager.load()
        scheduleManager.cancelAll()
        scheduleManager.loadAll()
    }

    fun reloadPlugin() {
        reload()
    }
}
