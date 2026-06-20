package com.luistureo.voicereminderapp.core.calendar.microsoft

import android.app.Activity
import android.content.Context
import com.microsoft.identity.client.AuthenticationCallback
import com.microsoft.identity.client.AcquireTokenSilentParameters
import com.microsoft.identity.client.IAccount
import com.microsoft.identity.client.IAuthenticationResult
import com.microsoft.identity.client.IPublicClientApplication
import com.microsoft.identity.client.ISingleAccountPublicClientApplication
import com.microsoft.identity.client.PublicClientApplication
import com.microsoft.identity.client.SignInParameters
import com.microsoft.identity.client.SilentAuthenticationCallback
import com.microsoft.identity.client.exception.MsalException
import com.luistureo.voicereminderapp.R
import com.luistureo.voicereminderapp.core.calendar.unified.CalendarSyncLogger
import com.luistureo.voicereminderapp.core.calendar.unified.CalendarAccountKey
import com.luistureo.voicereminderapp.core.calendar.unified.SharedPreferencesCalendarIncrementalSyncStore
import com.luistureo.voicereminderapp.domain.model.CalendarProvider
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

interface MicrosoftCalendarAuthController {
    val isAuthConfigured: Boolean
    val isConnected: Boolean
    val hasSession: Boolean
    val isSyncEnabled: Boolean
    val lastErrorCode: MicrosoftCalendarErrorCode?
    val syncAccountKey: String?

    fun refreshConnectionState(onComplete: (Boolean) -> Unit = {})

    fun signIn(activity: Activity, onResult: (MicrosoftCalendarAuthResult) -> Unit)

    fun signOut(onComplete: (Result<Unit>) -> Unit)

    fun pauseSync()

    fun activateExistingSession(onResult: (MicrosoftCalendarAuthResult) -> Unit)

    fun markConnectionError(code: MicrosoftCalendarErrorCode)

    fun clearConnectionError()

    suspend fun acquireAccessToken(): String
}

class MsalMicrosoftCalendarAuthController(
    context: Context,
    private val connectionState: MicrosoftCalendarConnectionStateStore =
        SharedPreferencesMicrosoftCalendarConnectionStateStore(context)
) : MicrosoftCalendarAuthController {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val application = CompletableDeferred<ISingleAccountPublicClientApplication>()
    private val incrementalSyncStore = SharedPreferencesCalendarIncrementalSyncStore(appContext)

    @Volatile
    private var connected = false

    @Volatile
    private var accountKey: String? = null

    override val isAuthConfigured: Boolean = MicrosoftCalendarConfig.isConfigured()

    override val isConnected: Boolean
        get() = connected && connectionState.syncEnabled

    override val hasSession: Boolean
        get() = connected && connectionState.hasSession

    override val isSyncEnabled: Boolean
        get() = connectionState.syncEnabled

    override val lastErrorCode: MicrosoftCalendarErrorCode?
        get() = MicrosoftCalendarErrorCode.fromStoredValue(connectionState.errorCode)

    override val syncAccountKey: String?
        get() = accountKey ?: connectionState.accountKey

    init {
        initializeApplication()
    }

    override fun refreshConnectionState(onComplete: (Boolean) -> Unit) {
        scope.launch {
            val client = runCatching { application.await() }.getOrElse { exception ->
                connected = false
                connectionState.hasSession = false
                CalendarSyncLogger.error(
                    provider = CalendarProvider.MICROSOFT_CALENDAR,
                    action = "load_current_account",
                    fallbackReason = exception.javaClass.simpleName
                )
                onComplete(false)
                return@launch
            }
            client.getCurrentAccountAsync(
                object : ISingleAccountPublicClientApplication.CurrentAccountCallback {
                    override fun onAccountLoaded(activeAccount: IAccount?) {
                        updateConnectionState(activeAccount)
                        onComplete(isConnected)
                    }

                    override fun onAccountChanged(
                        priorAccount: IAccount?,
                        currentAccount: IAccount?
                    ) {
                        updateConnectionState(currentAccount)
                        onComplete(isConnected)
                    }

                    override fun onError(exception: MsalException) {
                        connected = false
                        connectionState.errorCode = MicrosoftCalendarErrorCode
                            .fromAuthFailure(exception).value
                        CalendarSyncLogger.error(
                            provider = CalendarProvider.MICROSOFT_CALENDAR,
                            action = "load_current_account",
                            fallbackReason = exception.errorCode ?: exception.javaClass.simpleName
                        )
                        onComplete(false)
                    }
                }
            )
        }
    }

    override fun signIn(
        activity: Activity,
        onResult: (MicrosoftCalendarAuthResult) -> Unit
    ) {
        if (!isAuthConfigured) {
            CalendarSyncLogger.fallback(
                provider = CalendarProvider.MICROSOFT_CALENDAR,
                action = "auth_start",
                fallbackReason = "missing_config"
            )
            onResult(MicrosoftCalendarAuthResult.MissingConfig)
            return
        }

        CalendarSyncLogger.authStarted(CalendarProvider.MICROSOFT_CALENDAR)
        scope.launch {
            val client = runCatching { application.await() }.getOrElse { exception ->
                CalendarSyncLogger.error(
                    provider = CalendarProvider.MICROSOFT_CALENDAR,
                    action = "auth_start",
                    fallbackReason = exception.javaClass.simpleName
                )
                onResult(MicrosoftCalendarAuthResult.Error(exception))
                return@launch
            }
            val callback = object : AuthenticationCallback {
                override fun onSuccess(authenticationResult: IAuthenticationResult) {
                    connected = true
                    accountKey = CalendarAccountKey.fromStableId(
                        authenticationResult.account?.id
                    )
                    connectionState.hasSession = true
                    connectionState.syncEnabled = true
                    connectionState.accountKey = accountKey
                    connectionState.errorCode = null
                    CalendarSyncLogger.authSuccess(CalendarProvider.MICROSOFT_CALENDAR)
                    onResult(MicrosoftCalendarAuthResult.Success)
                }

                override fun onError(exception: MsalException) {
                    connected = false
                    connectionState.errorCode = MicrosoftCalendarErrorCode
                        .fromAuthFailure(exception).value
                    CalendarSyncLogger.error(
                        provider = CalendarProvider.MICROSOFT_CALENDAR,
                        action = "auth",
                        fallbackReason = exception.errorCode ?: exception.javaClass.simpleName
                    )
                    onResult(MicrosoftCalendarAuthResult.Error(exception))
                }

                override fun onCancel() {
                    connectionState.errorCode = MicrosoftCalendarErrorCode.AUTH_CANCELLED.value
                    CalendarSyncLogger.authCancelled(
                        CalendarProvider.MICROSOFT_CALENDAR,
                        reason = "user_cancelled"
                    )
                    onResult(MicrosoftCalendarAuthResult.Cancelled)
                }
            }
            client.signIn(
                SignInParameters.builder()
                    .withActivity(activity)
                    .withScopes(MicrosoftCalendarConfig.scopes.toList())
                    .withCallback(callback)
                    .build()
            )
        }
    }

    override fun signOut(onComplete: (Result<Unit>) -> Unit) {
        scope.launch {
            val client = runCatching { application.await() }.getOrElse { exception ->
                onComplete(Result.failure(exception))
                return@launch
            }
            client.signOut(
                object : ISingleAccountPublicClientApplication.SignOutCallback {
                    override fun onSignOut() {
                        connectionState.accountKey?.let { key ->
                            incrementalSyncStore.clearProviderAccount(
                                CalendarProvider.MICROSOFT_CALENDAR,
                                key
                            )
                        }
                        connected = false
                        accountKey = null
                        connectionState.hasSession = false
                        connectionState.syncEnabled = false
                        connectionState.accountKey = null
                        connectionState.errorCode = null
                        CalendarSyncLogger.providerDisconnected(
                            CalendarProvider.MICROSOFT_CALENDAR
                        )
                        onComplete(Result.success(Unit))
                    }

                    override fun onError(exception: MsalException) {
                        onComplete(Result.failure(exception))
                    }
                }
            )
        }
    }

    override fun pauseSync() {
        connectionState.syncEnabled = false
        connectionState.errorCode = null
        CalendarSyncLogger.providerPaused(
            CalendarProvider.MICROSOFT_CALENDAR,
            sessionPreserved = hasSession
        )
    }

    override fun markConnectionError(code: MicrosoftCalendarErrorCode) {
        connectionState.errorCode = code.value
        if (code.invalidatesSession) {
            connected = false
            connectionState.hasSession = false
        }
    }

    override fun clearConnectionError() {
        connectionState.errorCode = null
    }

    override fun activateExistingSession(onResult: (MicrosoftCalendarAuthResult) -> Unit) {
        if (!hasSession) {
            onResult(MicrosoftCalendarAuthResult.Error(MicrosoftCalendarNotConnectedException))
            return
        }
        scope.launch {
            val previousEnabled = connectionState.syncEnabled
            connectionState.syncEnabled = true
            runCatching { acquireAccessToken() }
                .onSuccess {
                    connectionState.errorCode = null
                    CalendarSyncLogger.providerActivated(CalendarProvider.MICROSOFT_CALENDAR)
                    onResult(MicrosoftCalendarAuthResult.Success)
                }
                .onFailure { error ->
                    val code = MicrosoftCalendarErrorCode.fromAuthFailure(error)
                    connectionState.syncEnabled = previousEnabled
                    connectionState.errorCode = code.value
                    if (code.invalidatesSession) {
                        connected = false
                        connectionState.hasSession = false
                    }
                    onResult(MicrosoftCalendarAuthResult.Error(error))
                }
        }
    }

    override suspend fun acquireAccessToken(): String {
        val client = application.await()
        return suspendCancellableCoroutine { continuation ->
            client.getCurrentAccountAsync(
                object : ISingleAccountPublicClientApplication.CurrentAccountCallback {
                    override fun onAccountLoaded(activeAccount: IAccount?) {
                        if (activeAccount == null) {
                            connected = false
                            connectionState.hasSession = false
                            if (continuation.isActive) {
                                continuation.resumeWithException(MicrosoftCalendarNotConnectedException)
                            }
                            return
                        }
                        acquireTokenSilently(client, activeAccount, continuation)
                    }

                    override fun onAccountChanged(
                        priorAccount: IAccount?,
                        currentAccount: IAccount?
                    ) {
                        if (currentAccount == null) {
                            connected = false
                            if (continuation.isActive) {
                                continuation.resumeWithException(MicrosoftCalendarNotConnectedException)
                            }
                        } else {
                            acquireTokenSilently(client, currentAccount, continuation)
                        }
                    }

                    override fun onError(exception: MsalException) {
                        connected = false
                        connectionState.errorCode = MicrosoftCalendarErrorCode
                            .fromAuthFailure(exception).value
                        if (continuation.isActive) continuation.resumeWithException(exception)
                    }
                }
            )
        }
    }

    private fun initializeApplication() {
        if (!isAuthConfigured) {
            application.completeExceptionally(MicrosoftCalendarNotConfiguredException)
            return
        }
        runCatching {
            PublicClientApplication.createSingleAccountPublicClientApplication(
                appContext,
                R.raw.auth_config_single_account,
                object : IPublicClientApplication.ISingleAccountApplicationCreatedListener {
                    override fun onCreated(applicationInstance: ISingleAccountPublicClientApplication) {
                        application.complete(applicationInstance)
                        refreshConnectionState()
                    }

                    override fun onError(exception: MsalException) {
                        CalendarSyncLogger.error(
                            provider = CalendarProvider.MICROSOFT_CALENDAR,
                            action = "msal_initialize",
                            fallbackReason = exception.errorCode ?: exception.javaClass.simpleName
                        )
                        application.completeExceptionally(exception)
                    }
                }
            )
        }.onFailure { exception ->
            CalendarSyncLogger.error(
                provider = CalendarProvider.MICROSOFT_CALENDAR,
                action = "msal_initialize",
                fallbackReason = exception.javaClass.simpleName
            )
            application.completeExceptionally(exception)
        }
    }

    private fun acquireTokenSilently(
        client: ISingleAccountPublicClientApplication,
        account: IAccount,
        continuation: kotlinx.coroutines.CancellableContinuation<String>
    ) {
        val callback = object : SilentAuthenticationCallback {
            override fun onSuccess(authenticationResult: IAuthenticationResult) {
                connected = true
                accountKey = CalendarAccountKey.fromStableId(
                    authenticationResult.account?.id
                )
                connectionState.hasSession = true
                connectionState.accountKey = accountKey
                connectionState.errorCode = null
                if (continuation.isActive) {
                    continuation.resume(authenticationResult.accessToken)
                }
            }

            override fun onError(exception: MsalException) {
                connectionState.errorCode = MicrosoftCalendarErrorCode
                    .fromAuthFailure(exception).value
                CalendarSyncLogger.error(
                    provider = CalendarProvider.MICROSOFT_CALENDAR,
                    action = "acquire_token_silent",
                    fallbackReason = exception.errorCode ?: exception.javaClass.simpleName
                )
                if (continuation.isActive) continuation.resumeWithException(exception)
            }
        }
        client.acquireTokenSilentAsync(
            AcquireTokenSilentParameters.Builder()
                .withScopes(MicrosoftCalendarConfig.scopes.toList())
                .forAccount(account)
                .fromAuthority(account.authority ?: MicrosoftCalendarConfig.AUTHORITY)
                .withCallback(callback)
                .build()
        )
    }

    private fun updateConnectionState(account: IAccount?) {
        val invalidSession = lastErrorCode?.invalidatesSession == true
        connected = account != null && !invalidSession
        accountKey = CalendarAccountKey.fromStableId(account?.id)
        connectionState.hasSession = connected
        connectionState.accountKey = accountKey
    }
}

object MicrosoftCalendarAuthProvider {
    @Volatile
    private var controller: MicrosoftCalendarAuthController? = null

    fun get(context: Context): MicrosoftCalendarAuthController {
        return controller ?: synchronized(this) {
            controller ?: MsalMicrosoftCalendarAuthController(context).also { controller = it }
        }
    }
}

sealed interface MicrosoftCalendarAuthResult {
    data object Success : MicrosoftCalendarAuthResult
    data object Cancelled : MicrosoftCalendarAuthResult
    data object MissingConfig : MicrosoftCalendarAuthResult
    data class Error(val cause: Throwable) : MicrosoftCalendarAuthResult
}

object MicrosoftCalendarNotConnectedException :
    IllegalStateException("Microsoft Calendar no esta conectado.")
