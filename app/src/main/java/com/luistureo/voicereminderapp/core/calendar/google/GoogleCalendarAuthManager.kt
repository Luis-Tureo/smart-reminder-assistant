package com.luistureo.voicereminderapp.core.calendar.google

import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.UserRecoverableAuthException
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class GoogleCalendarAuthManager(
    context: Context
) {
    private val appContext = context.applicationContext
    private val calendarScope = Scope(GoogleCalendarConfig.CALENDAR_EVENTS_SCOPE)

    fun buildSignInClient(): GoogleSignInClient {
        val options = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(calendarScope)
            .build()

        return GoogleSignIn.getClient(appContext, options)
    }

    fun buildSignInIntent(): Intent {
        return buildSignInClient().signInIntent
    }

    fun getSignedInAccount(): GoogleSignInAccount? {
        return GoogleSignIn.getLastSignedInAccount(appContext)
    }

    fun hasCalendarPermission(account: GoogleSignInAccount? = getSignedInAccount()): Boolean {
        return account != null && GoogleSignIn.hasPermissions(account, calendarScope)
    }

    suspend fun getAccessToken(): String = withContext(Dispatchers.IO) {
        val account = getSignedInAccount() ?: throw GoogleCalendarAuthException.NotConnected

        if (!hasCalendarPermission(account)) {
            throw GoogleCalendarAuthException.MissingCalendarScope
        }

        val androidAccount = account.account ?: throw GoogleCalendarAuthException.NotConnected
        val scope = "oauth2:${GoogleCalendarConfig.CALENDAR_EVENTS_SCOPE}"

        try {
            GoogleAuthUtil.getToken(appContext, androidAccount, scope)
        } catch (exception: UserRecoverableAuthException) {
            throw GoogleCalendarAuthException.UserActionRequired(exception.intent)
        }
    }
}

sealed class GoogleCalendarAuthException(
    message: String
) : Exception(message) {
    data object NotConnected : GoogleCalendarAuthException("Google Calendar no esta conectado.")

    data object MissingCalendarScope :
        GoogleCalendarAuthException("Falta autorizar el permiso de Google Calendar.")

    class UserActionRequired(
        val recoveryIntent: Intent?
    ) : GoogleCalendarAuthException("Google Calendar requiere una autorizacion adicional.")
}
