package com.example.medac

import android.content.Context
import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

sealed class AuthUiState {
    object Loading : AuthUiState()
    object LoggedOut : AuthUiState()
    data class LoggedIn(val username: String) : AuthUiState()
    data class MfaRequired(val token: String) : AuthUiState()
}

class AuthViewModel : ViewModel() {
    private val _authState = MutableStateFlow<AuthUiState>(AuthUiState.Loading)
    val authState: StateFlow<AuthUiState> = _authState

    private val _isBusy = MutableStateFlow(false)
    val isBusy: StateFlow<Boolean> = _isBusy

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private val _forgotSent = MutableStateFlow(false)
    val forgotSent: StateFlow<Boolean> = _forgotSent

    fun checkSession(context: Context) {
        val appCtx = context.applicationContext
        if (AuthRepository.isLoggedIn(appCtx)) {
            val name = AuthRepository.getUsername(appCtx) ?: ""
            _authState.value = AuthUiState.LoggedIn(name)
            viewModelScope.launch {
                when (val r = AuthRepository.me(appCtx)) {
                    is AuthApiResult.Success -> _authState.value = AuthUiState.LoggedIn(r.data.username)
                    is AuthApiResult.Error -> {
                        AuthRepository.clearSession(appCtx)
                        _authState.value = AuthUiState.LoggedOut
                    }
                }
            }
        } else {
            _authState.value = AuthUiState.LoggedOut
        }
    }

    fun login(context: Context, email: String, password: String, onSuccess: () -> Unit = {}) {
        if (email.isBlank() || password.isBlank()) {
            _error.value = "Email and password are required"
            return
        }
        _isBusy.value = true
        _error.value = null
        viewModelScope.launch {
            val device = mapOf("platform" to "android", "display_name" to Build.MODEL, "timezone" to java.util.TimeZone.getDefault().id)
            when (val r = AuthRepository.login(context.applicationContext, email.trim(), password, device)) {
                is AuthApiResult.Success -> {
                    // handle MFA branch via message containing mfa
                    if (r.data.token.contains("mfa_")) {
                        _authState.value = AuthUiState.MfaRequired(r.data.token)
                    } else {
                        _authState.value = AuthUiState.LoggedIn(r.data.user.username)
                        onSuccess()
                    }
                }
                // Error messages arrive already mapped (§7.3) from the repository.
                is AuthApiResult.Error -> _error.value = r.message
            }
            _isBusy.value = false
        }
    }

    fun register(context: Context, email: String, password: String, onSuccess: () -> Unit = {}) {
        if (email.isBlank() || password.isBlank()) {
            _error.value = "Email and password are required"
            return
        }
        if (!email.contains("@")) {
            _error.value = "Enter a valid email address"
            return
        }
        if (password.length < 12) {
            _error.value = "Password must be at least 12 characters"
            return
        }
        _isBusy.value = true
        _error.value = null
        viewModelScope.launch {
            when (val r = AuthRepository.register(context.applicationContext, email.trim(), password)) {
                is AuthApiResult.Success -> {
                    // OTP disabled — skip verification, auto-login locally per user request
                    // Still create local session so app is usable without server email setup
                    com.example.medac.data.TokenStore.saveSession(
                        context.applicationContext,
                        "local_${System.currentTimeMillis()}",
                        "local_refresh_${System.currentTimeMillis()}",
                        r.data.user.id,
                        email.substringBefore("@"),
                        email.trim()
                    )
                    _authState.value = AuthUiState.LoggedIn(email.substringBefore("@"))
                    onSuccess()
                }
                is AuthApiResult.Error -> _error.value = r.message
            }
            _isBusy.value = false
        }
    }

    fun verifyMfa(context: Context, token: String, code: String, isRecovery: Boolean, onSuccess: () -> Unit = {}) {
        _isBusy.value = true; _error.value = null
        viewModelScope.launch {
            when (val r = AuthRepository.verifyMfa(context.applicationContext, token, code, isRecovery)) {
                is AuthApiResult.Success -> { _authState.value = AuthUiState.LoggedIn(r.data.user.username); onSuccess() }
                is AuthApiResult.Error -> _error.value = r.message
            }
            _isBusy.value = false
        }
    }

    fun forgotPassword(context: Context, email: String) {
        if (email.isBlank()) { _error.value = "Enter your email"; return }
        _isBusy.value = true; _error.value = null; _forgotSent.value = false
        viewModelScope.launch {
            val r = AuthRepository.forgotPassword(context.applicationContext, email.trim())
            _forgotSent.value = true
            if (r is AuthApiResult.Error) _error.value = r.message
            _isBusy.value = false
        }
    }

    // ── Extended auth (§3.7-3.10) ──
    private val _sessions = MutableStateFlow<List<com.example.medac.data.SessionDto>>(emptyList())
    val sessions: StateFlow<List<com.example.medac.data.SessionDto>> = _sessions
    private val _totpSetup = MutableStateFlow<com.example.medac.data.TotpSetupResponse?>(null)
    val totpSetup: StateFlow<com.example.medac.data.TotpSetupResponse?> = _totpSetup
    private val _recoveryCodes = MutableStateFlow<List<String>?>(null)
    val recoveryCodes: StateFlow<List<String>?> = _recoveryCodes
    private val _authActionMsg = MutableStateFlow<String?>(null)
    val authActionMsg: StateFlow<String?> = _authActionMsg

    fun logout(context: Context) {
        viewModelScope.launch {
            try { AuthRepository.logoutAll(context.applicationContext) } catch (_: Exception) {}
            AuthRepository.clearSession(context.applicationContext)
            _authState.value = AuthUiState.LoggedOut
            _error.value = null
        }
    }
    fun logoutAll(context: Context, onDone: () -> Unit = {}) {
        _isBusy.value = true; _error.value = null; _authActionMsg.value = null
        viewModelScope.launch {
            when (val r = AuthRepository.logoutAll(context.applicationContext)) {
                is AuthApiResult.Success -> { _authActionMsg.value = "Logged out everywhere"; _authState.value = AuthUiState.LoggedOut; onDone() }
                is AuthApiResult.Error -> _error.value = r.message
            }
            _isBusy.value = false
        }
    }
    fun passwordChange(context: Context, current: String, newPass: String) {
        if (newPass.length < 12) { _error.value = "Password must be at least 12 characters"; return }
        _isBusy.value = true; _error.value = null; _authActionMsg.value = null
        viewModelScope.launch {
            when (val r = AuthRepository.passwordChange(context.applicationContext, current, newPass)) {
                is AuthApiResult.Success -> _authActionMsg.value = "Password changed — all sessions revoked. Please log in again."
                is AuthApiResult.Error -> _error.value = r.message
            }
            _isBusy.value = false
        }
    }
    fun passwordResetConfirm(context: Context, token: String, newPass: String, onSuccess: () -> Unit = {}) {
        if (newPass.length < 12) { _error.value = "Password must be at least 12 characters"; return }
        _isBusy.value = true; _error.value = null
        viewModelScope.launch {
            when (val r = AuthRepository.passwordReset(context.applicationContext, token, newPass)) {
                is AuthApiResult.Success -> { _authActionMsg.value = "Password reset — please log in"; onSuccess() }
                is AuthApiResult.Error -> _error.value = r.message
            }
            _isBusy.value = false
        }
    }
    fun listSessions(context: Context) {
        viewModelScope.launch {
            when (val r = AuthRepository.listSessions(context.applicationContext)) {
                is AuthApiResult.Success -> _sessions.value = r.data
                is AuthApiResult.Error -> _error.value = r.message
            }
        }
    }
    fun revokeSession(context: Context, sid: String) {
        viewModelScope.launch {
            when (val r = AuthRepository.revokeSession(context.applicationContext, sid)) {
                is AuthApiResult.Success -> _sessions.value = _sessions.value.filterNot { it.id == sid }
                is AuthApiResult.Error -> _error.value = r.message
            }
        }
    }
    fun totpSetup(context: Context) {
        _isBusy.value = true; _error.value = null; _recoveryCodes.value = null
        viewModelScope.launch {
            when (val r = AuthRepository.totpSetup(context.applicationContext)) {
                is AuthApiResult.Success -> _totpSetup.value = r.data
                is AuthApiResult.Error -> _error.value = r.message
            }
            _isBusy.value = false
        }
    }
    fun totpConfirm(context: Context, code: String) {
        if (code.length != 6) { _error.value = "Enter 6-digit code"; return }
        _isBusy.value = true; _error.value = null
        viewModelScope.launch {
            when (val r = AuthRepository.totpConfirm(context.applicationContext, code)) {
                is AuthApiResult.Success -> { _recoveryCodes.value = r.data; _authActionMsg.value = "MFA enabled" }
                is AuthApiResult.Error -> _error.value = r.message
            }
            _isBusy.value = false
        }
    }
    fun totpDelete(context: Context) {
        _isBusy.value = true; _error.value = null
        viewModelScope.launch {
            when (val r = AuthRepository.totpDelete(context.applicationContext)) {
                is AuthApiResult.Success -> { _totpSetup.value = null; _recoveryCodes.value = null; _authActionMsg.value = "MFA disabled" }
                is AuthApiResult.Error -> _error.value = r.message
            }
            _isBusy.value = false
        }
    }
    fun clearTotpSetup() { _totpSetup.value = null; _recoveryCodes.value = null }

    fun clearError() { _error.value = null }
    fun clearAuthMsg() { _authActionMsg.value = null }
    fun goToLogin() { _authState.value = AuthUiState.LoggedOut; _error.value = null }
    fun resetForgot() { _forgotSent.value = false }
}
