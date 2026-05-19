package oraserver.oraworldregen.model

enum class RegenStatus(val displayName: String) {
    IDLE("待機中"),
    COUNTDOWN("カウントダウン中"),
    TELEPORTING("プレイヤー転送中"),
    UNLOADING("アンロード中"),
    DELETING("削除中"),
    CREATING("生成中"),
    COMPLETE("完了"),
    FAILED("失敗")
}
