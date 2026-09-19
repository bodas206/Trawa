package com.example.trawa.ui.chat

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.trawa.domain.model.Attachment
import com.example.trawa.domain.model.AttachmentType
import com.example.trawa.domain.model.UploadState
import com.example.trawa.domain.model.VoiceSessionState
import com.example.trawa.ui.chat.components.*
import com.example.trawa.ui.components.TrawaHeaderBrand
import com.example.ui.theme.*
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
  viewModel: ChatViewModel,
  onOpenSidebar: () -> Unit,
  onNavigateToProfile: () -> Unit,
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current
  val keyboardController = LocalSoftwareKeyboardController.current
  val focusManager = LocalFocusManager.current
  val messages by viewModel.messages.collectAsState()
  val composerText by viewModel.composerText.collectAsState()
  val isStreaming by viewModel.isStreaming.collectAsState()
  val isTemporaryChat by viewModel.isTemporaryChat.collectAsState()
  val stagedAttachments by viewModel.stagedAttachments.collectAsState()
  val voiceState by viewModel.voiceState.collectAsState()
  val liveTranscript by viewModel.liveTranscript.collectAsState()
  val isCallSheetVisible by viewModel.isCallSheetVisible.collectAsState()

  val listState = rememberLazyListState()
  var showAttachmentMenu by remember { mutableStateOf(false) }
  var showInAppCamera by remember { mutableStateOf(false) }

  val isImeOpen = WindowInsets.ime.getBottom(LocalDensity.current) > 0

  // Scroll to bottom on new message or keyboard opened
  LaunchedEffect(messages.size, isImeOpen) {
    if (messages.isNotEmpty()) {
      listState.scrollToItem(messages.size - 1)
    }
  }

  // Follow streaming response smoothly only if user is near bottom
  LaunchedEffect(messages.lastOrNull()?.content?.length) {
    if (messages.isNotEmpty() && !listState.canScrollForward) {
      listState.scrollToItem(messages.size - 1)
    }
  }

  // Native Photo Picker (multiple photos)
  val photoPickerLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.PickMultipleVisualMedia()
  ) { uris: List<Uri> ->
    uris.forEach { uri ->
      viewModel.addAttachmentUri(context, uri, AttachmentType.IMAGE)
    }
  }

  // Native File Picker (Storage Access Framework - multiple documents)
  val filePickerLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.OpenMultipleDocuments()
  ) { uris: List<Uri> ->
    uris.forEach { uri ->
      viewModel.addAttachmentUri(context, uri, AttachmentType.FILE)
    }
  }

  // Microphone Permission Launcher
  val micPermissionLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.RequestPermission()
  ) { isGranted ->
    if (isGranted) {
      viewModel.startSpeechInput()
    } else {
      Toast.makeText(context, "Microphone permission is required for voice input.", Toast.LENGTH_LONG).show()
    }
  }

  // Camera Permission Launcher
  val cameraPermissionLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.RequestPermission()
  ) { isGranted ->
    if (isGranted) {
      showInAppCamera = true
    } else {
      Toast.makeText(context, "Camera permission is required to capture photos.", Toast.LENGTH_LONG).show()
    }
  }

  Scaffold(
    topBar = {
      TopAppBar(
        title = {},
        navigationIcon = {
          // Circular Menu Button with three horizontal lines inside
          IconButton(
            onClick = {
              keyboardController?.hide()
              focusManager.clearFocus(force = true)
              showAttachmentMenu = false
              onOpenSidebar()
            },
            modifier = Modifier
              .padding(start = 8.dp)
              .size(40.dp)
              .clip(CircleShape)
              .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
              .testTag("top_bar_menu_button")
          ) {
            Icon(
              imageVector = Icons.Default.Menu,
              contentDescription = "Open navigation menu",
              tint = MaterialTheme.colorScheme.onSurface,
              modifier = Modifier.size(20.dp)
            )
          }
        },
        actions = {
          // Top-right Temporary Chat button
          IconButton(
            onClick = { viewModel.toggleTemporaryChat() },
            modifier = Modifier
              .padding(end = 8.dp)
              .size(40.dp)
              .clip(CircleShape)
              .background(
                if (isTemporaryChat) Color.White.copy(alpha = 0.2f)
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
              )
              .testTag("top_bar_temporary_chat_button")
          ) {
            Icon(
              imageVector = if (isTemporaryChat) Icons.Default.ChatBubble else Icons.Default.ChatBubbleOutline,
              contentDescription = if (isTemporaryChat) "Temporary Chat Active (Click to exit)" else "Start Temporary Chat",
              tint = if (isTemporaryChat) Color.White else MaterialTheme.colorScheme.onSurface,
              modifier = Modifier.size(20.dp)
            )
          }
        },
        colors = TopAppBarDefaults.topAppBarColors(
          containerColor = MaterialTheme.colorScheme.background
        )
      )
    },
    containerColor = MaterialTheme.colorScheme.background,
    contentWindowInsets = WindowInsets.statusBars,
    modifier = modifier.fillMaxSize()
  ) { innerPadding ->
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(top = innerPadding.calculateTopPadding())
        .imePadding()
    ) {
      // Temporary Chat Mode visual indicator badge
      AnimatedVisibility(
        visible = isTemporaryChat,
        enter = fadeIn(),
        exit = fadeOut()
      ) {
        Surface(
          color = MaterialTheme.colorScheme.surfaceVariant,
          modifier = Modifier.fillMaxWidth()
        ) {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
          ) {
            Icon(
              imageVector = Icons.Default.LockClock,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.onSurface,
              modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
              text = "Temporary Chat Active — Messages & memory will not be saved",
              style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
              color = MaterialTheme.colorScheme.onSurface
            )
          }
        }
      }

      // Conversation Area: Scrollable messages or Empty Chat State
      Box(
        modifier = Modifier
          .weight(1f)
          .fillMaxWidth()
      ) {
        if (messages.isEmpty()) {
          // Deep clean empty canvas, focus kept on composer
        } else {
          LazyColumn(
            state = listState,
            modifier = Modifier
              .fillMaxSize()
              .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            contentPadding = PaddingValues(vertical = 12.dp)
          ) {
            items(messages, key = { it.id }) { msg ->
              ChatMessageItem(
                message = msg,
                isStreaming = isStreaming,
                onEditMessage = viewModel::editMessage,
                onRegenerate = viewModel::regenerateResponse,
                onReadAloud = viewModel::readAloud
              )
            }
          }
        }
      }

      // 3-Icon Attachment Action Strip floating directly above the + button
      AttachmentActionStrip(
        visible = showAttachmentMenu,
        onDismiss = { showAttachmentMenu = false },
        onPickCamera = {
          val hasPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA
          ) == PackageManager.PERMISSION_GRANTED
          if (hasPermission) {
            showInAppCamera = true
          } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
          }
        },
        onPickImages = {
          photoPickerLauncher.launch(
            androidx.activity.result.PickVisualMediaRequest(
              ActivityResultContracts.PickVisualMedia.ImageOnly
            )
          )
        },
        onPickFiles = {
          filePickerLauncher.launch(arrayOf("*/*"))
        }
      )

      // ComposerView placed directly at the bottom of the Column
      ComposerView(
        text = composerText,
        onTextChanged = viewModel::onComposerTextChanged,
        onSend = {
          showAttachmentMenu = false
          viewModel.sendMessage()
        },
        onOpenAttachmentMenu = { showAttachmentMenu = !showAttachmentMenu },
        onMicClick = {
          val hasPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
          ) == PackageManager.PERMISSION_GRANTED
          if (hasPermission) {
            if (voiceState == VoiceSessionState.LISTENING) {
              viewModel.stopSpeechInput()
            } else {
              viewModel.startSpeechInput()
            }
          } else {
            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
          }
        },
        onCallClick = {
          val hasPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
          ) == PackageManager.PERMISSION_GRANTED
          if (hasPermission) {
            viewModel.openCallSheet()
          } else {
            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
          }
        },
        voiceState = voiceState,
        stagedAttachments = stagedAttachments,
        onRemoveAttachment = viewModel::removeAttachment
      )
    }
  }

  // In-App Native Camera with Flash and Instant Capture
  if (showInAppCamera) {
    TrawaInAppCamera(
      onDismiss = { showInAppCamera = false },
      onPhotoCaptured = { uri ->
        viewModel.addAttachmentUri(context, uri, AttachmentType.IMAGE)
        showInAppCamera = false
      }
    )
  }

  // Voice Call Sheet Modal
  if (isCallSheetVisible) {
    VoiceCallSheet(
      voiceState = voiceState,
      liveTranscript = liveTranscript,
      onInterrupt = { viewModel.voiceService.interrupt() },
      onClose = { viewModel.closeCallSheet() },
      onToggleMic = {
        if (voiceState == VoiceSessionState.LISTENING) {
          viewModel.voiceService.stopListening()
        } else {
          viewModel.voiceService.startListening { text ->
            if (text.isNotBlank()) viewModel.sendMessage(text)
          }
        }
      }
    )
  }
}
