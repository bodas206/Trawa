package com.example.trawa.ui.auth

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.trawa.ui.components.TrawaEmblem
import com.example.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
  viewModel: AuthViewModel,
  onLoginSuccess: () -> Unit
) {
  val uiState by viewModel.uiState.collectAsState()
  val forgotMessage by viewModel.forgotPasswordMessage.collectAsState()
  val isResetting by viewModel.isResettingPassword.collectAsState()

  var email by remember { mutableStateOf("") }
  var fullName by remember { mutableStateOf("") }
  var isSignUp by remember { mutableStateOf(false) }
  var password by remember { mutableStateOf("") }
  var passwordVisible by remember { mutableStateOf(false) }
  var showForgotDialog by remember { mutableStateOf(false) }
  var forgotEmail by remember { mutableStateOf("") }

  val focusManager = LocalFocusManager.current
  val scrollState = rememberScrollState()

  LaunchedEffect(uiState) {
    if (uiState is AuthUiState.Authenticated) {
      onLoginSuccess()
    }
  }

  val isLoading = uiState is AuthUiState.Loading || uiState is AuthUiState.CheckingSession
  val errorMessage = (uiState as? AuthUiState.Error)?.message

  Scaffold(
    containerColor = MaterialTheme.colorScheme.background,
    contentWindowInsets = WindowInsets.safeDrawing
  ) { innerPadding ->
    Box(
      modifier = Modifier
        .fillMaxSize()
        .padding(innerPadding),
      contentAlignment = Alignment.Center
    ) {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .widthIn(max = 440.dp)
          .padding(horizontal = 28.dp)
          .verticalScroll(scrollState),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
      ) {
        Spacer(modifier = Modifier.height(24.dp))

        // TRAWA Emblem
        TrawaEmblem(size = 72.dp)

        Spacer(modifier = Modifier.height(16.dp))

        Text(
          text = "TRAWA",
          style = MaterialTheme.typography.headlineMedium.copy(
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 2.sp
          ),
          color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(modifier = Modifier.height(4.dp))

        Surface(
          shape = RoundedCornerShape(100.dp),
          color = TrawaSapphire.copy(alpha = 0.25f),
          border = null
        ) {
          Text(
            text = "3AR V1 Pro",
            style = MaterialTheme.typography.labelMedium.copy(
              fontWeight = FontWeight.SemiBold,
              letterSpacing = 1.sp
            ),
            color = TrawaCyan,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp)
          )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
          text = if (isSignUp) "Create your TRAWA account" else "Sign in to your TRAWA account",
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(32.dp))

        // Error message card
        AnimatedVisibility(
          visible = errorMessage != null,
          enter = fadeIn(),
          exit = fadeOut()
        ) {
          Surface(
            modifier = Modifier
              .fillMaxWidth()
              .padding(bottom = 16.dp),
            shape = RoundedCornerShape(12.dp),
            color = TrawaError.copy(alpha = 0.15f),
            border = null
          ) {
            Row(
              modifier = Modifier.padding(14.dp),
              verticalAlignment = Alignment.CenterVertically
            ) {
              Text(
                text = errorMessage ?: "",
                style = MaterialTheme.typography.bodySmall,
                color = TrawaError,
                modifier = Modifier.weight(1f)
              )
              IconButton(
                onClick = { viewModel.clearError() },
                modifier = Modifier.size(24.dp)
              ) {
                Text("×", color = TrawaError, fontSize = 18.sp)
              }
            }
          }
        }

        AnimatedVisibility(visible = isSignUp) {
          Column {
            OutlinedTextField(
              value = fullName,
              onValueChange = { fullName = it },
              label = { Text("Name") },
              singleLine = true,
              shape = RoundedCornerShape(14.dp),
              modifier = Modifier.fillMaxWidth().testTag("signup_name_input")
            )
            Spacer(modifier = Modifier.height(16.dp))
          }
        }

        // Email field
        OutlinedTextField(
          value = email,
          onValueChange = {
            email = it
            if (errorMessage != null) viewModel.clearError()
          },
          label = { Text("Email address") },
          placeholder = { Text("name@example.com") },
          leadingIcon = {
            Icon(
              imageVector = Icons.Default.Email,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.primary
            )
          },
          singleLine = true,
          keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Email,
            imeAction = ImeAction.Next
          ),
          keyboardActions = KeyboardActions(
            onNext = { focusManager.moveFocus(FocusDirection.Down) }
          ),
          shape = RoundedCornerShape(14.dp),
          colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = TrawaCyan,
            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
          ),
          modifier = Modifier
            .fillMaxWidth()
            .testTag("login_email_input")
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Password field
        OutlinedTextField(
          value = password,
          onValueChange = {
            password = it
            if (errorMessage != null) viewModel.clearError()
          },
          label = { Text("Password") },
          placeholder = { Text("••••••••") },
          leadingIcon = {
            Icon(
              imageVector = Icons.Default.Lock,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.primary
            )
          },
          trailingIcon = {
            IconButton(
              onClick = { passwordVisible = !passwordVisible },
              modifier = Modifier.testTag("toggle_password_visibility")
            ) {
              Icon(
                imageVector = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                contentDescription = if (passwordVisible) "Hide password" else "Show password",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
              )
            }
          },
          visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
          singleLine = true,
          keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Password,
            imeAction = ImeAction.Done
          ),
          keyboardActions = KeyboardActions(
            onDone = {
              focusManager.clearFocus()
              if (!isLoading) { if (isSignUp) viewModel.signUp(email, password, fullName) else viewModel.login(email, password) }
            }
          ),
          shape = RoundedCornerShape(14.dp),
          colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = TrawaCyan,
            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
          ),
          modifier = Modifier
            .fillMaxWidth()
            .testTag("login_password_input")
        )

        // Forgot password button
        if (!isSignUp) Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
          horizontalArrangement = Arrangement.End
        ) {
          TextButton(
            onClick = {
              forgotEmail = email
              showForgotDialog = true
            },
            modifier = Modifier.testTag("forgot_password_button")
          ) {
            Text(
              text = "Forgot password?",
              style = MaterialTheme.typography.labelMedium,
              color = TrawaElectricBlue
            )
          }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Primary Login Button
        Button(
          onClick = {
            focusManager.clearFocus()
            if (isSignUp) viewModel.signUp(email, password, fullName) else viewModel.login(email, password)
          },
          enabled = !isLoading,
          shape = RoundedCornerShape(14.dp),
          colors = ButtonDefaults.buttonColors(
            containerColor = Color.White,
            contentColor = Color.Black,
            disabledContainerColor = Color.White.copy(alpha = 0.35f),
            disabledContentColor = Color.Black.copy(alpha = 0.5f)
          ),
          modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .testTag("login_submit_button")
        ) {
          if (isLoading) {
            CircularProgressIndicator(
              color = Color.Black,
              strokeWidth = 2.5.dp,
              modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
              text = "Authenticating...",
              color = Color.Black,
              style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Black)
            )
          } else {
            Text(
              text = if (isSignUp) "Create account" else "Log in",
              color = Color.Black,
              style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Black)
            )
          }
        }

        Spacer(modifier = Modifier.height(10.dp))
        TextButton(
          onClick = {
            viewModel.clearError()
            isSignUp = !isSignUp
          },
          enabled = !isLoading
        ) {
          Text(if (isSignUp) "Already have an account? Log in" else "Create an account")
        }

        Spacer(modifier = Modifier.height(32.dp))
      }
    }
  }

  // Forgot password dialog
  if (showForgotDialog) {
    AlertDialog(
      onDismissRequest = { showForgotDialog = false },
      title = {
        Text("Reset Password", style = MaterialTheme.typography.titleLarge)
      },
      text = {
        Column {
          Text(
            "Enter your registered email address. We will verify with the TRAWA authentication backend.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
          Spacer(modifier = Modifier.height(16.dp))
          OutlinedTextField(
            value = forgotEmail,
            onValueChange = { forgotEmail = it },
            label = { Text("Email") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
          )
          if (forgotMessage != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
              text = forgotMessage ?: "",
              style = MaterialTheme.typography.bodySmall,
              color = if (forgotMessage?.contains("sent", ignoreCase = true) == true) TrawaSuccess else TrawaError
            )
          }
        }
      },
      confirmButton = {
        Button(
          onClick = { viewModel.resetPassword(forgotEmail) },
          enabled = !isResetting && forgotEmail.isNotBlank(),
          colors = ButtonDefaults.buttonColors(containerColor = TrawaSapphire)
        ) {
          if (isResetting) {
            CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
          } else {
            Text("Send Instructions")
          }
        }
      },
      dismissButton = {
        TextButton(onClick = { showForgotDialog = false }) {
          Text("Cancel")
        }
      },
      containerColor = MaterialTheme.colorScheme.surface,
      shape = RoundedCornerShape(20.dp)
    )
  }
}
