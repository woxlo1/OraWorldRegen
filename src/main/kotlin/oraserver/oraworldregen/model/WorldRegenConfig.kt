package oraserver.oraworldregen.model

import org.bukkit.World

data class WorldRegenConfig(
    val worldName: String,
    val multiverseWorldName: String,
    val environment: World.Environment,
    val worldType: String,
    val seed: String,
    val generator: String,
    val cronSchedules: List<String>,
    val countdownSeconds: Int,
    val fallbackWorld: String,
    var enabled: Boolean,

    // バックアップ設定
    val backupEnabled: Boolean = false,
    val backupDirectory: String = "backups",
    val backupMaxCount: Int = 5,          // 保持するバックアップ最大数

    // 完了後コマンド
    val postRegenCommands: List<String> = emptyList(),

    // ワールドボーダー設定
    val borderEnabled: Boolean = false,
    val borderSize: Double = 2000.0,       // 直径（例: 1000x1000 -> 1000.0）
    val borderCenterX: Double = 0.0,
    val borderCenterZ: Double = 0.0,
    val borderDamageAmount: Double = 0.2,
    val borderDamageBuffer: Double = 5.0,
    val borderWarningDistance: Int = 5,
    val borderWarningTime: Int = 15,

    // プレイヤー戻し設定
    val returnPlayersAfterRegen: Boolean = true,
    val returnDelay: Long = 60L            // 再生成完了後に戻すまでの秒数
)
