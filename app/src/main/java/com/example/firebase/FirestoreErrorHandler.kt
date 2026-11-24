package com.example.firebase

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

enum class OperationType {
    CREATE,
    UPDATE,
    DELETE,
    LIST,
    GET,
    WRITE,
}

@Serializable
data class FirestoreErrorInfo(
    val error: String,
    val operationType: String,
    val path: String?,
    val userId: String?,
    val email: String?,
    val providerIds: List<String>,
)

fun handleFirestoreError(
    error: Throwable,
    operationType: OperationType,
    path: String?,
    auth: FirebaseAuth,
) {
    val user = auth.currentUser
    val errInfo = FirestoreErrorInfo(
        error = error.message ?: "Unknown Firestore error",
        operationType = operationType.name.lowercase(),
        path = path,
        userId = user?.uid,
        email = user?.email,
        providerIds = user?.providerData?.mapNotNull { it.providerId } ?: emptyList(),
    )
    Log.e("FirestoreError", Json.encodeToString(errInfo), error)
}
