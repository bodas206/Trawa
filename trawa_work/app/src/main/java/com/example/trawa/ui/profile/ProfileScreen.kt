package com.example.trawa.ui.profile

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.trawa.ui.auth.AuthViewModel
import com.example.trawa.ui.components.TrawaEmblem
import com.example.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
  profileViewModel: ProfileViewModel,
  authViewModel: AuthViewModel,
  onNavigateBack: () -> Unit,
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current
  val personalization by profileViewModel.personalization.collectAsState()
  val memories by profileViewModel.memories.collectAsState()
  val isDarkTheme by profileViewModel.isDarkTheme.collectAsState()
  val feedback by profileViewModel.feedbackMessage.collectAsState()
  var instructions by remember(personalization.customInstructions) { mutableStateOf(personalization.customInstructions) }
  var language by remember(personalization.preferredLanguage) { mutableStateOf(personalization.preferredLanguage) }
  var showMemoryDialog by remember { mutableStateOf(false) }
  var newMemory by remember { mutableStateOf("") }

  LaunchedEffect(feedback) { feedback?.let { Toast.makeText(context, it, Toast.LENGTH_SHORT).show(); profileViewModel.clearFeedback() } }

  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text("Profile", fontWeight = FontWeight.SemiBold) },
        navigationIcon = { IconButton(onClick = onNavigateBack, modifier = Modifier.testTag("profile_back_button")) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
      )
    },
    containerColor = MaterialTheme.colorScheme.background,
    contentWindowInsets = WindowInsets.safeDrawing,
    modifier = modifier.fillMaxSize()
  ) { padding ->
    Column(
      Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 12.dp),
      verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
      Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surface, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha=.45f)), modifier=Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment=Alignment.CenterVertically) {
          TrawaEmblem(44.dp)
          Spacer(Modifier.width(12.dp))
          Column(Modifier.weight(1f)) {
            Text("TRAWA", style=MaterialTheme.typography.titleLarge, fontWeight=FontWeight.SemiBold)
            Text("Your account and AI preferences", style=MaterialTheme.typography.bodySmall, color=MaterialTheme.colorScheme.onSurfaceVariant)
          }
          IconButton(onClick={ { authViewModel.logout(); onNavigateBack() } }, modifier=Modifier.testTag("profile_logout_button")) { Icon(Icons.AutoMirrored.Filled.ExitToApp,"Log out", tint=TrawaError) }
        }
      }

      Surface(shape=RoundedCornerShape(18.dp), color=MaterialTheme.colorScheme.surface, border=BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha=.45f)), modifier=Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
          Row(verticalAlignment=Alignment.CenterVertically) { Icon(Icons.Default.Tune,null,tint=TrawaCyan); Spacer(Modifier.width(10.dp)); Text("Personalization", style=MaterialTheme.typography.titleMedium, fontWeight=FontWeight.SemiBold) }
          Spacer(Modifier.height(10.dp))
          OutlinedTextField(instructions,{instructions=it},label={Text("How should TRAWA respond?")},placeholder={Text("Language, tone, formatting, preferences…")},modifier=Modifier.fillMaxWidth().testTag("personalization_instructions_input"),minLines=3,maxLines=7,shape=RoundedCornerShape(14.dp))
          Spacer(Modifier.height(10.dp))
          Row(horizontalArrangement=Arrangement.spacedBy(8.dp),modifier=Modifier.fillMaxWidth()) {
            listOf("Auto (Default)","Egyptian Arabic","English").forEach { option -> FilterChip(selected=language==option,onClick={language=option},label={Text(option)},modifier=Modifier.weight(1f)) }
          }
          Spacer(Modifier.height(10.dp))
          Button(onClick={profileViewModel.updatePersonalization(instructions,language,personalization.responseTone)},modifier=Modifier.fillMaxWidth().testTag("save_personalization_button"),shape=RoundedCornerShape(12.dp)) { Text("Save") }
        }
      }

      Surface(shape=RoundedCornerShape(18.dp), color=MaterialTheme.colorScheme.surface, border=BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha=.45f)), modifier=Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
          Row(verticalAlignment=Alignment.CenterVertically) { Icon(Icons.Default.Psychology,null,tint=TrawaCyan); Spacer(Modifier.width(10.dp)); Text("Memory", style=MaterialTheme.typography.titleMedium, fontWeight=FontWeight.SemiBold); Spacer(Modifier.weight(1f)); IconButton(onClick={showMemoryDialog=true}){Icon(Icons.Default.Add,"Add memory",tint=TrawaCyan)} }
          Text("Facts you explicitly save are sent to the server and used in future non-temporary chats.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
          Spacer(Modifier.height(10.dp))
          if(memories.isEmpty()) Text("No saved memories.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
          else memories.forEach { memory ->
            Row(Modifier.fillMaxWidth().padding(vertical=5.dp),verticalAlignment=Alignment.CenterVertically){Text(memory.content,Modifier.weight(1f),style=MaterialTheme.typography.bodyMedium);IconButton(onClick={profileViewModel.deleteMemory(memory.id)}){Icon(Icons.Default.Delete,"Delete",tint=MaterialTheme.colorScheme.onSurfaceVariant)}}
          }
        }
      }

      Surface(shape=RoundedCornerShape(18.dp), color=MaterialTheme.colorScheme.surface, border=BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha=.45f)), modifier=Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
          Text("Appearance",style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.SemiBold)
          Spacer(Modifier.height(10.dp))
          Row(horizontalArrangement=Arrangement.spacedBy(10.dp),modifier=Modifier.fillMaxWidth()) {
            FilterChip(selected=isDarkTheme,onClick={profileViewModel.toggleTheme(true)},label={Text("Dark")},leadingIcon={Icon(Icons.Default.DarkMode,null)},modifier=Modifier.weight(1f))
            FilterChip(selected=!isDarkTheme,onClick={profileViewModel.toggleTheme(false)},label={Text("Light")},leadingIcon={Icon(Icons.Default.LightMode,null)},modifier=Modifier.weight(1f))
          }
        }
      }
      Spacer(Modifier.height(20.dp))
    }
  }

  if(showMemoryDialog) AlertDialog(
    onDismissRequest={showMemoryDialog=false},
    title={Text("Save a memory")},
    text={OutlinedTextField(newMemory,{newMemory=it},placeholder={Text("Example: I prefer concise answers.")},modifier=Modifier.fillMaxWidth(),minLines=2)},
    confirmButton={TextButton(onClick={profileViewModel.addMemory(newMemory);newMemory="";showMemoryDialog=false}){Text("Save")}},
    dismissButton={TextButton(onClick={showMemoryDialog=false}){Text("Cancel")}}
  )
}
