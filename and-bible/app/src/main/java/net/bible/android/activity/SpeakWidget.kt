package net.bible.android.activity



class SpeakWidget1 : AbstractSpeakWidget() {
    override val buttons: List<String> = listOf(ACTION_FAST_FORWARD, ACTION_NEXT, ACTION_PREV, ACTION_REWIND, ACTION_SPEAK, ACTION_STOP)

}
class SpeakWidget2 : AbstractSpeakWidget() {
    override val buttons: List<String> = listOf(ACTION_FAST_FORWARD, ACTION_REWIND, ACTION_SPEAK, ACTION_STOP)

}
class SpeakWidget3 : AbstractSpeakWidget() {
    override val buttons: List<String> = listOf(ACTION_REWIND, ACTION_SPEAK)
}

