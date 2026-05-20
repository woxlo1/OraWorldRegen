package oraserver.oraworldregen.manager

import net.kyori.adventure.text.Component
import oraserver.oraworldregen.OraWorldRegen
import org.bukkit.Bukkit

class WhitelistManager(private val plugin: OraWorldRegen) {

    private var prevWhitelist = false
    private var prevEnforce   = false
    var isBlocking = false
        private set

    fun enableBlock() {
        if (isBlocking) return
        prevWhitelist = Bukkit.hasWhitelist()
        prevEnforce   = Bukkit.isWhitelistEnforced()

        Bukkit.setWhitelist(true)
        Bukkit.setWhitelistEnforced(true)
        isBlocking = true

        plugin.logger.info("ホワイトリストを有効化しました（再生成中）")

        val kickMsg = Component.text()
            .append(Component.text("§e§lOraWorldRegen\n\n"))
            .append(Component.text("§fただいまワールド再生成中です。\n"))
            .append(Component.text("§7しばらく経ってから再接続してください。"))
            .build()

        Bukkit.getOnlinePlayers()
            .filter { !it.isOp && !it.isWhitelisted }
            .forEach { it.kick(kickMsg) }

        Bukkit.broadcast(
            Component.text("${OraWorldRegen.PREFIX}§cホワイトリストを有効化しました。再生成中はOPのみ入室できます。")
        )
    }

    fun disableBlock() {
        if (!isBlocking) return
        Bukkit.setWhitelist(prevWhitelist)
        Bukkit.setWhitelistEnforced(prevEnforce)
        isBlocking = false

        plugin.logger.info("ホワイトリストを元の状態に戻しました")
        Bukkit.broadcast(
            Component.text("${OraWorldRegen.PREFIX}§aホワイトリストを解除しました。サーバーに入室できます！")
        )
    }

    fun shutdown() {
        if (isBlocking) {
            Bukkit.setWhitelist(prevWhitelist)
            Bukkit.setWhitelistEnforced(prevEnforce)
            isBlocking = false
        }
    }
}
