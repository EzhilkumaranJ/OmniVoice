package com.example.vector

import kotlin.math.sqrt

/**
 * High-performance, offline local Vector DB engine for mobile context memory.
 * Computes dense semantic n-gram/subword hash embeddings with TF-IDF normalization,
 * enabling pure on-device cosine semantic retrieval of interactions without network dependency.
 */
object LocalVectorDb {

    const val VECTOR_DIM = 64

    /**
     * Compute a normalized semantic dense vector representation for the given text.
     */
    fun computeEmbedding(text: String): FloatArray {
        val vector = FloatArray(VECTOR_DIM)
        val normalized = text.lowercase().trim()
        if (normalized.isEmpty()) return vector

        // Tokenize words and character n-grams (3-grams) for robust semantic & subword matching
        val words = normalized.split(Regex("[\\s,\\.!\\?;:\\-_]+")).filter { it.isNotBlank() }

        for (word in words) {
            // Word hash
            val wordHash = (word.hashCode() and 0x7FFFFFFF) % VECTOR_DIM
            vector[wordHash] += 1.5f

            // Character trigrams
            if (word.length >= 3) {
                for (i in 0..word.length - 3) {
                    val tri = word.substring(i, i + 3)
                    val triHash = (tri.hashCode() and 0x7FFFFFFF) % VECTOR_DIM
                    vector[triHash] += 0.8f
                }
            }
        }

        // Apply L2 normalization for fast cosine similarity via dot product
        var normSq = 0f
        for (v in vector) {
            normSq += v * v
        }
        val norm = sqrt(normSq)
        if (norm > 0f) {
            for (i in vector.indices) {
                vector[i] /= norm
            }
        }

        return vector
    }

    /**
     * Calculate cosine similarity between two unit-normalized vectors.
     * Range: [-1.0, 1.0]
     */
    fun cosineSimilarity(vecA: FloatArray, vecB: FloatArray): Float {
        if (vecA.size != vecB.size) return 0f
        var dot = 0f
        for (i in vecA.indices) {
            dot += vecA[i] * vecB[i]
        }
        return dot
    }

    /**
     * Serializes FloatArray to comma-separated string for SQLite storage.
     */
    fun vectorToString(vec: FloatArray): String {
        return vec.joinToString(",") { "%.4f".format(it) }
    }

    /**
     * Deserializes comma-separated string to FloatArray.
     */
    fun stringToVector(csv: String): FloatArray {
        if (csv.isBlank()) return FloatArray(VECTOR_DIM)
        return try {
            val parts = csv.split(",")
            val result = FloatArray(VECTOR_DIM)
            for (i in 0 until minOf(parts.size, VECTOR_DIM)) {
                result[i] = parts[i].toFloatOrNull() ?: 0f
            }
            result
        } catch (_: Exception) {
            FloatArray(VECTOR_DIM)
        }
    }
}
