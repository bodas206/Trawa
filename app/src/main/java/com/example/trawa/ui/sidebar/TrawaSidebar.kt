package com.example.trawa.ui.sidebar

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.trawa.domain.model.Conversation
import com.example.trawa.ui.components.TrawaHeaderBrand
import com.example.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun TrawaSidebar(
  conversations: List<Conversation>,
  activeConversationId: String?,
  onSelectConversation: (String) -> Unit,
  onNewChat: () -> Unit,
  onNavigateToProfile: () -> Unit,
  onCloseDrawer: () -> Unit,
  modifier: Modifier = Modifier
) {
  val now = System.currentTimeMillis()
  val oneDayMs = 24 * 60 * 60 * 1000L
  val twoDaysMs = 2 * oneDayMs

  // Group conversations by Today, Yesterday, Older
  val normalConversations = conversations.filter { !it.isTemporary }
  val todayList = normalConversations.filter { now - it.createdAt < oneDayMs }
  val yesterdayList = normalConversations.filter { it.createdAt in (now - twoDaysMs) until (now - oneDayMs) }
  val olderList = normalConversations.filter { now - it.createdAt >= twoDaysMs }

  ModalDrawerSheet(
    modifier = modifier
      .fillMaxHeight()
      .width(320.dp),
    drawerContainerColor = MaterialTheme.colorScheme.surface,
    drawerContentColor = MaterialTheme.colorScheme.onSurface,
    windowInsets = WindowInsets.safeDrawing
  ) {
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(top = 16.dp, bottom = 12.dp, start = 16.dp, end = 16.dp)
    ) {
      // Top Branding
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
      ) {
        TrawaHeaderBrand()
      }

      Spacer(modifier = Modifier.height(20.dp))

      // Primary Action: "+ New Chat"
      Button(
        onClick = {
          onNewChat()
          onCloseDrawer()
        },
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(
          containerColor = Color.White,
          contentColor = Color.Black
        ),
        modifier = Modifier
          .fillMaxWidth()
          .height(48.dp)
          .testTag("sidebar_new_chat_button")
      ) {
        Icon(
          imageVector = Icons.Default.Add,
          contentDescription = null,
          modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
          text = "New Chat",
          style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
        )
      }

      Spacer(modifier = Modifier.height(16.dp))

      HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))

      Spacer(modifier = Modifier.height(12.dp))

      // Conversation History
      LazyColumn(
        modifier = Modifier
          .weight(1f)
          .fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
      ) {
        if (todayList.isNotEmpty()) {
          item {
            SectionHeader("Today")
          }
          items(todayList, key = { it.id }) { conv ->
            ConversationRowItem(
              conversation = conv,
              isSelected = conv.id == activeConversationId,
              onClick = {
                onSelectConversation(conv.id)
                onCloseDrawer()
              }
            )
          }
        }

        if (yesterdayList.isNotEmpty()) {
          item {
            SectionHeader("Yesterday")
          }
          items(yesterdayList, key = { it.id }) { conv ->
            ConversationRowItem(
              conversation = conv,
              isSelected = conv.id == activeConversationId,
              onClick = {
                onSelectConversation(conv.id)
                onCloseDrawer()
              }
            )
          }
        }

        if (olderList.isNotEmpty()) {
          item {
            SectionHeader("Older")
          }
          items(olderList, key = { it.id }) { conv ->
            ConversationRowItem(
              conversation = conv,
              isSelected = conv.id == activeConversationId,
              onClick = {
                onSelectConversation(conv.id)
                onCloseDrawer()
              }
            )
          }
        }

        if (normalConversations.isEmpty()) {
          item {
            Box(
              modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 36.dp),
              contentAlignment = Alignment.Center
            ) {
              Text(
                text = "No saved conversations yet.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
            }
          }
        }
      }

      Spacer(modifier = Modifier.height(8.dp))
      HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
      Spacer(modifier = Modifier.height(8.dp))

      // Bottom Navigation: Chat & Profile
      Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
      ) {
        NavigationDrawerItem(
          label = { Text("Chat", fontWeight = FontWeight.Medium) },
          selected = true,
          icon = { Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = null, tint = TrawaCyan) },
          onClick = { onCloseDrawer() },
          shape = RoundedCornerShape(12.dp),
          colors = NavigationDrawerItemDefaults.colors(
            selectedContainerColor = TrawaDarkSurfaceVariant.copy(alpha = 0.7f),
            selectedTextColor = MaterialTheme.colorScheme.onSurface
          ),
          modifier = Modifier.height(48.dp)
        )

        NavigationDrawerItem(
          label = { Text("Profile & Settings", fontWeight = FontWeight.Medium) },
          selected = false,
          icon = { Icon(Icons.Default.Person, contentDescription = null) },
          onClick = {
            onCloseDrawer()
            onNavigateToProfile()
          },
          shape = RoundedCornerShape(12.dp),
          colors = NavigationDrawerItemDefaults.colors(
            unselectedContainerColor = Color.Transparent,
            unselectedTextColor = MaterialTheme.colorScheme.onSurface
          ),
          modifier = Modifier
            .height(48.dp)
            .testTag("sidebar_profile_button")
        )
      }
    }
  }
}

@Composable
private fun SectionHeader(title: String) {
  Text(
    text = title,
    style = MaterialTheme.typography.labelSmall.copy(
      fontWeight = FontWeight.Bold,
      letterSpacing = 0.8.sp
    ),
    color = MaterialTheme.colorScheme.onSurfaceVariant,
    modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)
  )
}

@Composable
private fun ConversationRowItem(
  conversation: Conversation,
  isSelected: Boolean,
  onClick: () -> Unit
) {
  val backgroundColor = if (isSelected) {
    TrawaSapphire.copy(alpha = 0.25f)
  } else {
    Color.Transparent
  }

  Surface(
    modifier = Modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(10.dp))
      .clickable(onClick = onClick)
      .testTag("conversation_item_${conversation.id}"),
    color = backgroundColor,
    shape = RoundedCornerShape(10.dp)
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 12.dp, vertical = 10.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      Text(
        text = conversation.title.ifBlank { "New Chat" },
        style = MaterialTheme.typography.bodyMedium.copy(
          fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
        ),
        color = if (isSelected) TrawaCyan else MaterialTheme.colorScheme.onSurface,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.weight(1f)
      )
    }
  }
}
