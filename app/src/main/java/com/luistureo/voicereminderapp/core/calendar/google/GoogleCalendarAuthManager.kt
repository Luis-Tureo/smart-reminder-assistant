package com.luistureo.voicereminderapp.core.calendar.google

import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.GoogleAuthException
import com.google.android.gms.auth.UserRecoverableAuthException
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import com.luistureo.voicereminderapp.core.calendar.unified.CalendarSyncLogger
import com.luistureo.voicereminderapp.core.calendar.unified.CalendarAccountKey
import com.luistureo.voicereminderapp.core.calendar.unified.SharedPreferencesCalendarIncrementalSyncStore
import com.luistureo.voicereminderapp.domain.model.CalendarProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

class GoogleCalendarAuthManager(
    context: Context,
    private val connectionState: GoogleCalendarConnectionStateStore =
        SharedPreferencesGoogleCalendarConnectionStateStore(context)
) {
    private val appContext = context.applicationContext
    private val calendarScope = Scope(GoogleCalendarConfig.CALENDAR_EVENTS_SCOPE)
    private val incrementalSyncStore = SharedPreferencesCalendarIncrementalSyncStore(appContext)

    val isSyncEnabled: Boolean
        get() = connectionState.syncEnabled

    val hasConnectionError: Boolean
        get() = connectionState.hasError

    val lastErrorCode: GoogleCalendarErrorCode?
        get() = GoogleCalendarErrorCode.fromStoredValue(connectionState.errorCode)

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

    fun pauseSync() {
        connectionState.syncEnabled = false
        connectionState.hasError = false
        connectionState.errorCode = null
        connectionState.authPending = false
        connectionState.accountKey = syncAccountKey() ?: connectionState.accountKey
        CalendarSyncLogger.googleSyncPaused(sessionPreserved = connectionState.connected)
    }

    fun prepareReconnect(onComplete: (GoogleCalendarReconnectDecision) -> Unit) {
        connectionState.hasError = false
        connectionState.errorCode = null
        connectionState.authPending = true

        val account = getSignedInAccount()
        val stableAccountKey = CalendarAccountKey.fromStableId(account?.id ?: account?.email)
        val decision = GoogleCalendarConnectionPolicy.reconnectDecision(
            hasAccount = connectionState.connected && account != null,
            hasCalendarPermission = hasCalendarPermission(account),
            hasStableAccountId = stableAccountKey != null && account?.account != null
        )
        if (decision == GoogleCalendarReconnectDecision.REUSE_VALID_SESSION) {
            connectionState.accountKey = stableAccountKey
            connectionState.authPending = false
            CalendarSyncLogger.googleSessionReused()
            onComplete(decision)
            return
        }

        CalendarSyncLogger.googleStaleSessionDetected()
        if (connectionState.accountKey == null) {
            connectionState.accountKey = stableAccountKey
        }
        clearStaleLocalState()
        if (account == null) {
            connectionState.authPending = true
            onComplete(GoogleCalendarReconnectDecision.NEEDS_LOGIN)
            return
        }
        buildSignInClient().signOut().addOnCompleteListener { task ->
            if (!task.isSuccessful) {
                CalendarSyncLogger.googleAuthFailed("stale_session_sign_out_failed")
            }
            connectionState.authPending = true
            onComplete(GoogleCalendarReconnectDecision.NEEDS_LOGIN)
        }
    }

    suspend fun completeSignIn(
        account: GoogleSignInAccount?,
        activated: Boolean = false
    ): Boolean {
        val accountKey = CalendarAccountKey.fromStableId(account?.id ?: account?.email)
        val valid = hasCalendarPermission(account) && accountKey != null && account?.account != null
        if (!valid) {
            failPendingAuth(GoogleCalendarErrorCode.AUTH_SCOPE_DENIED)
            return false
        }

        requestAccessToken(requireNotNull(account))
        saveConnectedState(requireNotNull(accountKey), activated = activated)
        return true
    }

    suspend fun reactivateExistingSession(): Boolean {
        val account = getSignedInAccount()
        val accountKey = CalendarAccountKey.fromStableId(account?.id ?: account?.email)
        if (
            !connectionState.connected ||
            !hasCalendarPermission(account) ||
            account?.account == null ||
            accountKey == null
        ) {
            failPendingAuth(GoogleCalendarErrorCode.AUTH_SCOPE_DENIED)
            return false
        }
        requestAccessToken(account)
        saveConnectedState(accountKey, activated = true)
        return true
    }

    fun cancelPendingAuth() {
        connectionState.syncEnabled = false
        connectionState.authPending = false
        connectionState.connected = false
        connectionState.hasError = true
        connectionState.errorCode = GoogleCalendarErrorCode.AUTH_CANCELLED.value
    }

    fun failPendingAuth(errorCode: GoogleCalendarErrorCode) {
        connectionState.syncEnabled = false
        connectionState.authPending = false
        connectionState.hasError = true
        connectionState.errorCode = errorCode.value
        if (errorCode.invalidatesSession) {
            clearStaleLocalState()
            connectionState.hasError = true
            connectionState.errorCode = errorCode.value
        }
    }

    fun markConnectionError(errorCode: GoogleCalendarErrorCode) {
        connectionState.hasError = true
        connectionState.errorCode = errorCode.value
        connectionState.authPending = false
        if (errorCode.invalidatesSession) {
            clearStaleLocalState()
            connectionState.hasError = true
            connectionState.errorCode = errorCode.value
        }
    }

    fun clearConnectionError() {
        connectionState.hasError = false
        connectionState.errorCode = null
    }

    fun disconnect(onComplete: (Result<Unit>) -> Unit) {
        val accountKey = connectionState.accountKey
        buildSignInClient().signOut().addOnCompleteListener { task ->
            if (task.isSuccessful) {
                accountKey?.let { key ->
                    incrementalSyncStore.clearProviderAccount(
                        CalendarProvider.GOOGLE_CALENDAR,
                        key
                    )
                }
                connectionState.syncEnabled = false
                connectionState.connected = false
                connectionState.hasError = false
                connectionState.errorCode = null
                connectionState.authPending = false
                connectionState.accountKey = null
                CalendarSyncLogger.providerDisconnected(CalendarProvider.GOOGLE_CALENDAR)
                onComplete(Result.success(Unit))
            } else {
                onComplete(Result.failure(task.exception ?: IllegalStateException("google_sign_out_failed")))
            }
        }
    }

    fun getSignedInAccount(): GoogleSignInAccount? {
        return GoogleSignIn.getLastSignedInAccount(appContext)
    }

    fun hasCalendarPermission(account: GoogleSignInAccount? = getSignedInAccount()): Boolean {
        return account != null && GoogleSignIn.hasPermissions(account, calendarScope)
    }

    fun isConnected(account: GoogleSignInAccount? = getSignedInAccount()): Boolean {
        return connectionState.syncEnabled &&
                hasConnectedSession(account)
    }

    fun hasConnectedSession(account: GoogleSignInAccount? = getSignedInAccount()): Boolean {
        return connectionState.connected &&
                hasCalendarPermission(account) &&
                account?.account != null
    }

    fun isPaused(account: GoogleSignInAccount? = getSignedInAccount()): Boolean {
        return !connectionState.syncEnabled && hasConnectedSession(account)
    }

    fun syncAccountKey(): String? {
        val account = getSignedInAccount() ?: return null
        return CalendarAccountKey.fromStableId(account.id ?: account.email)
    }

    suspend fun getAccessToken(): String = withContext(Dispatchers.IO) {
        if (!connectionState.syncEnabled) {
            throw GoogleCalendarAuthException.SyncDisabled
        }
        if (!connectionState.connected) {
            throw GoogleCalendarAuthException.NotConnected
        }
        val account = getSignedInAccount() ?: run {
            CalendarSyncLogger.fallback(
                provider = CalendarProvider.GOOGLE_CALENDAR,
                action = "get_access_token",
                fallbackReason = "not_connected"
            )
            throw GoogleCalendarAuthException.NotConnected
        }

        if (!hasCalendarPermission(account)) {
            CalendarSyncLogger.fallback(
                provider = CalendarProvider.GOOGLE_CALENDAR,
                action = "get_access_token",
                fallbackReason = "missing_calendar_scope"
            )
            throw GoogleCalendarAuthException.MissingCalendarScope
        }

        requestAccessToken(account)
    }

    private suspend fun requestAccessToken(account: GoogleSignInAccount): String =
        withContext(Dispatchers.IO) {
        val androidAccount = account.account ?: run {
            CalendarSyncLogger.fallback(
                provider = CalendarProvider.GOOGLE_CALENDAR,
                action = "get_access_token",
                fallbackReason = "android_account_missing"
            )
            throw GoogleCalendarAuthException.NotConnected
        }
        val scope = "oauth2:${GoogleCalendarConfig.CALENDAR_EVENTS_SCOPE}"

        try {
            GoogleAuthUtil.getToken(appContext, androidAccount, scope).also {
                CalendarSyncLogger.authSuccess(CalendarProvider.GOOGLE_CALENDAR)
            }
        } catch (exception: UserRecoverableAuthException) {
            CalendarSyncLogger.fallback(
                provider = CalendarProvider.GOOGLE_CALENDAR,
                action = "get_access_token",
                fallbackReason = "user_action_required"
            )
            throw GoogleCalendarAuthException.UserActionRequired(exception.intent)
        } catch (exception: GoogleAuthException) {
            val reason = GoogleCalendarAuthFailureReason.from(exception)
            CalendarSyncLogger.googleAuthFailed(reason.logValue)
            failPendingAuth(GoogleCalendarErrorCode.fromAuthFailure(reason))
            throw GoogleCalendarAuthException.AuthenticationFailed(reason)
        } catch (exception: IOException) {
            CalendarSyncLogger.googleAuthFailed("io_exception")
            markConnectionError(GoogleCalendarErrorCode.AUTH_NETWORK_IO)
            throw GoogleCalendarAuthException.NetworkFailure(exception)
        }
    }

    private fun saveConnectedState(accountKey: String, activated: Boolean) {
        connectionState.syncEnabled = true
        connectionState.connected = true
        connectionState.hasError = false
        connectionState.errorCode = null
        connectionState.authPending = false
        connectionState.accountKey = accountKey
        CalendarSyncLogger.googleDisabledFlagCleared()
        CalendarSyncLogger.googleConnectedStateSaved()
        if (activated) CalendarSyncLogger.googleActivatedStateSaved()
    }

    private fun clearStaleLocalState() {
        connectionState.accountKey?.let { accountKey ->
            incrementalSyncStore.clearProviderAccount(
                CalendarProvider.GOOGLE_CALENDAR,
                accountKey
            )
        }
        connectionState.accountKey = null
        connectionState.connected = false
        connectionState.authPending = false
        CalendarSyncLogger.localGoogleStateCleared(reason = "stale_session")
    }
}

sealed class GoogleCalendarAuthException(
    message: String
) : Exception(message) {
    data object NotConnected : GoogleCalendarAuthException("Google Calendar no esta conectado.")

    data object MissingCalendarScope :
        GoogleCalendarAuthException("Falta autorizar el permiso de Google Calendar.")

    data object SyncDisabled :
        GoogleCalendarAuthException("La sincronizacion con Google Calendar esta desactivada.")

    class UserActionRequired(
        val recoveryIntent: Intent?
    ) : GoogleCalendarAuthException("Google Calendar requiere una autorizacion adicional.")

    class AuthenticationFailed(
        val reason: GoogleCalendarAuthFailureReason
    ) : GoogleCalendarAuthException("La sesion de Google Calendar no es valida.")

    class NetworkFailure(
        cause: IOException
    ) : GoogleCalendarAuthException("No se pudo conectar con Google Calendar.") {
        init {
            initCause(cause)
        }
    }
}

enum class GoogleCalendarAuthFailureReason(
    val logValue: String,
    val requiresFreshLogin: Boolean
) {
    INTERNAL_ERROR("internal_error", false),
    BAD_AUTHENTICATION("bad_authentication", true),
    MISSING_GAIA_ID("missing_gaia_id", true),
    TOKEN_FAILURE("failed_to_get_auth_token", true),
    AUTO_MANAGE_ERROR("auto_manage_unresolved_error", true),
    NETWORK("network_auth_error", false),
    UNKNOWN("authentication_error", true);

    companion object {
        fun from(exception: GoogleAuthException): GoogleCalendarAuthFailureReason {
            val normalized = exception.message.orEmpty().uppercase()
            return when {
                "BAD_AUTHENTICATION" in normalized -> BAD_AUTHENTICATION
                "GAIA" in normalized -> MISSING_GAIA_ID
                "FAILED TO GET AUTH TOKEN" in normalized -> TOKEN_FAILURE
                "AUTOMANAGEHELPER" in normalized || "UNRESOLVED ERROR" in normalized ->
                    AUTO_MANAGE_ERROR
                "INTERNAL_ERROR" in normalized -> INTERNAL_ERROR
                else -> UNKNOWN
            }
        }

        fun from(throwable: Throwable): GoogleCalendarAuthFailureReason {
            if (throwable is IOException || throwable.cause is IOException) return NETWORK
            val normalized = generateSequence(throwable) { it.cause }
                .joinToString(" ") { error ->
                    "${error.javaClass.simpleName} ${error.message.orEmpty()}"
                }
                .uppercase()
            return when {
                "BAD_AUTHENTICATION" in normalized -> BAD_AUTHENTICATION
                "GAIA" in normalized -> MISSING_GAIA_ID
                "FAILED TO GET AUTH TOKEN" in normalized -> TOKEN_FAILURE
                "AUTOMANAGEHELPER" in normalized || "UNRESOLVED ERROR" in normalized ->
                    AUTO_MANAGE_ERROR
                "INTERNAL_ERROR" in normalized -> INTERNAL_ERROR
                "IOEXCEPTION" in normalized || "NETWORK" in normalized -> NETWORK
                else -> UNKNOWN
            }
        }
    }
}
