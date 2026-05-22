package oraserver.oraworldregen.gui

import oraserver.oraworldregen.OraWorldRegen
import oraserver.oraworldregen.model.RegenStatus
import oraserver.orapluginapi.inventory.OraInventory
import oraserver.orapluginapi.inventory.OraInventoryItem
import org.bukkit.Material
import org.bukkit.entity.Player

/**
 * メインメニュー GUI
 */
class MainMenuGui(private val oraplugin: OraWorldRegen) :
    OraInventory(oraplugin, "§e§lOraWorldRegen §7- §fメインメニュー", 6) {

    override fun onOpen(player: Player): Boolean {
        fill(OraInventoryItem(Material.GRAY_STAINED_GLASS_PANE)
            .setDisplayName("§r")
            .setCanClick(false))

        val worlds = oraplugin.configManager.worldConfigs
        if (worlds.isEmpty()) {
            setItem(22, OraInventoryItem(Material.BARRIER)
                .setDisplayName("§c登録ワールドなし")
                .addLore("§7config.yml にワールドを追加してください")
                .setCanClick(false))
            setCloseButton(player)
            return true
        }

        var slot = 10
        worlds.entries.forEachIndexed { index, (worldName, config) ->
            if (index >= 28) return@forEachIndexed

            val activeTask = oraplugin.regenManager.tasks[worldName]
            val statusColor = when {
                !config.enabled                              -> "§8"
                activeTask?.status == RegenStatus.QUEUED     -> "§b"
                activeTask != null                           -> "§e"
                else                                         -> "§a"
            }
            val statusText = when {
                !config.enabled                              -> "§8無効"
                activeTask?.status == RegenStatus.QUEUED     -> "§bキュー待機中"
                activeTask != null                           -> "§e${activeTask.status.displayName}"
                else                                         -> "§a待機中"
            }

            val material = when {
                !config.enabled                              -> Material.RED_WOOL
                activeTask?.status == RegenStatus.QUEUED     -> Material.CYAN_WOOL
                activeTask?.status == RegenStatus.COUNTDOWN  -> Material.YELLOW_WOOL
                activeTask != null                           -> Material.ORANGE_WOOL
                else                                         -> Material.GREEN_WOOL
            }

            val lore = buildList {
                add("§7ステータス: $statusText")
                add("§7環境: §f${config.environment.name}")
                add("§7退避先: §f${config.fallbackWorld}")
                add("§7カウントダウン: §f${config.countdownSeconds}秒")
                if (config.backupEnabled)  add("§7バックアップ: §a有効")
                if (config.borderEnabled)  add("§7ボーダー: §a${config.borderSize.toInt()}×${config.borderSize.toInt()}")
                if (config.returnPlayersAfterRegen) add("§7帰還: §a有効 (${config.returnDelay}秒後)")
                // scheduleDescriptions を使用（cron/human-readable 両対応）
                if (config.scheduleDescriptions.isNotEmpty()) {
                    add("§7スケジュール: §f${config.scheduleDescriptions.joinToString(", ")}")
                }
                activeTask?.let {
                    add("§7経過時間: §f${it.elapsedSeconds}秒")
                    it.failReason?.let { r -> add("§c失敗理由: $r") }
                }
                add("")
                when {
                    !config.enabled -> add("§8クリックで詳細を表示")
                    activeTask?.status == RegenStatus.QUEUED -> {
                        add("§e左クリック: §f詳細を表示")
                        add("§c右クリック: §fキューから削除")
                    }
                    activeTask?.status == RegenStatus.COUNTDOWN -> {
                        add("§e左クリック: §f詳細を表示")
                        add("§c右クリック: §fカウントダウンをキャンセル")
                    }
                    activeTask == null -> {
                        add("§e左クリック: §f詳細を表示")
                        add("§a右クリック: §f今すぐ再生成")
                    }
                    else -> add("§7再生成中は操作できません")
                }
            }

            val item = OraInventoryItem(material)
                .setDisplayName("$statusColor§l$worldName")
                .setLore(lore)
                .setClickEvent { e ->
                    if (e.isRightClick) {
                        val task = oraplugin.regenManager.tasks[worldName]
                        when {
                            !config.enabled -> {
                                player.sendMessage("${OraWorldRegen.PREFIX}§c${worldName} は無効化されています。")
                            }
                            task?.status == RegenStatus.QUEUED -> {
                                oraplugin.regenManager.cancelRegen(worldName)
                                player.sendMessage("${OraWorldRegen.PREFIX}§e${worldName} §fをキューから削除しました。")
                                player.closeInventory()
                                open(player)
                            }
                            task?.status == RegenStatus.COUNTDOWN -> {
                                val cancelled = oraplugin.regenManager.cancelRegen(worldName)
                                if (cancelled) {
                                    player.sendMessage("${OraWorldRegen.PREFIX}§e${worldName} §fのカウントダウンをキャンセルしました。")
                                }
                                player.closeInventory()
                                open(player)
                            }
                            task == null -> {
                                player.closeInventory()
                                ConfirmRegenGui(oraplugin, worldName, player, this).open(player)
                            }
                        }
                    } else {
                        player.closeInventory()
                        WorldDetailGui(oraplugin, worldName, player, this).open(player)
                    }
                }

            setItem(slot, item)

            slot++
            if ((slot % 9) == 8) slot += 2
        }

        setBottomBar(player)
        return true
    }

    private fun setBottomBar(player: Player) {
        val barBg = OraInventoryItem(Material.BLACK_STAINED_GLASS_PANE)
            .setDisplayName("§r").setCanClick(false)
        (45..53).forEach { setItem(it, barBg) }

        val queue = oraplugin.regenManager.getQueue()
        if (queue.isNotEmpty()) {
            setItem(46, OraInventoryItem(Material.HOPPER)
                .setDisplayName("§e§lキュー: ${queue.size}件")
                .addLore(queue.mapIndexed { i, w -> "§7${i + 1}. §e$w" })
                .setCanClick(false))
        }

        setItem(48, OraInventoryItem(Material.COMPARATOR)
            .setDisplayName("§e§l設定リロード")
            .addLore("§7config.yml を再読み込みします")
            .setClickEvent {
                oraplugin.reloadPlugin()
                player.sendMessage("${OraWorldRegen.PREFIX}§a設定を再読み込みしました。")
                player.closeInventory()
                open(player)
            })

        setItem(49, OraInventoryItem(Material.BOOK)
            .setDisplayName("§b§lOraWorldRegen §7v${oraplugin.pluginMeta.version}")
            .addLore(
                "§7ワールド数: §f${oraplugin.configManager.worldConfigs.size}",
                "§7再生成中: §f${if (oraplugin.regenManager.isAnyRegenerating()) "§c${oraplugin.regenManager.tasks.size}件" else "§aなし"}",
                "§7キュー: §f${if (queue.isNotEmpty()) "§e${queue.size}件" else "§aなし"}",
                "§7WL状態: §f${if (oraplugin.whitelistManager.isBlocking) "§c有効（再生成中）" else "§a無効"}"
            )
            .setCanClick(false))

        setCloseButton(player, 50)
    }

    private fun setCloseButton(player: Player, slot: Int = 49) {
        setItem(slot, OraInventoryItem(Material.BARRIER)
            .setDisplayName("§c§l閉じる")
            .setClickEvent { player.closeInventory() })
    }
}