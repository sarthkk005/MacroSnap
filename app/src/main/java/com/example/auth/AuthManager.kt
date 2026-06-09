package com.example.auth

import android.content.Context
import android.util.Log
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

object AuthManager {
    private const val TAG = "AuthManager"
    // Users need to configure this with their Web Client ID from Google Cloud Console
    var WEB_CLIENT_ID = "YOUR_WEB_CLIENT_ID" // Ideally from BuildConfig

    private val _userState = MutableStateFlow<String?>(null)
    val userState: StateFlow<String?> = _userState
    
    private val _errorState = MutableStateFlow<String?>(null)
    val errorState: StateFlow<String?> = _errorState

    suspend fun signIn(context: Context) {
        if (WEB_CLIENT_ID == "YOUR_WEB_CLIENT_ID" || WEB_CLIENT_ID.isBlank()) {
            _errorState.value = "Google Sign-In configuration missing. Please update your Web Client ID in the code or secrets."
            return
        }

        val credentialManager = CredentialManager.create(context)
        val googleIdOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(false)
            .setServerClientId(WEB_CLIENT_ID)
            .setAutoSelectEnabled(true)
            .build()
            
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()
            
        try {
            val result = credentialManager.getCredential(context, request)
            val credential = result.credential
            if (credential is com.google.android.libraries.identity.googleid.GoogleIdTokenCredential) {
                // val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                // In generic Credential API, data can be extracted
                _userState.value = credential.id
                _errorState.value = null
            } else {
                _errorState.value = "Unexpected credential type."
            }
        } catch (e: GetCredentialException) {
            Log.e(TAG, "Sign in failed", e)
            _errorState.value = "Sign in failed: ${e.message}"
        }
    }
    
    fun signOut() {
        _userState.value = null
    }
}
