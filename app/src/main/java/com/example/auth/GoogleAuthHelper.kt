package com.example.auth

import android.content.Context
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.example.R
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.auth
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

sealed interface GoogleAuthOutcome {
    data class Success(val user: FirebaseUser) : GoogleAuthOutcome
    data object Cancelled : GoogleAuthOutcome
    data class Failure(val message: String) : GoogleAuthOutcome
}

object GoogleAuthHelper {
    private const val TAG = "GoogleAuthHelper"

    suspend fun attemptAutoSignIn(context: Context): FirebaseUser? {
        return try {
            val credentialManager = CredentialManager.create(context)
            val option = GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(true)
                .setServerClientId(context.getString(R.string.default_web_client_id))
                .setAutoSelectEnabled(true)
                .build()
            val request = GetCredentialRequest.Builder()
                .addCredentialOption(option)
                .build()
            val result = credentialManager.getCredential(context, request)
            val credential = result.credential
            if (credential is CustomCredential &&
                credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
            ) {
                val googleCred = GoogleIdTokenCredential.createFrom(credential.data)
                val firebaseCred = GoogleAuthProvider.getCredential(googleCred.idToken, null)
                Firebase.auth.signInWithCredential(firebaseCred).await().user
            } else {
                null
            }
        } catch (_: NoCredentialException) {
            null
        } catch (_: GetCredentialCancellationException) {
            null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.d(TAG, "Silent sign-in unavailable: ${e.message}")
            null
        }
    }

    suspend fun signInWithGoogle(context: Context): GoogleAuthOutcome {
        return try {
            val credentialManager = CredentialManager.create(context)
            val signInOption = GetSignInWithGoogleOption.Builder(
                context.getString(R.string.default_web_client_id)
            ).build()
            val request = GetCredentialRequest.Builder()
                .addCredentialOption(signInOption)
                .build()

            val result = credentialManager.getCredential(context, request)
            val credential = result.credential
            if (credential is CustomCredential &&
                credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
            ) {
                val googleCred = GoogleIdTokenCredential.createFrom(credential.data)
                val firebaseCred = GoogleAuthProvider.getCredential(googleCred.idToken, null)
                val user = Firebase.auth.signInWithCredential(firebaseCred).await().user
                    ?: return GoogleAuthOutcome.Failure("No se recibió el usuario autenticado.")
                GoogleAuthOutcome.Success(user)
            } else {
                GoogleAuthOutcome.Failure("Tipo de credencial inesperado.")
            }
        } catch (_: GetCredentialCancellationException) {
            GoogleAuthOutcome.Cancelled
        } catch (e: GetCredentialException) {
            Log.e(TAG, "CredentialManager sign-in failed", e)
            GoogleAuthOutcome.Failure(e.message ?: "Error al autenticar con Google.")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected sign-in error", e)
            GoogleAuthOutcome.Failure(e.message ?: "Error inesperado de autenticación.")
        }
    }

    suspend fun signOut(context: Context) {
        withContext(NonCancellable) {
            try {
                Firebase.auth.signOut()
                CredentialManager.create(context).clearCredentialState(ClearCredentialStateRequest())
            } catch (e: Exception) {
                Log.w(TAG, "Sign out warning", e)
            }
        }
    }
}
