// Adapted from Hex for Android. See assets/hex-typing/licenses/Hex-MIT.txt.
package org.futo.inputmethod.latin.hex

/** Apply Hex's word policy to a snapshot of FUTO's composition. */
internal class TypingWord(
    private val decoder: SpatialDecoder,
    private val token: String,
    private val taps: List<SpatialDecoder.Tap>,
    previousWord: String,
    private val suggestSwearWords: Boolean,
) {
    private val model = decoder.model
    private val previous = model.context(previousWord.lowercase())
    val isKnownLiteral: Boolean
        get() = protected() || blocked(token) || model.index(token.lowercase()) >= 0
            || model.validLiteral(token.lowercase())
    private val decision by lazy {
        decoder.decide(token.lowercase(), taps, previous, suggestSwearWords)
    }
    private fun blocked(spelling: String) = model.neverSuggest.blocks(spelling.lowercase(), suggestSwearWords, previous)
    private fun protected() = token.drop(1).any { it.isUpperCase() } ||
        (model.language == "en" && token.lowercase() in shorthand)
    private fun cased(s: String) = if (token.firstOrNull()?.isUpperCase() == true) s.replaceFirstChar { it.uppercase() } else s
    fun replacement(): String? {
        if (token.isEmpty() || taps.size != token.length || blocked(token)) return null
        val lower = token.lowercase()
        val allCaps = token.length >= 2 && token.all { it.isUpperCase() }
        if (token.drop(1).any { it.isUpperCase() } && !allCaps) return null
        if (model.language == "en") {
            if (lower in shorthand) return null
            englishFixes[lower]?.takeUnless(::blocked)?.let { return if (allCaps) {
                if (token.length >= 3) it.uppercase() else null
            } else cased(it) }
        }
        return if (allCaps) null else decision.replace?.let(::cased)
    }
    fun suggestions(): List<String> {
        if (token.length !in 1..MAX_LENGTH) return emptyList()
        val options = LinkedHashMap<String, Int>()
        for (w in model.completions(token.lowercase(), suggestSwearWords, previous)) options[model.text(w)] = model.frequencyCost(w)
        if (options.size < 3 && token.length >= 2 && !protected()) for (r in decision.readings)
            options.putIfAbsent(r.text, r.frequency)
        return options.entries.sortedWith(compareBy<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
            .take(3).map { cased(it.key) }
    }
    companion object {
        const val MAX_LENGTH = 32
        private val shorthand = "ok okay ty thx pls plz tmrw tbh idk imo imho lol lmao omg brb btw afaik fyi nvm rn irl jk np yw smh ikr ngl fr gg dm pr prs ci ui ux ios macos api apis url urls json sql ssh cli gpu cpu ram sdk hex wisp seer rune kit gonna wanna gotta kinda sorta yeah yep nope nah hey yo".split(' ').toSet()
    }
}
