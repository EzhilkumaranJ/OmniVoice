package com.example

import com.example.security.CryptoBackupManager
import com.example.service.LlmAnalysisService
import com.example.vector.LocalVectorDb
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OmniVoiceLogicTest {

    @Test
    fun testVectorEmbeddingAndCosineSimilarity() {
        val vec1 = LocalVectorDb.computeEmbedding("Meeting with team regarding project roadmap")
        val vec2 = LocalVectorDb.computeEmbedding("Project roadmap discussion and schedule")
        val vec3 = LocalVectorDb.computeEmbedding("Cooking recipe for chocolate strawberry cake")

        val simRelated = LocalVectorDb.cosineSimilarity(vec1, vec2)
        val simUnrelated = LocalVectorDb.cosineSimilarity(vec1, vec3)

        assertTrue("Related queries should have higher cosine similarity ($simRelated > $simUnrelated)", simRelated > simUnrelated)
        assertTrue("Cosine similarity of related queries should exceed 0.2", simRelated > 0.2f)
    }

    @Test
    fun testCryptoBackupEncryptionDecryption() {
        val originalPayload = """{"tasks":[{"title":"Sync with Google Tasks","category":"TASK"}]}"""
        val passphrase = "OmniPassphrase!123"

        val encrypted = CryptoBackupManager.encrypt(originalPayload, passphrase)
        assertTrue("Encrypted payload should not be blank", encrypted.isNotBlank())

        val decrypted = CryptoBackupManager.decrypt(encrypted, passphrase)
        assertEquals("Decrypted plaintext must match original payload", originalPayload, decrypted)
    }

    @Test
    fun testOfflineRuleBasedCategorization() {
        val service = LlmAnalysisService()
        val reminderAnalysis = service.offlineRuleBasedCategorization("Remind me to call John every day at 10 AM")
        assertEquals("REMINDER", reminderAnalysis.category)
        assertEquals("DAILY", reminderAnalysis.recurringRule)

        val taskAnalysis = service.offlineRuleBasedCategorization("Todo finish presentation slides for conference")
        assertEquals("TASK", taskAnalysis.category)
    }
}
