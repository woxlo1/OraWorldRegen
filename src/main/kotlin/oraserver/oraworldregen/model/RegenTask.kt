package oraserver.oraworldregen.model

import java.time.Instant

class RegenTask(val worldName: String) {
    val startTime: Instant = Instant.now()
    var status: RegenStatus = RegenStatus.COUNTDOWN
    var failReason: String? = null

    val elapsedSeconds: Long
        get() = Instant.now().epochSecond - startTime.epochSecond
}
