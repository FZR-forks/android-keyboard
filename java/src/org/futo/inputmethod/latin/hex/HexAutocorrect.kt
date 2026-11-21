package org.futo.inputmethod.latin.hex

import android.content.Context
import android.util.Log
import androidx.datastore.preferences.core.booleanPreferencesKey
import org.futo.inputmethod.keyboard.Keyboard
import org.futo.inputmethod.latin.Dictionary
import org.futo.inputmethod.latin.NgramContext
import org.futo.inputmethod.latin.SuggestedWords
import org.futo.inputmethod.latin.SuggestedWords.SuggestedWordInfo
import org.futo.inputmethod.latin.WordComposer
import org.futo.inputmethod.latin.common.Constants
import org.futo.inputmethod.latin.settings.SettingsValues
import org.futo.inputmethod.latin.uix.SettingsKey
import org.futo.inputmethod.latin.uix.getSetting
import java.io.IOException
import java.util.Locale

private val languageSettings = listOf("en", "de").associateWith {
    SettingsKey(booleanPreferencesKey("hex_autocorrect_$it"), false)
}

internal fun hexAutocorrectSetting(language: String) = languageSettings[language]

/** Used on the dictionary dispatcher, where the decoder's prefix caches are confined. */
internal class HexAutocorrect(private val context: Context) {
    private val decoders = mutableMapOf<String, SpatialDecoder?>()
    private var lastKeyboard: Keyboard? = null
    private var lastGrid: KeyGrid? = null

    class Request(
        val preferred: SpatialDecoder, val alternate: SpatialDecoder?,
        val text: String, val grid: KeyGrid, val points: List<KeyPoint>,
    )

    private fun enabled(language: String) = hexAutocorrectSetting(language)?.let { context.getSetting(it) } == true

    private fun decoder(language: String): SpatialDecoder? {
        if (!decoders.containsKey(language)) decoders[language] = try {
            SpatialDecoder(TypingModel.asset(context.assets, language))
        } catch (e: IOException) {
            Log.w("HexAutocorrect", "Could not open the $language typing model", e)
            null
        } catch (e: IllegalArgumentException) {
            Log.w("HexAutocorrect", "Invalid $language typing model", e)
            null
        }
        return decoders[language]
    }

    fun apply(request: Request, ngram: NgramContext, blockOffensive: Boolean, dictionaryWords: SuggestedWords): SuggestedWords {
        val typed = dictionaryWords.mTypedWordInfo ?: return dictionaryWords
        if (typed.mWord != request.text) return dictionaryWords
        val correction = findCorrection(request, ngram, blockOffensive, dictionaryWords, typed)
        if (correction == null || correction == typed.mWord) {
            dictionaryWords.mWillAutoCorrect = false
            return dictionaryWords
        }
        val candidates = arrayListOf(typed, SuggestedWordInfo(
            correction, typed.mPrevWordsContext, SuggestedWordInfo.MAX_SCORE,
            SuggestedWordInfo.KIND_CORRECTION, Dictionary.DICTIONARY_HARDCODED,
            SuggestedWordInfo.NOT_AN_INDEX, SuggestedWordInfo.NOT_A_CONFIDENCE,
        ))
        dictionaryWords.mSuggestedWordInfoList.filterTo(candidates) {
            it.mWord != typed.mWord && it.mWord != correction
        }

        return SuggestedWords(
            candidates, dictionaryWords.mRawSuggestions, typed, dictionaryWords.mTypedWordValid,
            true, dictionaryWords.mIsObsoleteSuggestions, dictionaryWords.mInputStyle,
            dictionaryWords.mSequenceNumber, dictionaryWords.mHighlightedCandidate,
        )
    }

    private fun findCorrection(
        request: Request,
        ngram: NgramContext,
        blockOffensive: Boolean,
        dictionaryWords: SuggestedWords,
        typed: SuggestedWordInfo,
    ): String? {
        val previous = ngram.getNthPrevWord(1)?.toString().orEmpty()
        fun read(model: SpatialDecoder) =
            TypingWord(model, request.text, request.grid, request.points, previous, suggestSwearWords = !blockOffensive)
        val preferredWord = read(request.preferred)
        if (preferredWord.isKnownLiteral) return preferredWord.replacement()

        // FUTO's confidence follows committed words. A dictionary reading that agrees
        // with the alternate Hex model lets us catch a typo at the language switch itself.
        val alternate = request.alternate
        if (alternate != null) {
            val word = read(alternate)
            if (word.isKnownLiteral) return word.replacement()
            val nativeCandidate = dictionaryWords.mSuggestedWordInfoList
                .getOrNull(SuggestedWords.INDEX_OF_AUTO_CORRECTION)
            if (nativeCandidate?.mSourceDict?.mLocale?.language == alternate.model.language) {
                word.replacement()?.takeIf { it == nativeCandidate.mWord }?.let { return it }
            }
        }
        val literalLanguage = typed.mSourceDict?.mLocale?.language
        if (!literalLanguage.isNullOrEmpty() && literalLanguage != request.preferred.model.language) return null
        return preferredWord.replacement()
    }

    fun prepare(composer: WordComposer, keyboard: Keyboard, settings: SettingsValues, detected: Locale): Request? {
        val multilingual = if (settings.mInputAttributes.mLocaleOverride == null) settings.mMultilingualLocales else emptyList()
        val language = if (detected in multilingual) detected.language else settings.mLocale.language
        if (!enabled(language)) return null

        val typed = composer.typedWord
        if (composer.isResumed || composer.isAttachedToNonWord
            || composer.isCursorFrontOrMiddleOfComposingWord
            || typed.length !in 1..TypingWord.MAX_LENGTH
        ) return null

        val grid = gridFor(keyboard) ?: return null
        if (typed.any { it.lowercaseChar() !in grid.centres }) return null
        val pointers = composer.inputPointers
        if (pointers.pointerSize < typed.length) return null
        val xs = pointers.xCoordinates
        val ys = pointers.yCoordinates
        // Missing coordinates mark hardware, popup or synthetic input. Keep its literal spelling.
        val points = typed.indices.map { i ->
            if (!Constants.isValidCoordinate(xs[i]) || !Constants.isValidCoordinate(ys[i])) return null
            KeyPoint(xs[i].toDouble(), ys[i].toDouble())
        }
        val preferred = decoder(language) ?: return null
        val other = if (language == "en") "de" else "en"
        val alternate = if (enabled(other) && multilingual.isNotEmpty()
            && (settings.mLocale.language == other || multilingual.any { it.language == other })
        ) decoder(other) else null
        fun supports(decoder: SpatialDecoder) = typed.all { decoder.model.code(it.lowercaseChar()) != 0 }
        if (!supports(preferred) && (alternate == null || !supports(alternate))) return null
        return Request(preferred, alternate, typed, grid, points)
    }

    private fun gridFor(keyboard: Keyboard): KeyGrid? {
        if (keyboard === lastKeyboard) return lastGrid
        if (keyboard.mMostCommonKeyWidth <= 0 || keyboard.mMostCommonKeyHeight <= 0) return null
        val centres = buildMap {
            for (key in keyboard.sortedKeys) {
                if (key.code !in 1..Char.MAX_VALUE.code) continue
                val letter = key.code.toChar().lowercaseChar()
                if (letter.isLetter()) {
                    put(letter, KeyPoint(key.x + key.width / 2.0, key.y + key.height / 2.0))
                }
            }
        }
        lastKeyboard = keyboard
        lastGrid = centres.takeIf { it.isNotEmpty() }?.let {
            KeyGrid(it, keyboard.mMostCommonKeyWidth.toDouble(), keyboard.mMostCommonKeyHeight.toDouble())
        }
        return lastGrid
    }
}
