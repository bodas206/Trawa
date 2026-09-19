package com.example.trawa.ui.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.Modifier
import com.example.trawa.core.di.TrawaContainer
import com.example.trawa.ui.auth.AuthStateCheckScreen
import com.example.trawa.ui.auth.AuthUiState
import com.example.trawa.ui.auth.AuthViewModel
import com.example.trawa.ui.auth.LoginScreen
import com.example.trawa.ui.auth.TrawaSplashScreen
import com.example.trawa.ui.chat.ChatScreen
import com.example.trawa.ui.chat.ChatViewModel
import com.example.trawa.ui.profile.ProfileScreen
import com.example.trawa.ui.profile.ProfileViewModel
import com.example.trawa.ui.sidebar.TrawaSidebar
import kotlinx.coroutines.launch

enum class TrawaScreen {
  LOGIN,
  CHAT,
  PROFILE
}

@Composable
fun TrawaNavHost(
  container: TrawaContainer,
  authViewModel: AuthViewModel = remember { AuthViewModel(container.authRepository) },
  chatViewModel: ChatViewModel = remember {
    ChatViewModel(
      container.chatRepository,
      container.attachmentRepository,
      container.voiceService
    )
  },
  profileViewModel: ProfileViewModel = remember { ProfileViewModel(container.profileRepository) },
  modifier: Modifier = Modifier
) {
  val authState by authViewModel.uiState.collectAsState()
  val conversations by chatViewModel.conversations.collectAsState()
  val activeConvId by chatViewModel.activeConversationId.collectAsState()

  val context = LocalContext.current
  val keyboardController = LocalSoftwareKeyboardController.current
  val focusManager = LocalFocusManager.current
  val launchPrefs = remember { context.getSharedPreferences("trawa_boot", android.content.Context.MODE_PRIVATE) }
  var currentScreen by rememberSaveable { mutableStateOf(TrawaScreen.CHAT) }
  var splashVisible by rememberSaveable {
    mutableStateOf(!launchPrefs.getBoolean("initial_brand_reveal_seen", false))
  }

  if (splashVisible) {
    TrawaSplashScreen {
      launchPrefs.edit().putBoolean("initial_brand_reveal_seen", true).apply()
      splashVisible = false
    }
    return
  }
  val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
  val coroutineScope = rememberCoroutineScope()

  // Opening the drawer by button OR swipe must dismiss the IME/focus first.
  // This prevents the keyboard from remaining above the navigation drawer.
  LaunchedEffect(drawerState.currentValue) {
    if (drawerState.isOpen) {
      keyboardController?.hide()
      focusManager.clearFocus(force = true)
    }
  }

  // Handle Authentication State transitions
  when (authState) {
    is AuthUiState.CheckingSession -> {
      AuthStateCheckScreen()
      return
    }
    is AuthUiState.Unauthenticated, is AuthUiState.Error, is AuthUiState.Loading -> {
      LaunchedEffect(authState) { chatViewModel.resetForAuthentication() }
      LoginScreen(
        viewModel = authViewModel,
        onLoginSuccess = {
          currentScreen = TrawaScreen.CHAT
        }
      )
      return
    }
    is AuthUiState.Authenticated -> {
      // Chat state is initialized only after a real authenticated session exists.
      LaunchedEffect(authState) { chatViewModel.initialize() }
    }
  }

  AnimatedContent(
    targetState = currentScreen,
    transitionSpec = {
      if (targetState == TrawaScreen.PROFILE) {
        (slideInHorizontally(
          initialOffsetX = { it / 3 },
          animationSpec = tween(280, easing = FastOutSlowInEasing)
        ) + fadeIn(animationSpec = tween(280)))
          .togetherWith(
            slideOutHorizontally(
              targetOffsetX = { -it / 3 },
              animationSpec = tween(240, easing = FastOutSlowInEasing)
            ) + fadeOut(animationSpec = tween(240))
          )
      } else {
        (slideInHorizontally(
          initialOffsetX = { -it / 3 },
          animationSpec = tween(280, easing = FastOutSlowInEasing)
        ) + fadeIn(animationSpec = tween(280)))
          .togetherWith(
            slideOutHorizontally(
              targetOffsetX = { it / 3 },
              animationSpec = tween(240, easing = FastOutSlowInEasing)
            ) + fadeOut(animationSpec = tween(240))
          )
      }
    },
    label = "screen_transition",
    modifier = modifier.fillMaxSize()
  ) { screen ->
    when (screen) {
      TrawaScreen.PROFILE -> {
        BackHandler {
          currentScreen = TrawaScreen.CHAT
        }
        ProfileScreen(
          profileViewModel = profileViewModel,
          authViewModel = authViewModel,
          onNavigateBack = { currentScreen = TrawaScreen.CHAT },
          modifier = Modifier.fillMaxSize()
        )
      }
      TrawaScreen.CHAT, TrawaScreen.LOGIN -> {
        // Handle back button on Chat screen if drawer is open
        if (drawerState.isOpen) {
          BackHandler {
            coroutineScope.launch { drawerState.close() }
          }
        }

        // Main Chat Screen inside Sidebar Drawer
        ModalNavigationDrawer(
          drawerState = drawerState,
          drawerContent = {
            TrawaSidebar(
              conversations = conversations,
              activeConversationId = activeConvId,
              onSelectConversation = { convId ->
                chatViewModel.selectConversation(convId)
              },
              onNewChat = {
                chatViewModel.startNewChat()
              },
              onNavigateToProfile = {
                currentScreen = TrawaScreen.PROFILE
              },
              onCloseDrawer = {
                coroutineScope.launch { drawerState.close() }
              }
            )
          },
          modifier = Modifier.fillMaxSize()
        ) {
          ChatScreen(
            viewModel = chatViewModel,
            onOpenSidebar = {
              coroutineScope.launch { drawerState.open() }
            },
            onNavigateToProfile = {
              currentScreen = TrawaScreen.PROFILE
            }
          )
        }
      }
    }
  }
}
