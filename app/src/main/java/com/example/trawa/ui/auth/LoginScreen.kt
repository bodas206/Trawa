package com.example.trawa.ui.auth

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.trawa.ui.components.TrawaEmblem
import com.example.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(viewModel: AuthViewModel, onLoginSuccess: () -> Unit) {
  val uiState by viewModel.uiState.collectAsState()
  var email by remember { mutableStateOf("") }
  var fullName by remember { mutableStateOf("") }
  var code by remember { mutableStateOf("") }
  var isSignUp by remember { mutableStateOf(false) }
  val focus = LocalFocusManager.current
  val scroll = rememberScrollState()

  LaunchedEffect(uiState) { if (uiState is AuthUiState.Authenticated) onLoginSuccess() }

  val awaiting = uiState as? AuthUiState.AwaitingOtp
  val loading = uiState is AuthUiState.Loading || uiState is AuthUiState.CheckingSession
  val error = (uiState as? AuthUiState.Error)?.message

  Scaffold(containerColor = MaterialTheme.colorScheme.background, contentWindowInsets = WindowInsets.safeDrawing) { pad ->
    Box(Modifier.fillMaxSize().padding(pad), contentAlignment = Alignment.Center) {
      Column(
        Modifier.fillMaxWidth().widthIn(max = 440.dp).padding(horizontal = 28.dp).verticalScroll(scroll),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
      ) {
        Spacer(Modifier.height(24.dp))
        TrawaEmblem(size = 72.dp)
        Spacer(Modifier.height(16.dp))
        Text("TRAWA", style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.ExtraBold, letterSpacing = 2.sp), color = MaterialTheme.colorScheme.onBackground)
        Spacer(Modifier.height(4.dp))
        Surface(shape = RoundedCornerShape(100.dp), color = TrawaSapphire.copy(alpha = .25f)) {
          Text("3AR V1 Pro", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp), color = TrawaCyan, modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp))
        }
        Spacer(Modifier.height(8.dp))
        Text(
          if (awaiting != null) "Enter the 6-digit code sent to your email" else if (isSignUp) "Create your TRAWA account" else "Sign in to your TRAWA account",
          style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(32.dp))

        AnimatedVisibility(error != null, enter = fadeIn(), exit = fadeOut()) {
          Surface(Modifier.fillMaxWidth().padding(bottom = 16.dp), shape = RoundedCornerShape(12.dp), color = TrawaError.copy(alpha = .15f)) {
            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
              Text(error ?: "", style = MaterialTheme.typography.bodySmall, color = TrawaError, modifier = Modifier.weight(1f))
              TextButton(onClick = viewModel::clearError) { Text("×", color = TrawaError, fontSize = 18.sp) }
            }
          }
        }

        if (awaiting == null) {
          if (isSignUp) {
            OutlinedTextField(
              value = fullName, onValueChange = { fullName = it }, label = { Text("Name") }, singleLine = true,
              keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
              modifier = Modifier.fillMaxWidth().testTag("signup_name_input"), shape = RoundedCornerShape(14.dp)
            )
            Spacer(Modifier.height(16.dp))
          }
          OutlinedTextField(
            value = email, onValueChange = { email = it; if (error != null) viewModel.clearError() },
            label = { Text("Email address") }, placeholder = { Text("name@example.com") },
            leadingIcon = { Icon(Icons.Default.Email, null, tint = MaterialTheme.colorScheme.primary) }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Done),
            modifier = Modifier.fillMaxWidth().testTag("login_email_input"), shape = RoundedCornerShape(14.dp),
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = TrawaCyan, unfocusedBorderColor = MaterialTheme.colorScheme.outline)
          )
          Spacer(Modifier.height(20.dp))
          Button(
            onClick = { focus.clearFocus(); viewModel.requestOtp(email, fullName, isSignUp) }, enabled = !loading,
            shape = RoundedCornerShape(14.dp), colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black),
            modifier = Modifier.fillMaxWidth().height(52.dp).testTag("login_submit_button")
          ) {
            if (loading) { CircularProgressIndicator(color = Color.Black, strokeWidth = 2.5.dp, modifier = Modifier.size(22.dp)); Spacer(Modifier.width(12.dp)) }
            Text(if (loading) "Sending code..." else "Send 6-digit code", fontWeight = FontWeight.Black)
          }
          Spacer(Modifier.height(10.dp))
          TextButton(onClick = { viewModel.clearError(); isSignUp = !isSignUp }, enabled = !loading) {
            Text(if (isSignUp) "Already have an account? Sign in" else "Create an account")
          }
        } else {
          Text(awaiting.email, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelLarge)
          Spacer(Modifier.height(16.dp))
          OutlinedTextField(
            value = code, onValueChange = { value -> code = value.filter(Char::isDigit).take(6); if (error != null) viewModel.clearError() },
            label = { Text("6-digit verification code") }, placeholder = { Text("123456") }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
            modifier = Modifier.fillMaxWidth().testTag("otp_code_input"), shape = RoundedCornerShape(14.dp)
          )
          Spacer(Modifier.height(20.dp))
          Button(
            onClick = { focus.clearFocus(); viewModel.verifyOtp(awaiting.email, code, if (awaiting.name.isNotBlank()) awaiting.name else fullName, awaiting.isSignUp) }, enabled = !loading,
            shape = RoundedCornerShape(14.dp), colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black),
            modifier = Modifier.fillMaxWidth().height(52.dp).testTag("otp_verify_button")
          ) {
            if (loading) { CircularProgressIndicator(color = Color.Black, strokeWidth = 2.5.dp, modifier = Modifier.size(22.dp)); Spacer(Modifier.width(12.dp)) }
            Text(if (loading) "Verifying..." else "Verify and continue", fontWeight = FontWeight.Black)
          }
          Spacer(Modifier.height(6.dp))
          TextButton(onClick = { viewModel.resendOtp(awaiting.email) }, enabled = !loading) { Text("Resend code") }
          TextButton(onClick = { viewModel.useDifferentEmail() }, enabled = !loading) { Text("Use a different email") }
        }
        Spacer(Modifier.height(32.dp))
      }
    }
  }
}
