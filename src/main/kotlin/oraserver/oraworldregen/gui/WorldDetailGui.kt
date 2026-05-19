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
) : OraInventory(oraplugin, "§e§l${worldName} §7- §f詳細", 4) {

    override fun onOpen(player: Player): Boolean {
        val config = oraplugin.configManager.worldConfigs[worldName]
            ?: return false

        fill(OraInventoryItem(Material.GRAY_STAINED_GLASS_PANE)
            .setDisplayName("§r").setCanClick(false))

        val activeTask = oraplugin.regenManager.tasks[worldName]

        // ステータス表示（中央上）
        val statusMat = when (activeTask?.status) {
            null                      -> if (config.enabled) Material.LIME_DYE else Material.GRAY_DYE
            RegenStatus.COUNTDOWN     -> Material.YELLOW_DYE
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

        // 設定情報
        setItem(10, OraInventoryItem(Material.GRASS_BLOCK)
            .setDisplayName("§a§lワールド設定")
            .addLore(
                "§7MV名: §f${config.multiverseWorldName}",
                "§7環境: §f${config.environment.name}",
                "§7タイプ: §f${config.worldType}",
                "§7シード: §f${config.seed.ifBlank { "ランダム" }}",
                "§7ジェネレーター: §f${config.generator.ifBlank { "バニラ" }}"
            )
            .setCanClick(false))

        // スケジュール情報
        setItem(12, OraInventoryItem(Material.CLOCK)
            .setDisplayName("§e§lスケジュール")
            .addLore(buildList {
                if (config.cronSchedules.isEmpty()) {
                    add("§7スケジュールなし")
                } else {
                    config.cronSchedules.forEach { add("§f$it") }
                }
                add("")
                add("§7退避先: §f${config.fallbackWorld}")
                add("§7CD: §f${config.countdownSeconds}秒")
            })
            .setCanClick(false))

        // 手動再生成ボタン
        if (activeTask == null && config.enabled) {
            setItem(14, OraInventoryItem(Material.BEACON)
                .setDisplayName("§a§l今すぐ再生成")
                .addLore("§7クリックで手動再生成を開始します", "§c※ 確認画面が表示されます")
                .setClickEvent {
                    player.closeInventory()
                    ConfirmRegenGui(oraplugin, worldName, player, parent).open(player)
                })
        } else if (activeTask?.status == RegenStatus.COUNTDOWN) {
            setItem(14, OraInventoryItem(Material.RED_CONCRETE)
                .setDisplayName("§c§lカウントダウンをキャンセル")
                .addLore("§7クリックでキャンセルします")
                .setClickEvent {
                    oraplugin.regenManager.cancelRegen(worldName)
                    player.sendMessage("${OraWorldRegen.PREFIX}§e${worldName} §fのカウントダウンをキャンセルしました。")
                    player.closeInventory()
                    parent.open(player)
                })
        } else if (activeTask != null) {
            setItem(14, OraInventoryItem(Material.HOPPER)
                .setDisplayName("§e§l再生成中...")
                .addLore(
                    "§7${activeTask.status.displayName}",
                    "§7経過: §f${activeTask.elapsedSeconds}秒"
                )
                .setCanClick(false))
        }

        // 有効/無効トグル
        val toggleMat = if (config.enabled) Material.LIME_CONCRETE else Material.RED_CONCRETE
        val toggleText = if (config.enabled) "§c§l無効化" else "§a§l有効化"
        setItem(16, OraInventoryItem(toggleMat)
            .setDisplayName(toggleText)
            .addLore("§7クリックでこのワールドの再生成を${if (config.enabled) "無効化" else "有効化"}します")
            .setClickEvent {
                config.enabled = !config.enabled
                player.sendMessage("${OraWorldRegen.PREFIX}§e${worldName} §fを${if (config.enabled) "§a有効化" else "§c無効化"}しました。")
                player.closeInventory()
                open(player)
            })

        // 戻るボタン
        setItem(31, OraInventoryItem(Material.ARROW)
            .setDisplayName("§f§l← 戻る")
            .setClickEvent {
                player.closeInventory()
                parent.open(player)
            })

        return true
    }
}
