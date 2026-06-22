package com.babymomo.core.memory

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import java.text.Normalizer
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Real BERT WordPiece tokenizer for [OnnxEmbedder] (v0.3).
 *
 * Loads the canonical `bert-base-uncased` 30,522-token `vocab.txt` (bundled at
 * `assets/models/bert-base-uncased-vocab.txt`, ~232 KB) on first use and
 * performs standard BERT tokenization, matching the input distribution the
 * `bge-small-en-v1.5` encoder was trained on:
 *
 *   1. **Basic tokenization** (pre-WordPiece):
 *      - Clean text: drop control chars / U+0000 / U+FFFD, normalize all
 *        whitespace to single spaces.
 *      - Lowercase + strip combining accents (NFD, drop `Mn` marks) — the
 *        uncased BERT/BGE convention.
 *      - Split on whitespace.
 *      - Split each word on punctuation (each punctuation char becomes its own
 *        token).
 *   2. **WordPiece** per word: greedy longest-match. First piece is matched
 *      as-is; subsequent pieces are prefixed with `##`. A word that can't be
 *      fully decomposed maps to `[UNK]`.
 *      Example: `"playing"` → `["play", "##ing"]` (both in vocab).
 *   3. **Truncate** to `maxLength - 2` (leaving room for `[CLS]` + `[SEP]`).
 *   4. **Add special tokens**: `[CLS] + tokens + [SEP]`.
 *   5. **Pad** to `maxLength` with `[PAD]`.
 *   6. **Attention mask**: 1 for real tokens (incl. `[CLS]`/`[SEP]`), 0 for pad.
 *   7. **Token type IDs**: all 0 (single-sentence input; BGE doesn't use segments).
 *
 * This replaces v0.2's FNV-1a hash-based stand-in. The hash-based tokenizer
 * produced input_ids inside the embedding-table range but NOT the IDs the
 * model was trained on, so embeddings were lower-quality. With the real vocab
 * + real WordPiece, the model sees the same token distribution it was trained
 * on, and semantic quality matches the BGE-small reference numbers.
 *
 * Output contract (unchanged from v0.2 — [OnnxEmbedder] needs no inference-side
 * changes):
 *   - `inputIds: LongArray`     — `[CLS] token* [SEP] [PAD]*`, length 512
 *   - `attentionMask: LongArray`— 1 for real tokens, 0 for `[PAD]`
 *   - `tokenTypeIds: LongArray` — all 0 (single-sentence)
 *
 * The vocab map is loaded lazily on first [tokenize] call (or first access of
 * a special-token id) so app startup isn't blocked by ~232 KB of file I/O.
 * [Singleton] scope ensures the map is loaded exactly once per process.
 *
 * Max sequence length is hard-clipped to 512 (BGE-small's
 * `max_position_embeddings`).
 */
@Singleton
class BertTokenizer @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val maxLength: Int = MAX_LENGTH
    /** Lazy-loaded vocab map: token string → token id (line number in vocab.txt). */
    private val vocab: Map<String, Int> by lazy { loadVocab() }

    /** `[UNK]` id (100 in the canonical bert-base-uncased vocab). */
    private val unkTokenId: Long by lazy { (vocab[UNK_TOKEN] ?: 100).toLong() }
    /** `[CLS]` id (101). */
    private val clsTokenId: Long by lazy { (vocab[CLS_TOKEN] ?: 101).toLong() }
    /** `[SEP]` id (102). */
    private val sepTokenId: Long by lazy { (vocab[SEP_TOKEN] ?: 102).toLong() }
    /** `[PAD]` id (0). */
    private val padTokenId: Long by lazy { (vocab[PAD_TOKEN] ?: 0).toLong() }

    /**
     * Tokenize [text] into model-ready input tensors.
     *
     * Returns a [Tokenized] triple of equal-length [maxLength] long arrays:
     * `inputIds`, `attentionMask`, `tokenTypeIds`.
     */
    fun tokenize(text: String): Tokenized {
        // 1. Basic tokenization: clean → lowercase+strip-accents → whitespace split → punct split.
        val basicTokens = mutableListOf<String>()
        for (rawWord in cleanText(text).split(WHITESPACE)) {
            if (rawWord.isEmpty()) continue
            val lower = stripAccents(rawWord.lowercase())
            for (sub in splitOnPunctuation(lower)) {
                if (sub.isNotEmpty()) basicTokens.add(sub)
            }
        }

        // 2. WordPiece per word.
        val wordPieceTokens = ArrayList<String>(basicTokens.size * 2)
        for (token in basicTokens) {
            wordPieceTokens.addAll(wordPieceTokenize(token))
        }

        // 3. Truncate to maxLength - 2 (room for [CLS] + [SEP]).
        val maxContent = maxLength - 2
        val truncated = if (wordPieceTokens.size > maxContent) {
            wordPieceTokens.subList(0, maxContent)
        } else {
            wordPieceTokens
        }
        val realLen = 2 + truncated.size  // [CLS] + content + [SEP]

        // 4-7. Build tensors.
        val inputIds = LongArray(maxLength)
        val attentionMask = LongArray(maxLength)
        // tokenTypeIds stays all-zero (single-sentence input)
        val tokenTypeIds = LongArray(maxLength)

        inputIds[0] = clsTokenId
        attentionMask[0] = 1L
        truncated.forEachIndexed { i, tok ->
            inputIds[i + 1] = (vocab[tok] ?: unkTokenId.toInt()).toLong()
            attentionMask[i + 1] = 1L
        }
        inputIds[realLen - 1] = sepTokenId
        attentionMask[realLen - 1] = 1L
        // Padding slots: inputIds stay 0 (= [PAD]); attentionMask stays 0.

        return Tokenized(inputIds, attentionMask, tokenTypeIds)
    }

    /**
     * Greedy longest-match WordPiece on a single (already lowercased +
     * accent-stripped + punct-split) word.
     *
     * First piece is matched as-is; subsequent pieces are prefixed with `##`.
     * Returns `["[UNK]"]` if the word can't be fully decomposed.
     */
    private fun wordPieceTokenize(word: String): List<String> {
        if (word.isEmpty()) return emptyList()
        // Fast path: whole word is in vocab.
        if (word in vocab) return listOf(word)

        val tokens = mutableListOf<String>()
        val length = word.length
        var start = 0
        while (start < length) {
            var end = length
            var curSubstring: String? = null
            while (start < end) {
                val sub = if (start == 0) {
                    word.substring(start, end)
                } else {
                    "##" + word.substring(start, end)
                }
                if (sub in vocab) {
                    curSubstring = sub
                    break
                }
                end -= 1
            }
            if (curSubstring == null) {
                // No prefix of the remaining suffix is in vocab → whole word is [UNK].
                return listOf(UNK_TOKEN)
            }
            tokens.add(curSubstring)
            start = end
        }
        return tokens
    }

    /** Load `vocab.txt` from app assets into a token → id map. Lines are 0-indexed. */
    private fun loadVocab(): Map<String, Int> {
        val map = HashMap<String, Int>(VOCAB_CAPACITY)
        try {
            context.assets.open(VOCAB_ASSET_PATH).bufferedReader().use { reader ->
                var index = 0
                reader.forEachLine { line ->
                    map[line.trim()] = index
                    index++
                }
            }
        } catch (_: IOException) {
            // Asset missing — fall back to an empty vocab so every token maps to [UNK].
            // Shouldn't happen in shipped builds (vocab is bundled) but keeps the
            // tokenizer from crashing if the asset is stripped by a custom build.
        }
        return map
    }

    /**
     * BERT basic-tokenizer punctuation splitter: every punctuation char becomes
     * its own token, runs of non-punctuation are kept together.
     */
    private fun splitOnPunctuation(text: String): List<String> {
        val out = mutableListOf<String>()
        val sb = StringBuilder()
        for (ch in text) {
            if (isPunctuation(ch)) {
                if (sb.isNotEmpty()) {
                    out.add(sb.toString())
                    sb.setLength(0)
                }
                out.add(ch.toString())
            } else {
                sb.append(ch)
            }
        }
        if (sb.isNotEmpty()) out.add(sb.toString())
        return out
    }

    /** NFD-normalize and drop combining marks (Unicode category `Mn`). BERT uncased convention. */
    private fun stripAccents(text: String): String {
        val nfd = Normalizer.normalize(text, Normalizer.Form.NFD)
        val sb = StringBuilder(nfd.length)
        for (ch in nfd) {
            if (Character.getType(ch) != Character.NON_SPACING_MARK.toInt()) {
                sb.append(ch)
            }
        }
        return sb.toString()
    }

    /** Standard BERT `_is_punctuation`: ASCII punct ranges OR any Unicode `P*` category. */
    private fun isPunctuation(ch: Char): Boolean {
        val cp = ch.code
        if (cp in 33..47 || cp in 58..64 || cp in 91..96 || cp in 123..126) return true
        val type = Character.getType(ch)
        return type == Character.CONNECTOR_PUNCTUATION.toInt() ||
            type == Character.DASH_PUNCTUATION.toInt() ||
            type == Character.START_PUNCTUATION.toInt() ||
            type == Character.END_PUNCTUATION.toInt() ||
            type == Character.INITIAL_QUOTE_PUNCTUATION.toInt() ||
            type == Character.FINAL_QUOTE_PUNCTUATION.toInt() ||
            type == Character.OTHER_PUNCTUATION.toInt()
    }

    /** Space / tab / newline / carriage-return OR any Unicode `Zs` (space separator). */
    private fun isWhitespace(ch: Char): Boolean {
        if (ch == ' ' || ch == '\t' || ch == '\n' || ch == '\r') return true
        return Character.getType(ch) == Character.SPACE_SEPARATOR.toInt()
    }

    /**
     * Standard BERT `_is_control`: any Unicode `C*` category (Cc/Cf/Cs/Co/Cn),
     * excluding `\t` / `\n` / `\r` which are treated as whitespace instead.
     */
    private fun isControl(ch: Char): Boolean {
        if (ch == '\t' || ch == '\n' || ch == '\r') return false
        val type = Character.getType(ch)
        return type == Character.CONTROL.toInt() ||
            type == Character.FORMAT.toInt() ||
            type == Character.SURROGATE.toInt() ||
            type == Character.PRIVATE_USE.toInt() ||
            type == Character.UNASSIGNED.toInt()
    }

    /** BERT `_clean_text`: drop U+0000 / U+FFFD / control chars, normalize whitespace to ` `. */
    private fun cleanText(text: String): String {
        val sb = StringBuilder(text.length)
        for (ch in text) {
            val cp = ch.code
            if (cp == 0 || cp == 0xFFFD || isControl(ch)) continue
            if (isWhitespace(ch)) sb.append(' ') else sb.append(ch)
        }
        return sb.toString()
    }

    /** Bundle of model-input tensors returned by [tokenize]. */
    data class Tokenized(
        val inputIds: LongArray,
        val attentionMask: LongArray,
        val tokenTypeIds: LongArray
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Tokenized) return false
            return inputIds.contentEquals(other.inputIds) &&
                attentionMask.contentEquals(other.attentionMask) &&
                tokenTypeIds.contentEquals(other.tokenTypeIds)
        }

        override fun hashCode(): Int {
            var result = inputIds.contentHashCode()
            result = 31 * result + attentionMask.contentHashCode()
            result = 31 * result + tokenTypeIds.contentHashCode()
            return result
        }
    }

    companion object {
        /** BGE-small-en-v1.5 / `bert-base-uncased` max sequence length. */
        const val MAX_LENGTH: Int = 512

        /** Path of the vocab inside the APK's `assets/` dir. */
        private const val VOCAB_ASSET_PATH = "models/bert-base-uncased-vocab.txt"

        /** Capacity hint for the vocab map (matches the 30,522-line vocab + small slack). */
        private const val VOCAB_CAPACITY = 30_522

        private const val PAD_TOKEN = "[PAD]"
        private const val UNK_TOKEN = "[UNK]"
        private const val CLS_TOKEN = "[CLS]"
        private const val SEP_TOKEN = "[SEP]"

        private val WHITESPACE: Regex = Regex("\\s+")
    }
}
