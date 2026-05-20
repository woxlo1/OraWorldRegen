package oraserver.oraworldregen.model

import org.bukkit.Location
import java.time.Instant
import java.util.UUID

class RegenTask(val worldName: String) {
    val startTime: Instant = Instant.now()
    var status: RegenStatus = RegenStatus.COUNTDOWN
    var failReason: String? = null

    /** ワールド再生成前のプレイヤー位置を保存（UUID -> 元位置） */
    val playerReturnLocations = HashMap<UUID, Location>()

    val elapsedSeconds: Long
        get() = Instant.now().epochSecond - startTime.epochSecond
}
