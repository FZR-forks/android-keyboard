// Adapted from Hex for Android. See assets/hex-typing/licenses/Hex-MIT.txt.
package org.futo.inputmethod.latin.hex

/** Apply Hex's word policy to a snapshot of FUTO's composition. */
internal class TypingWord(
    private val decoder: SpatialDecoder,
    private val token: String,
    private val grid: KeyGrid,
    private val points: List<KeyPoint>,
    previousWord: String,
    private val suggestSwearWords: Boolean,
) {
    private val model = decoder.model
    private val previous = model.context(previousWord.lowercase())
    private val lower = token.lowercase()
    private val literal = model.index(lower)
    private val knownSpelling = literal >= 0 || model.validLiteral(lower)
    private val innerCaps = token.drop(1).any { it.isUpperCase() }
    private val isShorthand = model.language == "en" && lower in shorthand
    private val blockedToken = blocked(lower)
    val isKnownLiteral: Boolean
        get() = innerCaps || isShorthand || blockedToken || knownSpelling

    private fun blocked(spelling: String) = model.neverSuggest.blocks(spelling, suggestSwearWords, previous)
    private fun cased(s: String) = if (token.first().isUpperCase()) s.replaceFirstChar { it.uppercase() } else s

    fun replacement(): String? {
        if (token.length !in 1..MAX_LENGTH || points.size != token.length || blockedToken || isShorthand) {
            return null
        }
        val allCaps = token.length >= 2 && token.all { it.isUpperCase() }
        if (innerCaps && !allCaps) return null
        if (model.language == "en") {
            englishFixes[lower]?.takeUnless { blocked(it.lowercase()) }?.let {
                return when {
                    !allCaps -> cased(it)
                    token.length >= 3 -> it.uppercase()
                    else -> null
                }
            }
        }
        if (allCaps || lower.length != token.length) return null
        val taps = points.map { decoder.score(grid, it.x, it.y) }
        return decoder.replacement(lower, taps, previous, suggestSwearWords, literal, knownSpelling)
            ?.let(::cased)
    }
    companion object {
        const val MAX_LENGTH = 32
        private val shorthand = "ok okay ty thx pls plz tmrw tbh idk imo imho lol lmao omg brb btw afaik fyi nvm rn irl jk np yw smh ikr ngl fr gg dm pr prs ci ui ux ios macos api apis url urls json sql ssh cli gpu cpu ram sdk hex wisp seer rune kit gonna wanna gotta kinda sorta yeah yep nope nah hey yo".split(' ').toSet()
    }
}
