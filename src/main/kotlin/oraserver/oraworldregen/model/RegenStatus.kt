package oraserver.oraworldregen.model

enum class RegenStatus(val displayName: String) {
    IDLE("待機中"),
    QUEUED("キュー待機中"),
    COUNTDOWN("カウントダウン中"),
    BACKING_UP("バックアップ中"),
    TELEPORTING("プレイヤー転送中"),
    UNLOADING("アンロード中"),
    DELETING("削除中"),
    CREATING("生成中"),
    SETTING_BORDER("ボーダー設定中"),
    POST_COMMANDS("コマンド実行中"),
    RETURNING("プレイヤー帰還中"),
    COMPLETE("完了"),
    FAILED("失敗")
}
