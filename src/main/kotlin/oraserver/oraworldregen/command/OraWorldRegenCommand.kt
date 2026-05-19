package oraserver.oraworldregen.command

import oraserver.oraworldregen.OraWorldRegen
import oraserver.oraworldregen.gui.MainMenuGui
import oraserver.oraworldregen.model.RegenStatus
import oraserver.orapluginapi.annotation.OraCommandBody
import oraserver.orapluginapi.command.OraCommand
import oraserver.orapluginapi.command.OraCommandObject
import oraserver.orapluginapi.commandapi.ToolTip
import oraserver.orapluginapi.commandapi.argumenttype.StringArg
import org.bukkit.entity.Player

class OraWorldRegenCommand(plugin: OraWorldRegen) : OraCommand("owr") {

    @OraCommandBody(asRoot = true)
    val mainCommand = OraCommandObject {

        // /owr → GUI を開く（プレイヤーのみ）
        literal("owr") {
            setPermission("oraworldregen.admin")
            setPlayerExecutor { data ->
                MainMenuGui(plugin).open(data.sender)
            }

            // /owr gui
            literal("gui") {
                setPlayerExecutor { data ->
                    MainMenuGui(plugin).open(data.sender)
                }
            }

            // /owr start <world>
            literal("start") {
                argument("world", StringArg.word()) {
                    suggest({ _, _, _ ->
                        plugin.configManager.worldConfigs.keys.map { ToolTip(it) }
                    })
                    setExecutor { data ->
                        val worldName = data.getArgument("world", String::class.java)
                        val config = plugin.configManager.worldConfigs[worldName]
                        if (config == null) {
                            data.sender.sendMessage("${OraWorldRegen.PREFIX}§cワールド §e${worldName} §cは登録されていません。")
                            return@setExecutor
                        }
                        if (plugin.regenManager.isRegenerating(worldName)) {
                            data.sender.sendMessage("${OraWorldRegen.PREFIX}§e${worldName} §fは現在再生成中です。")
                            return@setExecutor
                        }
                        // プレイヤーなら確認GUI、コンソールは直接実行
                        val sender = data.sender
                        if (sender is Player) {
                            MainMenuGui(plugin).open(sender)
                            sender.sendMessage("${OraWorldRegen.PREFIX}§fGUIから再生成を開始してください。")
                        } else {
                            plugin.regenManager.startRegen(worldName)
                            sender.sendMessage("${OraWorldRegen.PREFIX}§e${worldName} §fの再生成を開始しました。")
                        }
                    }
                }
            }

            // /owr cancel <world>
            literal("cancel") {
                argument("world", StringArg.word()) {
                    suggest({ _, _, _ ->
                        plugin.regenManager.tasks.keys.map { ToolTip(it) }
                    })
                    setExecutor { data ->
                        val worldName = data.getArgument("world", String::class.java)
                        val cancelled = plugin.regenManager.cancelRegen(worldName)
                        if (cancelled) {
                            data.sender.sendMessage("${OraWorldRegen.PREFIX}§e${worldName} §fのカウントダウンをキャンセルしました。")
                        } else {
                            val task = plugin.regenManager.tasks[worldName]
                            if (task == null) {
                                data.sender.sendMessage("${OraWorldRegen.PREFIX}§e${worldName} §fは現在再生成中ではありません。")
                            } else {
                                data.sender.sendMessage("${OraWorldRegen.PREFIX}§cカウントダウン以外のフェーズはキャンセルできません。§7(${task.status.displayName})")
                            }
                        }
                    }
                }
            }

            // /owr status
            literal("status") {
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
                                task.failReason?.let { sender.sendMessage("   §8└ §c失敗: $it") }
                            } else if (config.cronSchedules.isNotEmpty()) {
                                sender.sendMessage("   §8└ §7スケジュール: §f${config.cronSchedules.joinToString(", ")}")
                            }
                        }
                    }

                    sender.sendMessage(" §7ホワイトリスト: ${if (plugin.whitelistManager.isBlocking) "§c有効（再生成中）" else "§a無効"}")
                    sender.sendMessage("§8§m━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
                }
            }

            // /owr list
            literal("list") {
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
                            val cronStr = config.cronSchedules.joinToString(", ").ifEmpty { "(スケジュールなし)" }
                            if (config.enabled) {
                                sender.sendMessage(" §a✔ §e${worldName} §8│ §7${cronStr}")
                            } else {
                                sender.sendMessage(" §c✘ §7${worldName} §8│ §7${cronStr} §c(無効)")
                            }
                            sender.sendMessage("   §8└ §7環境:§f${config.environment.name}  退避先:§f${config.fallbackWorld}  CD:§f${config.countdownSeconds}秒")
                        }
                    }
                    sender.sendMessage("§8§m━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
                }
            }

            // /owr reload
            literal("reload") {
                setExecutor { data ->
                    try {
                        plugin.reloadPlugin()
                        data.sender.sendMessage("${OraWorldRegen.PREFIX}§a設定を再読み込みしました。")
                    } catch (e: Exception) {
                        data.sender.sendMessage("${OraWorldRegen.PREFIX}§c再読み込みに失敗しました: §7${e.message}")
                    }
                }
            }

            // /owr help
            literal("help") {
                setExecutor { data ->
                    val s = data.sender
                    s.sendMessage("§e§lOra§6§lWorld§e§lRegen §7| §7v${plugin.description.version}")
                    s.sendMessage("§6/owr             §8» §7GUI を開く")
                    s.sendMessage("§6/owr gui         §8» §7メインメニューを開く")
                    s.sendMessage("§6/owr start §e<W>  §8» §7ワールドの再生成を開始")
                    s.sendMessage("§6/owr cancel §e<W> §8» §7カウントダウンをキャンセル")
                    s.sendMessage("§6/owr status      §8» §7全ワールドの状態を表示")
                    s.sendMessage("§6/owr list        §8» §7登録ワールド一覧")
                    s.sendMessage("§6/owr reload      §8» §7設定を再読み込み")
                    s.sendMessage("§6/owr help        §8» §7このヘルプを表示")
                }
            }
        }
    }
}
