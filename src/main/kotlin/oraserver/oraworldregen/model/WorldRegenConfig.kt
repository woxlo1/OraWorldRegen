package oraserver.oraworldregen.model

import org.bukkit.World

data class WorldRegenConfig(
    val worldName: String,
    val multiverseWorldName: String,
    val environment: World.Environment,
    val worldType: String,
    val seed: String,
    val generator: String,

    /** スケジュール一覧（cron形式・human-readable形式の混在可） */
    val scheduleEntries: List<ScheduleEntry> = emptyList(),

    val countdownSeconds: Int,
    val fallbackWorld: String,
    var enabled: Boolean,

    // バックアップ設定
    val backupEnabled: Boolean = false,
    val backupDirectory: String = "backups",
    val backupMaxCount: Int = 5,

    // 完了後コマンド
    val postRegenCommands: List<String> = emptyList(),

    // ワールドボーダー設定
    val borderEnabled: Boolean = false,
    val borderSize: Double = 2000.0,
    val borderCenterX: Double = 0.0,
    val borderCenterZ: Double = 0.0,
    val borderDamageAmount: Double = 0.2,
    val borderDamageBuffer: Double = 5.0,
    val borderWarningDistance: Int = 5,
    val borderWarningTime: Int = 15,

    // プレイヤー戻し設定
    val returnPlayersAfterRegen: Boolean = true,
    val returnDelay: Long = 60L
) {
    /**
     * スケジュールの文字列表現一覧（GUI・コマンド表示用）
     */
    val scheduleDescriptions: List<String>
        get() = scheduleEntries.map { it.toString() }
}