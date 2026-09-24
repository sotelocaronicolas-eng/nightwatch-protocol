package com.example.nightwatchprotocol.data.quiz

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

class FirestoreQuizResultRepository(
    private val firestore: FirebaseFirestore,
) : QuizResultRepository {

    override suspend fun getResult(uid: String): PlacementResult? {
        val snapshot = resultDocument(uid).get().await()
        if (!snapshot.exists()) return null

        val levelName = snapshot.getString("level") ?: return null
        val level = runCatching { PlacementLevel.valueOf(levelName) }.getOrNull() ?: return null

        @Suppress("UNCHECKED_CAST")
        val categoryNames = snapshot.get("weakCategories") as? List<String> ?: emptyList()
        val weakCategories = categoryNames
            .mapNotNull { name -> runCatching { QuizCategory.valueOf(name) }.getOrNull() }
            .toSet()

        return PlacementResult(level, weakCategories)
    }

    override suspend fun saveResult(uid: String, result: PlacementResult) {
        val data = mapOf(
            "level" to result.level.name,
            "weakCategories" to result.weakCategories.map { it.name },
            "completedAt" to FieldValue.serverTimestamp(),
        )
        resultDocument(uid).set(data).await()
    }

    private fun resultDocument(uid: String) =
        firestore.collection("users").document(uid).collection("placement").document("result")
}
