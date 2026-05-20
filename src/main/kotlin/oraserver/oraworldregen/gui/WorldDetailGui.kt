package oraserver.oraworldregen.gui

import oraserver.oraworldregen.OraWorldRegen
import oraserver.oraworldregen.model.RegenStatus
import oraserver.orapluginapi.inventory.OraInventory
import oraserver.orapluginapi.inventory.OraInventoryItem
import org.bukkit.Material
import org.bukkit.entity.Player

/**
 * ワールド詳細・操作 GUI
 */
class WorldDetailGui(
    private val oraplugin: OraWorldRegen,
    private val worldName: String,
    private val viewer: Player,
    private val parent: OraInventory
) : OraInventory(oraplugin, "§e§l${worldName} §7- §f詳細", 5) {

    override fun onOpen(player: Player): Boolean {
        val config = oraplugin.configManager.worldConfigs[worldName] ?: return false

        fill(OraInventoryItem(Material.GRAY_STAINED_GLASS_PANE)
            .setDisplayName("§r").setCanClick(false))

        val activeTask = oraplugin.regenManager.tasks[worldName]

        // ── ステータス表示（上中央）────────────────────────────────────
        val statusMat = when (activeTask?.status) {
            null                      -> if (config.enabled) Material.LIME_DYE else Material.GRAY_DYE
            RegenStatus.COUNTDOWN     -> Material.YELLOW_DYE
            RegenStatus.QUEUED        -> Material.CYAN_DYE
            RegenStatus.COMPLETE      -> Material.GREEN_DYE
            RegenStatus.FAILED        -> Material.RED_DYE
            else                      -> Material.ORANGE_DYE
        }
        setItem(4, OraInventoryItem(statusMat)
            .setDisplayName("§f§l現在のステータス")
            .addLore(
                "§7状態: ${if (activeTask != null) "§e${activeTask.status.displayName}" else if (config.enabled) "§a待機中" else "§8無効"}",
                activeTask?.let { "§7経過時間: §f${it.elapsedSeconds}秒" } ?: "",
                activeTask?.failReason?.let { "§c失敗理由: $it" } ?: ""
            )
            .setCanClick(false))

        // ── 基本設定情報 ─────────────────────────────────────────────
        setItem(10, OraInventoryItem(Material.GRASS_BLOCK)
            .setDisplayName("§a§lワールド設定")
            .addLore(
                "§7MV名: §f${config.multiverseWorldName}",
                "§7環境: §f${config.environment.name}",
                "§7タイプ: §f${config.worldType}",
                "§7シード: §f${config.seed.ifBlank { "ランダム" }}",
                "§7ジェネレーター: §f${config.generator.ifBlank { "バニラ" }}",
                "§7カウントダウン: §f${config.countdownSeconds}秒",
                "§7退避先: §f${config.fallbackWorld}"
            )
            .setCanClick(false))

        // ── スケジュール情報 ──────────────────────────────────────────
        setItem(12, OraInventoryItem(Material.CLOCK)
            .setDisplayName("§e§lスケジュール")
            .addLore(buildList {
                if (config.cronSchedules.isEmpty()) {
                    add("§7スケジュールなし")
                } else {
                    config.cronSchedules.forEach { add("§f$it") }
                }
            })
            .setCanClick(false))

        // ── バックアップ設定 ──────────────────────────────────────────
        setItem(14, OraInventoryItem(if (config.backupEnabled) Material.CHEST else Material.BARREL)
            .setDisplayName("§6§lバックアップ")
            .addLore(buildList {
                add("§7状態: ${if (config.backupEnabled) "§a有効" else "§c無効"}")
                if (config.backupEnabled) {
                    add("§7保存先: §f${config.backupDirectory}")
                    add("§7最大保持数: §f${config.backupMaxCount}件")
                    // 最新バックアップ履歴
                    val lastBackup = oraplugin.historyManager
                        .getByWorld(worldName)
                        .firstOrNull { it.backupFile != null }
                    if (lastBackup != null) {
                        add("§7最新BAK: §f${lastBackup.backupFile}")
                    }
                }
            })
            .setCanClick(false))

        // ── ワールドボーダー設定 ──────────────────────────────────────
        setItem(16, OraInventoryItem(if (config.borderEnabled) Material.BARRIER else Material.LIGHT_GRAY_STAINED_GLASS_PANE)
            .setDisplayName("§b§lワールドボーダー")
            .addLore(buildList {
                add("§7状態: ${if (config.borderEnabled) "§a有効" else "§c無効"}")
                if (config.borderEnabled) {
                    add("§7サイズ: §f${config.borderSize.toInt()} × ${config.borderSize.toInt()}")
                    add("§7中心: §fX=${config.borderCenterX.toInt()}, Z=${config.borderCenterZ.toInt()}")
                    add("§7ダメージ: §f${config.borderDamageAmount}/s  バッファ:${config.borderDamageBuffer}m")
                    add("§7警告距離: §f${config.borderWarningDistance}m  時間:${config.borderWarningTime}s")
                }
            })
            .setCanClick(false))

        // ── プレイヤー戻し設定 ────────────────────────────────────────
        setItem(20, OraInventoryItem(if (config.returnPlayersAfterRegen) Material.ENDER_PEARL else Material.ENDER_EYE)
            .setDisplayName("§d§lプレイヤー帰還")
            .addLore(buildList {
                add("§7状態: ${if (config.returnPlayersAfterRegen) "§a有効" else "§c無効"}")
                if (config.returnPlayersAfterRegen) {
                    add("§7帰還ディレイ: §f${config.returnDelay}秒後")
                    add("§7再生成されたワールドから退避した")
                    add("§7プレイヤーを完了後に元の場所へ戻します。")
                }
            })
            .setCanClick(false))

        // ── 完了後コマンド ────────────────────────────────────────────
        val postCmdItem = OraInventoryItem(Material.COMMAND_BLOCK)
            .setDisplayName("§c§l完了後コマンド")
        if (config.postRegenCommands.isEmpty()) {
            postCmdItem.addLore("§7設定なし")
        } else {
            config.postRegenCommands.forEachIndexed { i, cmd ->
                postCmdItem.addLore("§7${i + 1}. §f$cmd")
            }
        }
        setItem(22, postCmdItem.setCanClick(false))

        // ── 最近の履歴 ────────────────────────────────────────────────
        val recentHistory = oraplugin.historyManager.getByWorld(worldName).take(3)
        if (recentHistory.isNotEmpty()) {
            val histItem = OraInventoryItem(Material.BOOK)
                .setDisplayName("§f§l最近の再生成履歴")
            recentHistory.forEach { h ->
                oraplugin.historyManager.formatHistory(h).forEach { histItem.addLore(it) }
                histItem.addLore("§8──────────────────")
            }
            setItem(24, histItem.setCanClick(false))
        }

        // ── 操作ボタン ────────────────────────────────────────────────
        if (activeTask == null && config.enabled) {
            setItem(30, OraInventoryItem(Material.BEACON)
                .setDisplayName("§a§l今すぐ再生成")
                .addLore("§7クリックで手動再生成を開始します", "§c※ 確認画面が表示されます")
                .setClickEvent {
                    player.closeInventory()
                    ConfirmRegenGui(oraplugin, worldName, player, parent).open(player)
                })
        } else if (activeTask?.status == RegenStatus.COUNTDOWN) {
            setItem(30, OraInventoryItem(Material.RED_CONCRETE)
                .setDisplayName("§c§lカウントダウンをキャンセル")
                .addLore("§7クリックでキャンセルします")
                .setClickEvent {
                    oraplugin.regenManager.cancelRegen(worldName)
                    player.sendMessage("${OraWorldRegen.PREFIX}§e${worldName} §fのカウントダウンをキャンセルしました。")
                    player.closeInventory()
                    parent.open(player)
                })
        } else if (activeTask?.status == RegenStatus.QUEUED) {
            setItem(30, OraInventoryItem(Material.YELLOW_CONCRETE)
                .setDisplayName("§e§lキュー待機中")
                .addLore("§7クリックでキューから削除します")
                .setClickEvent {
                    oraplugin.regenManager.cancelRegen(worldName)
                    player.sendMessage("${OraWorldRegen.PREFIX}§e${worldName} §fをキューから削除しました。")
                    player.closeInventory()
                    parent.open(player)
                })
        } else if (activeTask != null) {
            setItem(30, OraInventoryItem(Material.HOPPER)
                .setDisplayName("§e§l再生成中...")
                .addLore(
                    "§7${activeTask.status.displayName}",
                    "§7経過: §f${activeTask.elapsedSeconds}秒"
                )
                .setCanClick(false))
        }

        // 有効/無効トグル
        val toggleMat  = if (config.enabled) Material.LIME_CONCRETE else Material.RED_CONCRETE
        val toggleText = if (config.enabled) "§c§l無効化" else "§a§l有効化"
        setItem(32, OraInventoryItem(toggleMat)
            .setDisplayName(toggleText)
            .addLore("§7クリックでこのワールドの再生成を${if (config.enabled) "無効化" else "有効化"}します")
            .setClickEvent {
                config.enabled = !config.enabled
                player.sendMessage("${OraWorldRegen.PREFIX}§e${worldName} §fを${if (config.enabled) "§a有効化" else "§c無効化"}しました。")
                player.closeInventory()
                open(player)
            })

        // 戻るボタン
        setItem(40, OraInventoryItem(Material.ARROW)
            .setDisplayName("§f§l← 戻る")
            .setClickEvent {
                player.closeInventory()
                parent.open(player)
            })

        return true
    }
}
