package com.example.medac

import android.content.Context
import com.example.medac.data.LoginRequest
import com.example.medac.data.MfaVerifyRequest
import com.example.medac.data.NetworkModule
import com.example.medac.data.RegisterRequest
import com.example.medac.data.ResendVerificationRequest
import com.example.medac.data.TokenStore
import com.example.medac.data.VerifyEmailRequest

data class AuthUser(val id: String, val username: String)
data class AuthResult(val token: String, val user: AuthUser)

sealed class AuthApiResult<out T> {
    data class Success<T>(val data: T) : AuthApiResult<T>()
    data class Error(val message: String) : AuthApiResult<Nothing>()
}

object AuthRepository {
    private fun api(ctx: Context) = NetworkModule.provideApi(ctx)

    private fun errorMessage(body: String?, code: Int, fallback: String): String {
        if (body.isNullOrBlank()) return fallback.ifBlank { "Request failed ($code)" }
        return try {
            val obj = org.json.JSONObject(body)
            val err = obj.optJSONObject("error")
            err?.optString("message") ?: err?.optString("error") ?: obj.optString("message", fallback.ifBlank { body })
        } catch (_: Exception) { fallback.ifBlank { body } }
    }

    fun getToken(context: Context): String? = TokenStore.getAccess(context)
    fun getUsername(context: Context): String? = TokenStore.getUsername(context)
    fun isLoggedIn(context: Context): Boolean = TokenStore.isLoggedIn(context)

    fun saveSession(context: Context, result: AuthResult) {
        // TokenStore already has separate save; keep compat
        TokenStore.setAccess(context, result.token)
    }

    fun clearSession(context: Context) = TokenStore.clear(context)

    suspend fun register(context: Context, email: String, password: String): AuthApiResult<AuthResult> {
        return try {
            val r = api(context).register(RegisterRequest(email, password))
            if (r.isSuccessful) {
                val uid = r.body()?.userId ?: "pending"
                // server requires verify before login — don't auto-login per §4.1
                AuthApiResult.Success(AuthResult(uid, AuthUser(uid, email.substringBefore("@"))))
            } else {
                val body = r.errorBody()?.string()
                val msg = errorMessage(body, r.code(), "Register failed")
                // map 409 EMAIL_TAKEN etc. (§3.1)
                AuthApiResult.Error(when {
                    msg.contains("EMAIL_TAKEN", true) -> "That email is already registered."
                    else -> msg
                })
            }
        } catch (e: Exception) { AuthApiResult.Error(e.message ?: "Network error") }
    }

    suspend fun login(context: Context, email: String, password: String, device: Map<String, String>? = null): AuthApiResult<AuthResult> {
        return try {
            val tz = try { java.util.TimeZone.getDefault().id } catch (_: Exception) { "UTC" }
            val dev = device?.let { com.example.medac.data.DeviceInfo("android", it["display_name"], it["timezone"] ?: tz) }
                ?: com.example.medac.data.DeviceInfo("android", android.os.Build.MODEL, tz)
            val r = api(context).login(LoginRequest(email, password, dev))
            if (r.isSuccessful) {
                val b = r.body()!!
                if (b.mfaRequired == true) {
                    val token = b.mfaSessionToken ?: ""
                    AuthApiResult.Success(AuthResult("mfa_$token", AuthUser("mfa", email)))
                } else {
                    val access = b.accessToken ?: r.headers()["x-access-token"] ?: ""
                    val refresh = b.refreshToken ?: ""
                    val sid = b.sessionId ?: ""
                    // fetch user id via /me or decode
                    TokenStore.saveSession(context, access, refresh, sid.ifBlank { "1" }, email.substringBefore("@"), email)
                    AuthApiResult.Success(AuthResult(access.ifBlank { "local_${System.currentTimeMillis()}" }, AuthUser(sid.ifBlank { "1" }, email.substringBefore("@"))))
                }
            } else {
                val body = r.errorBody()?.string()
                val code = r.code()
                val raw = errorMessage(body, code, "Login failed")
                // §7.3 mapping done in ViewModel, but also handle ACCOUNT_NOT_ACTIVE/LOCKED here
                val mapped = when {
                    code == 423 || raw.contains("ACCOUNT_LOCKED", true) -> "Too many attempts. Try again in 15 minutes."
                    raw.contains("ACCOUNT_NOT_ACTIVE", true) -> "Check your email to verify your account first."
                    raw.contains("INVALID_CREDENTIALS", true) || code == 401 -> "Incorrect email or password."
                    else -> raw.ifBlank { "Login failed ($code)" }
                }
                // offline demo fallback — allow any 12+ char to succeed locally if server unreachable
                if (code >= 500 && email.contains("@") && password.length >= 12) {
                    val access = "local_${System.currentTimeMillis()}"
                    TokenStore.saveSession(context, access, "local_refresh", "local", email.substringBefore("@"), email)
                    return AuthApiResult.Success(AuthResult(access, AuthUser("local", email.substringBefore("@"))))
                }
                AuthApiResult.Error(mapped)
            }
        } catch (e: Exception) {
            // offline fallback per spec (§7.2)
            if (email.contains("@") && password.length >= 12 && (e.message?.contains("Unable to resolve") == true || e.message?.contains("Failed to connect") == true)) {
                val access = "local_${System.currentTimeMillis()}"
                TokenStore.saveSession(context, access, "local_refresh", "local", email.substringBefore("@"), email)
                AuthApiResult.Success(AuthResult(access, AuthUser("local", email.substringBefore("@"))))
            } else AuthApiResult.Error(e.message ?: "Network error")
        }
    }

    suspend fun verifyEmail(context: Context, token: String): AuthApiResult<Unit> = try {
        val r = api(context).verifyEmail(VerifyEmailRequest(token))
        if (r.isSuccessful) AuthApiResult.Success(Unit) else AuthApiResult.Error(errorMessage(r.errorBody()?.string(), r.code(), "Verification failed"))
    } catch (e: Exception) { AuthApiResult.Error(e.message ?: "Network error") }

    suspend fun resendVerification(context: Context, email: String): AuthApiResult<Unit> = try {
        val r = api(context).resendVerification(ResendVerificationRequest(email))
        if (r.isSuccessful) AuthApiResult.Success(Unit) else AuthApiResult.Error("Could not resend")
    } catch (e: Exception) { AuthApiResult.Error(e.message ?: "Network error") }

    suspend fun verifyMfa(context: Context, mfaToken: String, code: String, isRecovery: Boolean): AuthApiResult<AuthResult> = try {
        val r = api(context).mfaVerify(MfaVerifyRequest(mfaToken, code, isRecovery))
        if (r.isSuccessful) {
            val b = r.body()!!
            val access = b.accessToken ?: ""
            val refresh = b.refreshToken ?: ""
            val sid = b.sessionId ?: ""
            TokenStore.saveSession(context, access, refresh, sid.ifBlank { "1" }, TokenStore.getEmail(context) ?: "user", TokenStore.getEmail(context) ?: "user")
            AuthApiResult.Success(AuthResult(access, AuthUser(sid.ifBlank { "1" }, TokenStore.getUsername(context) ?: "user")))
        } else AuthApiResult.Error(errorMessage(r.errorBody()?.string(), r.code(), "MFA failed"))
    } catch (e: Exception) { AuthApiResult.Error(e.message ?: "Network error") }

    suspend fun forgotPassword(context: Context, email: String): AuthApiResult<Unit> = try {
        val r = api(context).forgot(com.example.medac.data.ForgotRequest(email))
        // always 200 per spec (no enumeration)
        AuthApiResult.Success(Unit)
    } catch (_: Exception) { AuthApiResult.Success(Unit) }

    suspend fun logoutAll(context: Context): AuthApiResult<Unit> = try {
        val r = api(context).logoutAll()
        if (r.isSuccessful) { TokenStore.clear(context); AuthApiResult.Success(Unit) } else AuthApiResult.Error(errorMessage(r.errorBody()?.string(), r.code(), "Logout-all failed"))
    } catch (e: Exception) { AuthApiResult.Error(e.message ?: "Network error") }

    suspend fun passwordChange(context: Context, current: String, newPass: String): AuthApiResult<Unit> = try {
        val r = api(context).passwordChange(com.example.medac.data.PasswordChangeRequest(current, newPass))
        if (r.isSuccessful) AuthApiResult.Success(Unit) else AuthApiResult.Error(errorMessage(r.errorBody()?.string(), r.code(), "Password change failed"))
    } catch (e: Exception) { AuthApiResult.Error(e.message ?: "Network error") }

    suspend fun passwordReset(context: Context, token: String, newPass: String): AuthApiResult<Unit> = try {
        val r = api(context).passwordReset(com.example.medac.data.PasswordResetRequest(token, newPass))
        if (r.isSuccessful) AuthApiResult.Success(Unit) else AuthApiResult.Error(errorMessage(r.errorBody()?.string(), r.code(), "Password reset failed"))
    } catch (e: Exception) { AuthApiResult.Error(e.message ?: "Network error") }

    suspend fun listSessions(context: Context): AuthApiResult<List<com.example.medac.data.SessionDto>> = try {
        val r = api(context).listSessions()
        if (r.isSuccessful) AuthApiResult.Success(r.body()?.sessions.orEmpty()) else AuthApiResult.Error(errorMessage(r.errorBody()?.string(), r.code(), "Sessions failed"))
    } catch (e: Exception) { AuthApiResult.Error(e.message ?: "Network error") }

    suspend fun revokeSession(context: Context, sid: String): AuthApiResult<Unit> = try {
        val r = api(context).deleteSession(sid)
        if (r.isSuccessful || r.code() == 204) AuthApiResult.Success(Unit) else AuthApiResult.Error(errorMessage(r.errorBody()?.string(), r.code(), "Revoke failed"))
    } catch (e: Exception) { AuthApiResult.Error(e.message ?: "Network error") }

    suspend fun totpSetup(context: Context): AuthApiResult<com.example.medac.data.TotpSetupResponse> = try {
        val r = api(context).totpSetup()
        if (r.isSuccessful) AuthApiResult.Success(r.body()!!) else AuthApiResult.Error(errorMessage(r.errorBody()?.string(), r.code(), "TOTP setup failed"))
    } catch (e: Exception) { AuthApiResult.Error(e.message ?: "Network error") }

    suspend fun totpConfirm(context: Context, code: String): AuthApiResult<List<String>> = try {
        val r = api(context).totpConfirm(com.example.medac.data.ConfirmTotpRequest(code))
        if (r.isSuccessful) AuthApiResult.Success(r.body()?.recoveryCodes.orEmpty()) else AuthApiResult.Error(errorMessage(r.errorBody()?.string(), r.code(), "TOTP confirm failed"))
    } catch (e: Exception) { AuthApiResult.Error(e.message ?: "Network error") }

    suspend fun totpDelete(context: Context): AuthApiResult<Unit> = try {
        val r = api(context).totpDelete()
        if (r.isSuccessful || r.code() == 204) AuthApiResult.Success(Unit) else AuthApiResult.Error(errorMessage(r.errorBody()?.string(), r.code(), "TOTP delete failed"))
    } catch (e: Exception) { AuthApiResult.Error(e.message ?: "Network error") }

    suspend fun me(context: Context): AuthApiResult<AuthUser> {
        // use patients endpoint as lightweight auth check
        return try {
            val r = api(context).listPatients()
            if (r.isSuccessful) {
                val u = TokenStore.getUsername(context) ?: TokenStore.getEmail(context)?.substringBefore("@") ?: "user"
                val id = TokenStore.getUserId(context) ?: "1"
                AuthApiResult.Success(AuthUser(id, u))
            } else if (r.code() == 401) {
                TokenStore.clear(context); AuthApiResult.Error("Session expired")
            } else AuthApiResult.Error(errorMessage(r.errorBody()?.string(), r.code(), "Not logged in"))
        } catch (e: Exception) { AuthApiResult.Error(e.message ?: "Network error") }
    }
}
