package oraserver.oraworldregen.gui

import oraserver.oraworldregen.OraWorldRegen
import oraserver.orapluginapi.inventory.OraInventory
import oraserver.orapluginapi.inventory.OraInventoryItem
import org.bukkit.Material
import org.bukkit.entity.Player

/**
 * 手動再生成の確認ダイアログ GUI
 */
class ConfirmRegenGui(
    private val oraplugin: OraWorldRegen,
    private val worldName: String,
    private val viewer: Player,
    private val parent: OraInventory
) : OraInventory(oraplugin, "§c§l確認 - ${worldName} を再生成しますか？", 3) {

    override fun onOpen(player: Player): Boolean {
        fill(OraInventoryItem(Material.BLACK_STAINED_GLASS_PANE)
            .setDisplayName("§r").setCanClick(false))

        val config = oraplugin.configManager.worldConfigs[worldName]

        // 警告表示
        setItem(4, OraInventoryItem(Material.TNT)
            .setDisplayName("§c§l警告")
            .addLore(buildList {
                add("§f${worldName} §cのデータが完全に削除されます。")
                add("§7この操作は §c§l取り消せません。")
                add("")
                if (config?.backupEnabled == true) {
                    add("§aバックアップが作成されます。")
                } else {
                    add("§cバックアップは無効です。")
                }
                if (config?.borderEnabled == true) {
                    add("§bボーダー: ${config.borderSize.toInt()}×${config.borderSize.toInt()} が設定されます。")
                }
                add("")
                add("§7カウントダウン開始後にキャンセルは可能です。")
            })
            .setCanClick(false))

        // YES ボタン
        setItem(11, OraInventoryItem(Material.LIME_CONCRETE)
            .setDisplayName("§a§l✔ 再生成を開始する")
            .addLore("§7クリックで確定します")
            .setClickEvent {
                player.closeInventory()
                oraplugin.regenManager.startRegen(worldName, "manual:${player.name}")
                player.sendMessage("${OraWorldRegen.PREFIX}§e${worldName} §fの手動再生成を開始しました。")
            })

        // NO ボタン
        setItem(15, OraInventoryItem(Material.RED_CONCRETE)
            .setDisplayName("§c§l✘ キャンセル")
            .addLore("§7クリックで戻ります")
            .setClickEvent {
                player.closeInventory()
                parent.open(player)
            })

        return true
    }
}
