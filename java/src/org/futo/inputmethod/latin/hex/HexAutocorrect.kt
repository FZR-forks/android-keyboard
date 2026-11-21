package org.futo.inputmethod.latin.hex

import android.content.res.AssetManager
import android.util.Log
import androidx.datastore.preferences.core.booleanPreferencesKey
import org.futo.inputmethod.keyboard.Keyboard
import org.futo.inputmethod.latin.Dictionary
import org.futo.inputmethod.latin.NgramContext
import org.futo.inputmethod.latin.SuggestedWords
import org.futo.inputmethod.latin.SuggestedWords.SuggestedWordInfo
import org.futo.inputmethod.latin.WordComposer
import org.futo.inputmethod.latin.common.Constants
import org.futo.inputmethod.latin.uix.SettingsKey
import java.io.IOException

private val languageSettings = listOf("en", "de").associateWith {
    SettingsKey(booleanPreferencesKey("hex_autocorrect_$it"), false)
}

internal fun hexAutocorrectSetting(language: String) = languageSettings[language]

/** Used on the dictionary dispatcher, where the decoder's prefix caches are confined. */
internal class HexAutocorrect(private val assets: AssetManager) {
    private val decoders = mutableMapOf<String, SpatialDecoder?>()

    fun decoder(language: String): SpatialDecoder? {
        if (!decoders.containsKey(language)) {
            decoders[language] = try {
                SpatialDecoder(TypingModel.asset(assets, language))
            } catch (e: IOException) {
                Log.w("HexAutocorrect", "Could not open the $language typing model", e)
                null
            } catch (e: IllegalArgumentException) {
                Log.w("HexAutocorrect", "Invalid $language typing model", e)
                null
            }
        }
        return decoders[language]
    }

    fun apply(
        decoder: SpatialDecoder,
        alternate: SpatialDecoder?,
        composer: WordComposer,
        keyboard: Keyboard,
        ngram: NgramContext,
        blockOffensive: Boolean,
        dictionaryWords: SuggestedWords,
    ): SuggestedWords {
        val typed = dictionaryWords.mTypedWordInfo ?: return dictionaryWords
        val previous = ngram.getNthPrevWord(1)?.toString().orEmpty()
        fun read(model: SpatialDecoder) = readTaps(model, composer, keyboard, typed.mWord)?.let { taps ->
            TypingWord(model, typed.mWord, taps, previous, suggestSwearWords = !blockOffensive)
        }
        val preferredWord = read(decoder)
        val literalLanguage = typed.mSourceDict?.mLocale?.language
        val foreignLiteral = preferredWord?.isKnownLiteral == false
            && !literalLanguage.isNullOrEmpty() && literalLanguage != decoder.model.language

        // FUTO's confidence follows committed words. A dictionary reading that agrees
        // with the alternate Hex model lets us catch a typo at the language switch itself.
        val nativeCandidate = dictionaryWords.mSuggestedWordInfoList
            .getOrNull(SuggestedWords.INDEX_OF_AUTO_CORRECTION)
        val alternateWord = if (alternate != null && preferredWord?.isKnownLiteral == false) {
            read(alternate)?.takeIf {
                it.isKnownLiteral || (
                    nativeCandidate?.mSourceDict?.mLocale?.language == alternate.model.language
                        && it.replacement() == nativeCandidate.mWord
                    )
            }
        } else null
        val word = alternateWord ?: preferredWord.takeUnless { foreignLiteral }
        val correction = word?.replacement()?.takeUnless { it == typed.mWord }
        val candidates = buildList {
            add(typed)
            correction?.let { add(candidate(it, typed, SuggestedWordInfo.KIND_CORRECTION)) }
            word?.suggestions()?.forEach {
                add(candidate(it, typed, SuggestedWordInfo.KIND_COMPLETION))
            }
            addAll(dictionaryWords.mSuggestedWordInfoList)
        }.distinctBy { it.mWord }

        return SuggestedWords(
            ArrayList(candidates),
            dictionaryWords.mRawSuggestions,
            typed,
            dictionaryWords.mTypedWordValid,
            correction != null,
            dictionaryWords.mIsObsoleteSuggestions,
            dictionaryWords.mInputStyle,
            dictionaryWords.mSequenceNumber,
        )
    }

    private fun readTaps(
        decoder: SpatialDecoder,
        composer: WordComposer,
        keyboard: Keyboard,
        typed: String,
    ): List<SpatialDecoder.Tap>? {
        if (composer.isResumed || composer.isAttachedToNonWord
            || composer.isCursorFrontOrMiddleOfComposingWord
            || typed.length !in 1..TypingWord.MAX_LENGTH
            || composer.typedWord != typed
            || keyboard.mMostCommonKeyWidth <= 0 || keyboard.mMostCommonKeyHeight <= 0
        ) return null

        val centres = buildMap {
            for (key in keyboard.sortedKeys) {
                if (key.code !in 1..Char.MAX_VALUE.code) continue
                val letter = key.code.toChar().lowercaseChar()
                if (letter.isLetter() && decoder.model.code(letter) != 0) {
                    put(letter, KeyPoint(key.x + key.width / 2.0, key.y + key.height / 2.0))
                }
            }
        }
        if (typed.any { it.lowercaseChar() !in centres }) return null
        val grid = KeyGrid(centres, keyboard.mMostCommonKeyWidth.toDouble(),
            keyboard.mMostCommonKeyHeight.toDouble())

        val pointers = composer.inputPointers
        if (pointers.pointerSize < typed.length) return null
        val xs = pointers.xCoordinates
        val ys = pointers.yCoordinates
        // Missing coordinates mark hardware, popup or synthetic input. Keep its literal spelling.
        return typed.indices.map { i ->
            if (!Constants.isValidCoordinate(xs[i]) || !Constants.isValidCoordinate(ys[i])) return null
            decoder.score(grid, xs[i].toDouble(), ys[i].toDouble())
        }
    }

    private fun candidate(word: String, typed: SuggestedWordInfo, kind: Int) = SuggestedWordInfo(
        word, typed.mPrevWordsContext, SuggestedWordInfo.MAX_SCORE, kind,
        Dictionary.DICTIONARY_HARDCODED, SuggestedWordInfo.NOT_AN_INDEX,
        SuggestedWordInfo.NOT_A_CONFIDENCE,
    )
}
