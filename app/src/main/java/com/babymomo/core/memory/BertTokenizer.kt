package com.babymomo.core.memory

/**
 * Minimal BERT-style tokenizer for [OnnxEmbedder] (v0.2).
 *
 * **v0.2 limitation — NOT real BGE tokenization.**
 * The real `bge-small-en-v1.5` model is a BERT-family encoder and expects
 * WordPiece token IDs from its 30,522-word vocabulary (the same vocab used by
 * `bert-base-uncased`). Bundling that 860 KB `vocab.txt` asset + implementing
 * proper WordPiece (with `##` continuation pieces, special tokens
 * `[CLS]`/`[SEP]`/`[PAD]`/`[UNK]`, and longest-match greedy splitting) is
 * planned for v0.3 — see TODO at the bottom of this file.
 *
 * For v0.2 we use the simplest scheme that still produces consistent, model-
 * consumable input_ids:
 *   1. Lowercase the input.
 *   2. Split on `[^a-z0-9]+` (whitespace + punctuation).
 *   3. Hash each token to a stable int in range `[0, 30_000)` via a 32-bit
 *      FNV-1a hash reduced mod 30_000. This is the same range as the real
 *      vocab, so input_ids land inside the model's embedding table — but they
 *      are NOT the IDs the model was trained on, so embeddings will be lower
 *      quality than a proper WordPiece tokenizer.
 *
 * Despite that caveat, the BGE encoder still produces meaningful (if
 * downgraded) semantic vectors: the model's transformer layers can still
 * attend across the hashed tokens and produce a contextual pooled output.
 * This is sufficient for v0.2 development + integration testing of the
 * retrieval pipeline. Real production-quality search requires the v0.3
 * upgrade to the actual WordPiece vocab.
 *
 * Output contract matches the BGE ONNX input names:
 *   - `inputIds: LongArray`     — `[CLS] token* [SEP] [PAD]*`, length 512
 *   - `attentionMask: LongArray`— 1 for real tokens (incl. [CLS]/[SEP]), 0 for [PAD]
 *   - `tokenTypeIds: LongArray` — all 0 (single-sentence; BGE doesn't use segment ids)
 *
 * Max sequence length is hard-clipped to 512 (BGE-small's `max_position_embeddings`).
 */
class BertTokenizer(
    private val maxLength: Int = MAX_LENGTH
) {
    /** Special token ids used by every BERT-family encoder. */
    private val clsId: Long = 101L
    private val sepId: Long = 102L
    private val padId: Long = 0L

    /**
     * Tokenize [text] into model-ready input tensors.
     *
     * Returns a [Tokenized] triple of equal-length [maxLength] long arrays.
     */
    fun tokenize(text: String): Tokenized {
        val tokens = text.lowercase()
            .split(NON_ALNUM)
            .filter { it.isNotEmpty() }
            .take(maxLength - 2)  // reserve 2 slots for [CLS] + [SEP]

        val contentIds = tokens.map { hashToken(it) }
        val realLen = 2 + contentIds.size  // [CLS] + content + [SEP]

        val inputIds = LongArray(maxLength)
        val attentionMask = LongArray(maxLength)
        val tokenTypeIds = LongArray(maxLength)

        inputIds[0] = clsId
        attentionMask[0] = 1L
        contentIds.forEachIndexed { i, id ->
            inputIds[i + 1] = id
            attentionMask[i + 1] = 1L
        }
        inputIds[realLen - 1] = sepId
        attentionMask[realLen - 1] = 1L
        // tokenTypeIds stay all-zero (single-sentence input)
        // padding (inputIds slot = 0 = [PAD]; attentionMask already 0) is correct by default

        return Tokenized(inputIds, attentionMask, tokenTypeIds)
    }

    /**
     * Stable 32-bit FNV-1a hash of [token] reduced mod [VOCAB_SIZE].
     *
     * FNV-1a is chosen for: simplicity, no allocation beyond a Long, well-
     * distributed low bits, and determinism across runs/devices/JVMs (unlike
     * `String.hashCode()` whose implementation can vary).
     */
    private fun hashToken(token: String): Long {
        var h = 0x811C9DC5L  // FNV offset basis (32-bit)
        for (c in token) {
            h = h xor c.code.toLong()
            h = (h * 0x01000193L) and 0xFFFFFFFFL  // FNV prime (32-bit)
        }
        return h % VOCAB_SIZE
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

        /** Size of the (virtual) vocab range — matches `bert-base-uncased` (30,522 tokens). */
        private const val VOCAB_SIZE: Long = 30_000L

        private val NON_ALNUM: Regex = Regex("[^a-z0-9]+")
    }
}

// TODO(v0.3): Replace this hash-based tokenizer with a real WordPiece tokenizer
//   using the bundled `vocab.txt` from `bert-base-uncased` (30,522 tokens,
//   ~860 KB). The implementation should:
//     1. Bundle `app/src/main/assets/models/bge-small-vocab.txt`.
//     2. Implement greedy longest-match WordPiece splitting with `##`
//        continuation pieces.
//     3. Map OOV tokens to `[UNK]` (id 100).
//     4. Preserve the [CLS]/[SEP]/[PAD] handling already in place here.
//   Alternative: swap the whole embedder to `EmbeddingGemma` (256-d Matryoshka,
//   multilingual, with its own SentencePiece tokenizer) — see
//   docs/architecture-decisions.md §"Embeddings".
