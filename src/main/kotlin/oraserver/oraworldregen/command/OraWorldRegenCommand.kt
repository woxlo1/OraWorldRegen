package oraserver.oraworldregen.command

import oraserver.oraworldregen.OraWorldRegen
import oraserver.oraworldregen.gui.MainMenuGui
import oraserver.oraworldregen.model.RegenStatus
import oraserver.orapluginapi.command.OraCommandObject
import oraserver.orapluginapi.commandapi.ToolTip
import oraserver.orapluginapi.commandapi.argumenttype.IntArg
import oraserver.orapluginapi.commandapi.argumenttype.StringArg
import org.bukkit.entity.Player

class OraWorldRegenCommand(private val plugin: OraWorldRegen) {

    private val commandObject = OraCommandObject {

        // ─── ルートリテラル: /owr ───────────────────────────────────────
        literal("owr") {
            setPermission("oraworldregen.admin")

            setPlayerExecutor { data ->
                MainMenuGui(plugin).open(data.sender)
            }

            // ── /owr gui ──────────────────────────────────────────────
            literal("gui") {
                setPermission("oraworldregen.admin")
                setPlayerExecutor { data ->
                    MainMenuGui(plugin).open(data.sender)
                }
            }

            // ── /owr start <world> ────────────────────────────────────
            literal("start") {
                setPermission("oraworldregen.admin")

                argument("world", StringArg.word()) {
                    suggest({ _, _, _ ->
                        plugin.configManager.worldConfigs.keys.map { ToolTip(it) }
                    })

                    setExecutor { data ->
                        val worldName  = data.getArgument("world", String::class.java)
                        val config     = plugin.configManager.worldConfigs[worldName]
                        val senderName = data.sender.name

                        if (config == null) {
                            data.sender.sendMessage(
                                "${OraWorldRegen.PREFIX}§cワールド §e${worldName} §cは登録されていません。"
                            )
                            return@setExecutor
                        }
                        if (plugin.regenManager.isRegenerating(worldName)) {
                            val task = plugin.regenManager.tasks[worldName]
                            if (task?.status == RegenStatus.QUEUED) {
                                data.sender.sendMessage(
                                    "${OraWorldRegen.PREFIX}§e${worldName} §fは既にキューに入っています。"
                                )
                            } else {
                                data.sender.sendMessage(
                                    "${OraWorldRegen.PREFIX}§e${worldName} §fは現在再生成中です。"
                                )
                            }
                            return@setExecutor
                        }

                        val sender = data.sender
                        if (sender is Player) {
                            MainMenuGui(plugin).open(sender)
                            sender.sendMessage("${OraWorldRegen.PREFIX}§fGUIから再生成を開始してください。")
                        } else {
                            plugin.regenManager.startRegen(worldName, "manual:console")
                            sender.sendMessage(
                                "${OraWorldRegen.PREFIX}§e${worldName} §fの再生成を開始しました。"
                            )
                        }
                    }
                }
            }

            // ── /owr cancel <world> ───────────────────────────────────
            literal("cancel") {
                setPermission("oraworldregen.admin")

                argument("world", StringArg.word()) {
                    suggest({ _, _, _ ->
                        plugin.regenManager.tasks.keys.map { ToolTip(it) }
                    })

                    setExecutor { data ->
                        val worldName = data.getArgument("world", String::class.java)
                        val cancelled = plugin.regenManager.cancelRegen(worldName)

                        if (cancelled) {
                            data.sender.sendMessage(
                                "${OraWorldRegen.PREFIX}§e${worldName} §fのカウントダウン/キューをキャンセルしました。"
                            )
                        } else {
                            val task = plugin.regenManager.tasks[worldName]
                            if (task == null) {
                                data.sender.sendMessage(
                                    "${OraWorldRegen.PREFIX}§e${worldName} §fは現在再生成中ではありません。"
                                )
                            } else {
                                data.sender.sendMessage(
                                    "${OraWorldRegen.PREFIX}§cカウントダウン以外のフェーズはキャンセルできません。§7(${task.status.displayName})"
                                )
                            }
                        }
                    }
                }
            }

            // ── /owr queue ────────────────────────────────────────────
            literal("queue") {
                setPermission("oraworldregen.admin")

                setExecutor { data ->
                    val sender = data.sender
                    val queue  = plugin.regenManager.getQueue()
                    sender.sendMessage("§8§m━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
                    sender.sendMessage("      §e§lOra§6§lWorld§e§lRegen §7| §f再生成キュー")
                    sender.sendMessage("§8§m━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")

                    val active = plugin.regenManager.tasks
                        .filter { it.value.status != RegenStatus.QUEUED }
                    if (active.isNotEmpty()) {
                        sender.sendMessage(" §a▶ 実行中:")
                        active.forEach { (name, task) ->
                            sender.sendMessage("   §e${name} §7│ §f${task.status.displayName} (${task.elapsedSeconds}秒)")
                        }
                    } else {
                        sender.sendMessage(" §7実行中: なし")
                    }

                    if (queue.isEmpty()) {
                        sender.sendMessage(" §7キュー: なし")
                    } else {
                        sender.sendMessage(" §e⏳ キュー (${queue.size}件):")
                        queue.forEachIndexed { i, name ->
                            sender.sendMessage("   §7${i + 1}. §e${name}")
                        }
                    }
                    sender.sendMessage("§8§m━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
                }
            }

            // ── /owr status ───────────────────────────────────────────
            literal("status") {
                setPermission("oraworldregen.admin")

                setExecutor { data ->
                    val sender = data.sender
                    sender.sendMessage("§8§m━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
                    sender.sendMessage("      §e§lOra§6§lWorld§e§lRegen §7| §fステータス")
                    sender.sendMessage("§8§m━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")

                    val configs = plugin.configManager.worldConfigs
                    if (configs.isEmpty()) {
                        sender.sendMessage(" §7登録ワールドなし")
                    } else {
                        configs.forEach { (worldName, config) ->
                            val task = plugin.regenManager.tasks[worldName]
                            val statusText = when {
                                !config.enabled -> "§8無効"
                                task != null    -> "§e${task.status.displayName}"
                                else            -> "§a待機中"
                            }
                            sender.sendMessage(" §7◆ §e${worldName} §8│ §f$statusText")
                            if (task != null) {
                                sender.sendMessage("   §8└ §7経過: §f${task.elapsedSeconds}秒")
                                task.failReason?.let {
                                    sender.sendMessage("   §8└ §c失敗: $it")
                                }
                            } else if (config.scheduleDescriptions.isNotEmpty()) {
                                sender.sendMessage(
                                    "   §8└ §7スケジュール: §f${config.scheduleDescriptions.joinToString(", ")}"
                                )
                            }
                        }
                    }

                    val wlStatus = if (plugin.whitelistManager.isBlocking) "§c有効（再生成中）" else "§a無効"
                    sender.sendMessage(" §7ホワイトリスト: $wlStatus")
                    val q = plugin.regenManager.getQueue()
                    if (q.isNotEmpty()) {
                        sender.sendMessage(" §7キュー: §e${q.size}件 §7(${q.joinToString(", ")})")
                    }
                    sender.sendMessage("§8§m━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
                }
            }

            // ── /owr history [world] [page] ───────────────────────────
            literal("history") {
                setPermission("oraworldregen.admin")

                setExecutor { data ->
                    sendHistory(data.sender, null, 1)
                }

                argument("world", StringArg.word()) {
                    suggest({ _, _, _ ->
                        plugin.configManager.worldConfigs.keys.map { ToolTip(it) }
                    })

                    setExecutor { data ->
                        val worldName = data.getArgument("world", String::class.java)
                        sendHistory(data.sender, worldName, 1)
                    }

                    argument("page", IntArg(1, 100)) {
                        setExecutor { data ->
                            val worldName = data.getArgument("world", String::class.java)
                            val page      = data.getArgument("page", Int::class.java)
                            sendHistory(data.sender, worldName, page)
                        }
                    }
                }
            }

            // ── /owr list ─────────────────────────────────────────────
            literal("list") {
                setPermission("oraworldregen.admin")

                setExecutor { data ->
                    val sender = data.sender
                    sender.sendMessage("§8§m━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
                    sender.sendMessage("      §e§lOra§6§lWorld§e§lRegen §7| §f登録ワールド一覧")
                    sender.sendMessage("§8§m━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")

                    val configs = plugin.configManager.worldConfigs
                    if (configs.isEmpty()) {
                        sender.sendMessage(" §7登録されているワールドはありません。")
                    } else {
                        configs.forEach { (worldName, config) ->
                            val schedStr = config.scheduleDescriptions.joinToString(", ").ifEmpty { "(スケジュールなし)" }
                            if (config.enabled) {
                                sender.sendMessage(" §a✔ §e${worldName} §8│ §7${schedStr}")
                            } else {
                                sender.sendMessage(" §c✘ §7${worldName} §8│ §7${schedStr} §c(無効)")
                            }
                            sender.sendMessage(
                                "   §8└ §7環境:§f${config.environment.name}" +
                                        "  退避:§f${config.fallbackWorld}" +
                                        "  CD:§f${config.countdownSeconds}秒" +
                                        (if (config.backupEnabled) "  §aBAK" else "") +
                                        (if (config.borderEnabled) "  §eBOR:${config.borderSize.toInt()}" else "") +
                                        (if (config.returnPlayersAfterRegen) "  §bRET" else "")
                            )
                        }
                    }
                    sender.sendMessage("§8§m━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
                }
            }

            // ── /owr reload ───────────────────────────────────────────
            literal("reload") {
                setPermission("oraworldregen.admin")

                setExecutor { data ->
                    try {
                        plugin.reloadPlugin()
                        data.sender.sendMessage("${OraWorldRegen.PREFIX}§a設定を再読み込みしました。")
                    } catch (e: Exception) {
                        data.sender.sendMessage(
                            "${OraWorldRegen.PREFIX}§c再読み込みに失敗しました: §7${e.message}"
                        )
                    }
                }
            }

            // ── /owr help ─────────────────────────────────────────────
            literal("help") {
                setPermission("oraworldregen.admin")

                setExecutor { data ->
                    val s = data.sender
                    s.sendMessage("§e§lOra§6§lWorld§e§lRegen §7| §7v${plugin.pluginMeta.version}")
                    s.sendMessage("§6/owr               §8» §7GUI を開く")
                    s.sendMessage("§6/owr gui           §8» §7メインメニューを開く")
                    s.sendMessage("§6/owr start §e<W>    §8» §7ワールドの再生成を開始")
                    s.sendMessage("§6/owr cancel §e<W>   §8» §7カウントダウン/キューをキャンセル")
                    s.sendMessage("§6/owr queue         §8» §7再生成キューを表示")
                    s.sendMessage("§6/owr status        §8» §7全ワールドの状態を表示")
                    s.sendMessage("§6/owr list          §8» §7登録ワールド一覧")
                    s.sendMessage("§6/owr history §e[W] [P] §8» §7再生成履歴を表示")
                    s.sendMessage("§6/owr reload        §8» §7設定を再読み込み")
                    s.sendMessage("§6/owr help          §8» §7このヘルプを表示")
                }
            }
        }
    }

    // -------------------------------------------------------------------------
    // 履歴表示ヘルパー
    // -------------------------------------------------------------------------

    private fun sendHistory(
        sender: org.bukkit.command.CommandSender,
        worldName: String?,
        page: Int
    ) {
        val pageSize = 5
        val histories = if (worldName != null) {
            plugin.historyManager.getByWorld(worldName)
        } else {
            plugin.historyManager.getAll()
        }

        val totalPages = maxOf(1, (histories.size + pageSize - 1) / pageSize)
        val safePage   = page.coerceIn(1, totalPages)
        val from       = (safePage - 1) * pageSize
        val slice      = histories.drop(from).take(pageSize)

        val title = if (worldName != null) "$worldName の履歴" else "全ワールドの履歴"
        sender.sendMessage("§8§m━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
        sender.sendMessage("   §e§lOra§6§lWorld§e§lRegen §7| §f$title  §8[${safePage}/${totalPages}]")
        sender.sendMessage("§8§m━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")

        if (slice.isEmpty()) {
            sender.sendMessage(" §7履歴はありません。")
        } else {
            slice.forEach { h ->
                plugin.historyManager.formatHistory(h).forEach { line ->
                    sender.sendMessage(" $line")
                }
                sender.sendMessage("")
            }
        }

        if (totalPages > 1) {
            sender.sendMessage(" §7次のページ: §e/owr history${if (worldName != null) " $worldName" else ""} ${safePage + 1}")
        }
        sender.sendMessage("§8§m━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
    }

    fun register() {
        commandObject.register()
    }
}