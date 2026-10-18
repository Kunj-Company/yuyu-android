package com.twilighty.yuyu.feature.auth.google

import android.content.Context
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException

/** Outcome of the Credential Manager Google sheet. The ID token is passed straight to the server. */
sealed interface GoogleSignInResult {
    data class Token(val idToken: String) : GoogleSignInResult {
        override fun toString() = "Token(██)"
    }
    data object Cancelled : GoogleSignInResult
    data class Failed(val message: String) : GoogleSignInResult
}

/**
 * Google sign-in via Credential Manager. [webClientId] comes from
 * `GET /auth/config` (the instance's web client id), which becomes the ID
 * token's audience that the server verifies. Needs an Activity [context].
 */
suspend fun requestGoogleIdToken(context: Context, webClientId: String): GoogleSignInResult {
    val option = GetGoogleIdOption.Builder()
        .setServerClientId(webClientId)
        .setFilterByAuthorizedAccounts(false)
        .setAutoSelectEnabled(false)
        .build()
    val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
    return try {
        val credential = CredentialManager.create(context).getCredential(context, request).credential
        if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
            GoogleSignInResult.Token(GoogleIdTokenCredential.createFrom(credential.data).idToken)
        } else {
            GoogleSignInResult.Failed("Google didn't return an account. Try again.")
        }
    } catch (e: GetCredentialCancellationException) {
        GoogleSignInResult.Cancelled
    } catch (e: NoCredentialException) {
        GoogleSignInResult.Failed("No Google account is available on this device.")
    } catch (e: GoogleIdTokenParsingException) {
        GoogleSignInResult.Failed("Google sign-in didn't work. Try again.")
    } catch (e: GetCredentialException) {
        GoogleSignInResult.Failed("Google sign-in didn't work. Try again.")
    }
}

/** Forget the selected Google account so the next sign-in shows the picker again. */
suspend fun clearGoogleCredentialState(context: Context) {
    runCatching { CredentialManager.create(context).clearCredentialState(ClearCredentialStateRequest()) }
}
