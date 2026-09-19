package com.example.medac

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.example.medac.ui.theme.CardSurface
import com.example.medac.ui.theme.NavyPrimary
import com.example.medac.ui.theme.StatusGreen
import com.example.medac.ui.theme.TextSecondary
import com.example.medac.ui.theme.BorderSubtle
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.width

@Composable
fun LoginScreen(
    isBusy: Boolean,
    error: String?,
    onLogin: (String, String) -> Unit,
    onNavigateToRegister: () -> Unit,
    onNavigateToForgot: () -> Unit,
    onClearError: () -> Unit
) {
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var showPassword by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Welcome back", style = MaterialTheme.typography.headlineMedium, color = NavyPrimary)
        Spacer(Modifier.height(8.dp))
        Text("Log in with your email and password", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
        Spacer(Modifier.height(24.dp))
        if (!error.isNullOrBlank()) {
            Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.errorContainer, modifier = Modifier.fillMaxWidth()) {
                Text(error, modifier = Modifier.padding(12.dp), color = MaterialTheme.colorScheme.onErrorContainer, style = MaterialTheme.typography.bodyMedium)
            }
            Spacer(Modifier.height(12.dp))
        }
        OutlinedTextField(
            value = email,
            onValueChange = { email = it; if (error != null) onClearError() },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Email") },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next)
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = password,
            onValueChange = { password = it; if (error != null) onClearError() },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Password") },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(onClick = { showPassword = !showPassword }) {
                    Icon(if (showPassword) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility, contentDescription = null)
                }
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done)
        )
        Spacer(Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onNavigateToForgot, modifier = Modifier.heightIn(min = 48.dp)) { Text("Forgot password?") }
        }
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = { onLogin(email.trim(), password) },
            enabled = !isBusy && email.isNotBlank() && password.isNotBlank(),
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
            shape = RoundedCornerShape(14.dp)
        ) { Text(if (isBusy) "Please wait..." else "Log in") }
        Spacer(Modifier.height(12.dp))
        TextButton(onClick = onNavigateToRegister, modifier = Modifier.heightIn(min = 48.dp)) {
            Text("Don't have an account? Create account")
        }
    }
}

@Composable
fun RegisterScreen(
    isBusy: Boolean,
    error: String?,
    onRegister: (String, String) -> Unit,
    onNavigateToLogin: () -> Unit,
    onClearError: () -> Unit
) {
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var confirm by rememberSaveable { mutableStateOf("") }
    var localError by rememberSaveable { mutableStateOf<String?>(null) }
    var showPassword by rememberSaveable { mutableStateOf(false) }
    val displayError = localError ?: error

    val hasLength = password.length >= 12
    val hasUpper = password.any { it.isUpperCase() }
    val hasLower = password.any { it.isLowerCase() }
    val hasDigit = password.any { it.isDigit() }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Create account", style = MaterialTheme.typography.headlineMedium, color = NavyPrimary)
        Spacer(Modifier.height(8.dp))
        Text("Create your account — no email verification required", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
        Spacer(Modifier.height(24.dp))
        if (!displayError.isNullOrBlank()) {
            Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.errorContainer, modifier = Modifier.fillMaxWidth()) {
                Text(displayError, modifier = Modifier.padding(12.dp), color = MaterialTheme.colorScheme.onErrorContainer, style = MaterialTheme.typography.bodyMedium)
            }
            Spacer(Modifier.height(12.dp))
        }
        OutlinedTextField(
            value = email,
            onValueChange = { email = it; localError = null; if (error != null) onClearError() },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Email") },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next)
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = password,
            onValueChange = { password = it; localError = null; if (error != null) onClearError() },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Password") },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(onClick = { showPassword = !showPassword }) {
                    Icon(if (showPassword) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility, contentDescription = null)
                }
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
        )
        Spacer(Modifier.height(8.dp))
        Surface(shape = RoundedCornerShape(14.dp), color = CardSurface, border = BorderStroke(0.5.dp, BorderSubtle), modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Password must have at least 12 characters", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                PasswordCheckRow("At least 12 characters", hasLength)
                PasswordCheckRow("Uppercase letter", hasUpper)
                PasswordCheckRow("Lowercase letter", hasLower)
                PasswordCheckRow("Number", hasDigit)
            }
        }
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = confirm,
            onValueChange = { confirm = it; localError = null },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Confirm password") },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done)
        )
        Spacer(Modifier.height(20.dp))
        Button(
            onClick = {
                if (!email.contains("@") || !email.contains(".")) { localError = "Enter a valid email address"; return@Button }
                if (password.length < 12) { localError = "Password must be at least 12 characters"; return@Button }
                if (password != confirm) { localError = "Passwords do not match"; return@Button }
                onRegister(email.trim(), password)
            },
            enabled = !isBusy && email.isNotBlank() && password.isNotBlank() && confirm.isNotBlank(),
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
            shape = RoundedCornerShape(14.dp)
        ) { Text(if (isBusy) "Please wait..." else "Create account") }
        Spacer(Modifier.height(12.dp))
        TextButton(onClick = onNavigateToLogin, modifier = Modifier.heightIn(min = 48.dp)) {
            Text("Already have an account? Log in")
        }
    }
}

@Composable
private fun PasswordCheckRow(label: String, ok: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(Icons.Outlined.CheckCircle, contentDescription = null, tint = if (ok) StatusGreen else TextSecondary, modifier = androidx.compose.ui.Modifier.width(16.dp).height(16.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = if (ok) StatusGreen else TextSecondary)
    }
}

@Composable
fun MfaScreen(
    isBusy: Boolean,
    error: String?,
    onVerify: (String, Boolean) -> Unit,
    onBack: () -> Unit
) {
    var code by rememberSaveable { mutableStateOf("") }
    var useRecovery by rememberSaveable { mutableStateOf(false) }
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Two-factor code", style = MaterialTheme.typography.headlineMedium, color = NavyPrimary)
        Spacer(Modifier.height(8.dp))
        Text(if (useRecovery) "Enter a recovery code" else "Enter the 6-digit code from your authenticator app", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
        Spacer(Modifier.height(24.dp))
        if (!error.isNullOrBlank()) {
            Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.errorContainer, modifier = Modifier.fillMaxWidth()) {
                Text(error, modifier = Modifier.padding(12.dp), color = MaterialTheme.colorScheme.onErrorContainer, style = MaterialTheme.typography.bodyMedium)
            }
            Spacer(Modifier.height(12.dp))
        }
        OutlinedTextField(value = code, onValueChange = { code = it }, modifier = Modifier.fillMaxWidth(), label = { Text(if (useRecovery) "Recovery code" else "MFA code") }, singleLine = true, shape = RoundedCornerShape(14.dp), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword))
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = { useRecovery = !useRecovery }, modifier = Modifier.heightIn(min = 48.dp)) { Text(if (useRecovery) "Use authenticator code" else "Use a recovery code") }
        Spacer(Modifier.height(16.dp))
        Button(onClick = { onVerify(code.trim(), useRecovery) }, enabled = !isBusy && code.isNotBlank(), modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp), shape = RoundedCornerShape(14.dp)) { Text(if (isBusy) "Verifying..." else "Verify") }
        Spacer(Modifier.height(12.dp))
        TextButton(onClick = onBack, modifier = Modifier.heightIn(min = 48.dp)) { Text("Back") }
    }
}

@Composable
fun ForgotPasswordScreen(
    isBusy: Boolean,
    error: String?,
    sent: Boolean,
    onSend: (String) -> Unit,
    onBack: () -> Unit
) {
    var email by rememberSaveable { mutableStateOf("") }
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Reset password", style = MaterialTheme.typography.headlineMedium, color = NavyPrimary)
        Spacer(Modifier.height(8.dp))
        Text("Enter your email and we'll send a reset link if that address exists.", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
        Spacer(Modifier.height(24.dp))
        if (sent) {
            Surface(shape = RoundedCornerShape(14.dp), color = CardSurface, border = BorderStroke(0.5.dp, BorderSubtle), modifier = Modifier.fillMaxWidth()) {
                Text("If that address exists, a reset link is on its way.", modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.bodyLarge, color = TextSecondary)
            }
            Spacer(Modifier.height(16.dp))
        }
        if (!error.isNullOrBlank()) {
            Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.errorContainer, modifier = Modifier.fillMaxWidth()) {
                Text(error, modifier = Modifier.padding(12.dp), color = MaterialTheme.colorScheme.onErrorContainer, style = MaterialTheme.typography.bodyMedium)
            }
            Spacer(Modifier.height(12.dp))
        }
        OutlinedTextField(value = email, onValueChange = { email = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Email") }, singleLine = true, shape = RoundedCornerShape(14.dp), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email))
        Spacer(Modifier.height(16.dp))
        Button(onClick = { onSend(email.trim()) }, enabled = !isBusy && email.isNotBlank(), modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp), shape = RoundedCornerShape(14.dp)) { Text(if (isBusy) "Sending..." else "Send reset link") }
        Spacer(Modifier.height(12.dp))
        TextButton(onClick = onBack, modifier = Modifier.heightIn(min = 48.dp)) { Text("Back to login") }
    }
}
