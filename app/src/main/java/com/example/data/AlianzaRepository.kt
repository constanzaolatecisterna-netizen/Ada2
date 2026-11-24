package com.example.data

import android.content.Context
import com.example.R
import com.example.firebase.OperationType
import com.example.firebase.handleFirestoreError
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class AlianzaRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth,
) {
    companion object {
        fun create(context: Context): AlianzaRepository {
            val dbId = context.getString(R.string.firestore_database_id)
            val db = FirebaseFirestore.getInstance(dbId)
            return AlianzaRepository(db, Firebase.auth)
        }
    }

    private fun requireUserId(): String =
        auth.currentUser?.uid ?: throw IllegalStateException("El usuario debe iniciar sesión.")

    private fun userDoc(uid: String) = db.collection("users").document(uid)
    private fun profilesCol(uid: String) = userDoc(uid).collection("profiles")
    private fun routinesCol(uid: String) = userDoc(uid).collection("routines")
    private fun remindersCol(uid: String) = userDoc(uid).collection("reminders")
    private fun contactsCol(uid: String) = userDoc(uid).collection("contacts")
    private fun messagesCol(uid: String) = userDoc(uid).collection("messages")
    private fun rulesCol(uid: String) = userDoc(uid).collection("rules")
    private fun alertsCol(uid: String) = userDoc(uid).collection("alerts")
    private fun webhooksCol(uid: String) = userDoc(uid).collection("webhooks")

    fun observeNetworkConfig(uid: String): Flow<UserNetworkConfig?> =
        userDoc(uid).snapshots().map { snap ->
            snap.toObject(
                UserNetworkConfig::class.java,
                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
            )
        }

    fun observeProfiles(uid: String): Flow<List<FamilyProfile>> =
        profilesCol(uid)
            .orderBy("createdAt", Query.Direction.ASCENDING)
            .snapshots()
            .map { snap ->
                snap.documents.mapNotNull { doc ->
                    doc.toObject(
                        FamilyProfile::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
            }

    fun observeRoutines(uid: String): Flow<List<RoutineItem>> =
        routinesCol(uid)
            .orderBy("createdAt", Query.Direction.ASCENDING)
            .snapshots()
            .map { snap ->
                snap.documents.mapNotNull { doc ->
                    doc.toObject(
                        RoutineItem::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
            }

    fun observeReminders(uid: String): Flow<List<ReminderItem>> =
        remindersCol(uid)
            .orderBy("createdAt", Query.Direction.ASCENDING)
            .snapshots()
            .map { snap ->
                snap.documents.mapNotNull { doc ->
                    doc.toObject(
                        ReminderItem::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
            }

    fun observeContacts(uid: String): Flow<List<ContactItem>> =
        contactsCol(uid)
            .orderBy("createdAt", Query.Direction.ASCENDING)
            .snapshots()
            .map { snap ->
                snap.documents.mapNotNull { doc ->
                    doc.toObject(
                        ContactItem::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
            }

    fun observeMessages(uid: String): Flow<List<AllianceMessage>> =
        messagesCol(uid)
            .orderBy("createdAt", Query.Direction.ASCENDING)
            .snapshots()
            .map { snap ->
                snap.documents.mapNotNull { doc ->
                    doc.toObject(
                        AllianceMessage::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
            }

    fun observeRules(uid: String): Flow<List<AllianceRule>> =
        rulesCol(uid)
            .orderBy("createdAt", Query.Direction.ASCENDING)
            .snapshots()
            .map { snap ->
                snap.documents.mapNotNull { doc ->
                    doc.toObject(
                        AllianceRule::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
            }

    fun observeAlerts(uid: String): Flow<List<PushAlert>> =
        alertsCol(uid)
            .orderBy("createdAt", Query.Direction.ASCENDING)
            .snapshots()
            .map { snap ->
                snap.documents.mapNotNull { doc ->
                    doc.toObject(
                        PushAlert::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
            }

    fun observeWebhooks(uid: String): Flow<List<PushWebhookEndpoint>> =
        webhooksCol(uid)
            .orderBy("createdAt", Query.Direction.ASCENDING)
            .snapshots()
            .map { snap ->
                snap.documents.mapNotNull { doc ->
                    doc.toObject(
                        PushWebhookEndpoint::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
            }

    suspend fun ensureInitialSeed(uid: String): Result<Unit> = runFirestoreWrite(
        op = OperationType.WRITE,
        path = "users/$uid",
    ) {
        val existingProfiles = profilesCol(uid).limit(1).get().await()
        if (!existingProfiles.isEmpty) return@runFirestoreWrite

        val batch = db.batch()
        val configSnap = userDoc(uid).get().await()
        if (!configSnap.exists()) {
            batch.set(
                userDoc(uid),
                mapOf(
                    "uid" to uid,
                    "networkCode" to DefaultSeed.DEFAULT_NETWORK_CODE,
                    "activeProfileId" to DefaultSeed.DEFAULT_ACTIVE_PROFILE_ID,
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
            )
        }

        for (seed in DefaultSeed.profiles) {
            batch.set(
                profilesCol(uid).document(seed.id),
                mapOf(
                    "ownerUid" to uid,
                    "name" to seed.name,
                    "role" to seed.role,
                    "colorHex" to seed.colorHex,
                    "persona" to seed.persona,
                    "availability" to seed.availability,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
            )
            for ((rtId, rtText) in seed.routines) {
                batch.set(
                    routinesCol(uid).document(rtId),
                    mapOf(
                        "ownerUid" to uid,
                        "profileId" to seed.id,
                        "text" to rtText,
                        "done" to false,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp(),
                    )
                )
            }
            for ((remId, remTime, remText) in seed.reminders) {
                batch.set(
                    remindersCol(uid).document(remId),
                    mapOf(
                        "ownerUid" to uid,
                        "profileId" to seed.id,
                        "time" to remTime,
                        "text" to remText,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp(),
                    )
                )
            }
            for ((cId, cAlias, cPhone) in seed.contacts) {
                batch.set(
                    contactsCol(uid).document(cId),
                    mapOf(
                        "ownerUid" to uid,
                        "profileId" to seed.id,
                        "alias" to cAlias,
                        "phone" to cPhone,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp(),
                    )
                )
            }
        }

        batch.set(
            messagesCol(uid).document("msg_welcome"),
            mapOf(
                "ownerUid" to uid,
                "fromName" to "ADA",
                "toName" to "Toda la Familia",
                "text" to "Bienvenidos a la Red Alianza. Todos los perfiles familiares están enlazados.",
                "time" to nowHHMM(),
                "status" to "Entregado",
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp(),
            )
        )

        batch.commit().await()
    }

    suspend fun updateNetworkCode(code: String): Result<Unit> {
        val uid = requireUserId()
        val clean = code.trim().uppercase().take(40)
        if (clean.isEmpty()) return Result.failure(IllegalArgumentException("Código vacío"))
        return runFirestoreWrite(OperationType.UPDATE, "users/$uid") {
            userDoc(uid).update(
                mapOf(
                    "networkCode" to clean,
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
            ).await()
        }
    }

    suspend fun selectActiveProfile(profileId: String): Result<Unit> {
        val uid = requireUserId()
        return runFirestoreWrite(OperationType.UPDATE, "users/$uid") {
            userDoc(uid).update(
                mapOf(
                    "activeProfileId" to profileId,
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
            ).await()
        }
    }

    suspend fun createProfile(
        name: String,
        role: String,
        phone: String,
        colorIndex: Int,
    ): Result<String> {
        val uid = requireUserId()
        val cleanName = name.trim().take(60)
        val cleanRole = role.trim().ifEmpty { "Familia" }.take(60)
        val colorHex = DefaultSeed.profileColors[colorIndex % DefaultSeed.profileColors.size]
        val newRef = profilesCol(uid).document("prof_${System.currentTimeMillis()}")
        return runFirestoreWrite(OperationType.CREATE, newRef.path) {
            val batch = db.batch()
            batch.set(
                newRef,
                mapOf(
                    "ownerUid" to uid,
                    "name" to cleanName,
                    "role" to cleanRole,
                    "colorHex" to colorHex,
                    "persona" to "ada",
                    "availability" to AvailabilityStatus.DISPONIBLE.label,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
            )
            if (phone.isNotBlank()) {
                val contactRef = contactsCol(uid).document()
                batch.set(
                    contactRef,
                    mapOf(
                        "ownerUid" to uid,
                        "profileId" to newRef.id,
                        "alias" to cleanName,
                        "phone" to phone.trim().take(32),
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp(),
                    )
                )
            }
            batch.update(
                userDoc(uid),
                mapOf(
                    "activeProfileId" to newRef.id,
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
            )
            batch.commit().await()
            newRef.id
        }
    }

    suspend fun deleteProfile(profileId: String, fallbackProfileId: String): Result<Unit> {
        val uid = requireUserId()
        return runFirestoreWrite(OperationType.DELETE, "users/$uid/profiles/$profileId") {
            val batch = db.batch()
            batch.delete(profilesCol(uid).document(profileId))
            batch.update(
                userDoc(uid),
                mapOf(
                    "activeProfileId" to fallbackProfileId,
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
            )
            batch.commit().await()
        }
    }

    suspend fun updateProfileAvailability(
        profileId: String,
        availability: AvailabilityStatus,
    ): Result<Unit> {
        val uid = requireUserId()
        val ref = profilesCol(uid).document(profileId)
        return runFirestoreWrite(OperationType.UPDATE, ref.path) {
            ref.update(
                mapOf(
                    "availability" to availability.label,
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
            ).await()
        }
    }

    suspend fun addRoutine(profileId: String, text: String): Result<Unit> {
        val uid = requireUserId()
        val clean = text.trim().take(200)
        if (clean.isEmpty()) return Result.failure(IllegalArgumentException("Rutina vacía"))
        val ref = routinesCol(uid).document()
        return runFirestoreWrite(OperationType.CREATE, ref.path) {
            ref.set(
                mapOf(
                    "ownerUid" to uid,
                    "profileId" to profileId,
                    "text" to clean,
                    "done" to false,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
            ).await()
        }
    }

    suspend fun toggleRoutineDone(routineId: String, nextDone: Boolean): Result<Unit> {
        val uid = requireUserId()
        val ref = routinesCol(uid).document(routineId)
        return runFirestoreWrite(OperationType.UPDATE, ref.path) {
            ref.update(
                mapOf(
                    "done" to nextDone,
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
            ).await()
        }
    }

    suspend fun deleteRoutine(routineId: String): Result<Unit> {
        val uid = requireUserId()
        val ref = routinesCol(uid).document(routineId)
        return runFirestoreWrite(OperationType.DELETE, ref.path) {
            ref.delete().await()
        }
    }

    suspend fun addReminder(profileId: String, time: String, text: String): Result<Unit> {
        val uid = requireUserId()
        val cleanText = text.trim().take(200)
        val cleanTime = time.trim().take(10)
        if (cleanText.isEmpty() || cleanTime.length < 4) {
            return Result.failure(IllegalArgumentException("Recordatorio inválido"))
        }
        val ref = remindersCol(uid).document()
        return runFirestoreWrite(OperationType.CREATE, ref.path) {
            ref.set(
                mapOf(
                    "ownerUid" to uid,
                    "profileId" to profileId,
                    "time" to cleanTime,
                    "text" to cleanText,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
            ).await()
        }
    }

    suspend fun deleteReminder(reminderId: String): Result<Unit> {
        val uid = requireUserId()
        val ref = remindersCol(uid).document(reminderId)
        return runFirestoreWrite(OperationType.DELETE, ref.path) {
            ref.delete().await()
        }
    }

    suspend fun addContact(profileId: String, alias: String, phone: String): Result<Unit> {
        val uid = requireUserId()
        val cleanAlias = alias.trim().take(60)
        val cleanPhone = phone.trim().take(32)
        if (cleanAlias.isEmpty()) return Result.failure(IllegalArgumentException("Nombre vacío"))
        val ref = contactsCol(uid).document()
        return runFirestoreWrite(OperationType.CREATE, ref.path) {
            ref.set(
                mapOf(
                    "ownerUid" to uid,
                    "profileId" to profileId,
                    "alias" to cleanAlias,
                    "phone" to cleanPhone,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
            ).await()
        }
    }

    suspend fun deleteContact(contactId: String): Result<Unit> {
        val uid = requireUserId()
        val ref = contactsCol(uid).document(contactId)
        return runFirestoreWrite(OperationType.DELETE, ref.path) {
            ref.delete().await()
        }
    }

    suspend fun sendMessage(
        fromName: String,
        toName: String,
        text: String,
        status: String = "Entregado",
    ): Result<Unit> {
        val uid = requireUserId()
        val cleanText = text.trim().take(500)
        if (cleanText.isEmpty()) return Result.failure(IllegalArgumentException("Mensaje vacío"))
        val ref = messagesCol(uid).document()
        return runFirestoreWrite(OperationType.CREATE, ref.path) {
            ref.set(
                mapOf(
                    "ownerUid" to uid,
                    "fromName" to fromName.trim().take(60).ifEmpty { "ADA" },
                    "toName" to toName.trim().take(60).ifEmpty { "Toda la Familia" },
                    "text" to cleanText,
                    "time" to nowHHMM(),
                    "status" to status,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
            ).await()
        }
    }

    suspend fun markMessagesRead(messageIds: List<String>): Result<Unit> {
        if (messageIds.isEmpty()) return Result.success(Unit)
        val uid = requireUserId()
        return runFirestoreWrite(OperationType.UPDATE, "users/$uid/messages") {
            val batch = db.batch()
            for (id in messageIds) {
                batch.update(
                    messagesCol(uid).document(id),
                    mapOf(
                        "status" to "Leído",
                        "updatedAt" to FieldValue.serverTimestamp(),
                    )
                )
            }
            batch.commit().await()
        }
    }

    suspend fun deleteMessage(messageId: String): Result<Unit> {
        val uid = requireUserId()
        val ref = messagesCol(uid).document(messageId)
        return runFirestoreWrite(OperationType.DELETE, ref.path) {
            ref.delete().await()
        }
    }

    suspend fun addRule(
        trigger: RuleTrigger,
        targetProfileId: String,
        text: String,
    ): Result<Unit> {
        val uid = requireUserId()
        val clean = text.trim().take(300)
        if (clean.isEmpty()) return Result.failure(IllegalArgumentException("Regla vacía"))
        val ref = rulesCol(uid).document()
        return runFirestoreWrite(OperationType.CREATE, ref.path) {
            ref.set(
                mapOf(
                    "ownerUid" to uid,
                    "trigger" to trigger.wireValue,
                    "targetProfileId" to targetProfileId,
                    "text" to clean,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
            ).await()
        }
    }

    suspend fun deleteRule(ruleId: String): Result<Unit> {
        val uid = requireUserId()
        val ref = rulesCol(uid).document(ruleId)
        return runFirestoreWrite(OperationType.DELETE, ref.path) {
            ref.delete().await()
        }
    }

    suspend fun addAlert(
        docId: String? = null,
        app: String,
        text: String,
        source: String,
    ): Result<Unit> {
        val uid = requireUserId()
        val cleanText = text.trim().take(400)
        val cleanApp = app.trim().take(40).ifEmpty { "Sistema" }
        if (cleanText.isEmpty()) return Result.failure(IllegalArgumentException("Alerta vacía"))
        val ref = if (!docId.isNullOrBlank()) alertsCol(uid).document(docId) else alertsCol(uid).document()
        return runFirestoreWrite(OperationType.CREATE, ref.path) {
            ref.set(
                mapOf(
                    "ownerUid" to uid,
                    "app" to cleanApp,
                    "text" to cleanText,
                    "time" to nowHHMM(),
                    "source" to source,
                    "createdAt" to FieldValue.serverTimestamp(),
                )
            ).await()
        }
    }

    suspend fun deleteAlert(alertId: String): Result<Unit> {
        val uid = requireUserId()
        val ref = alertsCol(uid).document(alertId)
        return runFirestoreWrite(OperationType.DELETE, ref.path) {
            ref.delete().await()
        }
    }

    suspend fun clearAlerts(alertIds: List<String>): Result<Unit> {
        if (alertIds.isEmpty()) return Result.success(Unit)
        val uid = requireUserId()
        return runFirestoreWrite(OperationType.DELETE, "users/$uid/alerts") {
            val batch = db.batch()
            for (id in alertIds) {
                batch.delete(alertsCol(uid).document(id))
            }
            batch.commit().await()
        }
    }

    suspend fun addWebhook(name: String, url: String): Result<Unit> {
        val uid = requireUserId()
        val cleanName = name.trim().take(80)
        val cleanUrl = url.trim().take(512)
        if (cleanName.isEmpty() || (!cleanUrl.startsWith("https://") && !cleanUrl.startsWith("http://"))) {
            return Result.failure(IllegalArgumentException("Ingresa un nombre y URL http/https válida"))
        }
        val ref = webhooksCol(uid).document()
        return runFirestoreWrite(OperationType.CREATE, ref.path) {
            ref.set(
                mapOf(
                    "ownerUid" to uid,
                    "name" to cleanName,
                    "url" to cleanUrl,
                    "active" to true,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
            ).await()
        }
    }

    suspend fun toggleWebhook(webhookId: String, nextActive: Boolean): Result<Unit> {
        val uid = requireUserId()
        val ref = webhooksCol(uid).document(webhookId)
        return runFirestoreWrite(OperationType.UPDATE, ref.path) {
            ref.update(
                mapOf(
                    "active" to nextActive,
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
            ).await()
        }
    }

    suspend fun deleteWebhook(webhookId: String): Result<Unit> {
        val uid = requireUserId()
        val ref = webhooksCol(uid).document(webhookId)
        return runFirestoreWrite(OperationType.DELETE, ref.path) {
            ref.delete().await()
        }
    }

    private suspend inline fun <T> runFirestoreWrite(
        op: OperationType,
        path: String,
        crossinline block: suspend () -> T,
    ): Result<T> {
        return try {
            Result.success(block())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, op, path, auth)
            Result.failure(e)
        }
    }
}
