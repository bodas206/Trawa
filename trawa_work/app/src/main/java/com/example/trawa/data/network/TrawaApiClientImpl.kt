package com.example.trawa.data.network

import android.util.Log
import com.example.trawa.domain.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.MultipartBody
import java.io.File
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.UUID
import java.util.concurrent.TimeUnit

class TrawaApiClientImpl(
  private val context: android.content.Context,
  private var baseUrl: String = NetworkConfig.DEFAULT_PRODUCTION_URL,
  private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
    .connectTimeout(30, TimeUnit.SECONDS)
    .readTimeout(180, TimeUnit.SECONDS)
    .writeTimeout(60, TimeUnit.SECONDS)
    .build()
) : TrawaApiClient {

  private var activeSession: Session? = null
  private val prefs = context.getSharedPreferences("trawa_session", android.content.Context.MODE_PRIVATE)

  override fun getBaseUrl(): String = baseUrl

  override fun setBaseUrl(newUrl: String) {
    val sanitized = newUrl.trim().removeSuffix("/")
    // Strictly disallow localhost and LAN IPs in production architecture
    if (sanitized.contains("localhost") || sanitized.contains("127.0.0.1") ||
        sanitized.contains("192.168.") || sanitized.contains(":5173")
    ) {
      throw IllegalArgumentException("Localhost, 192.168.x.x and Vite dev server ports are forbidden in TRAWA production.")
    }
    this.baseUrl = sanitized
  }

  override fun isProductionReady(): Boolean {
    return !baseUrl.contains("localhost") && !baseUrl.contains("127.0.0.1") && !baseUrl.contains("192.168.")
  }

  private fun authHeader(builder: Request.Builder): Request.Builder {
    activeSession?.token?.takeIf { it.isNotBlank() }?.let { builder.header("Authorization", "Bearer $it") }
    return builder
  }

  private fun persistSession(session: Session?) {
    activeSession = session
    if (session == null) {
      prefs.edit().clear().apply()
      return
    }
    prefs.edit()
      .putString("token", session.token)
      .putString("user_id", session.user.id)
      .putString("email", session.user.email)
      .putString("name", session.user.displayName)
      .putLong("expires_at", session.expiresAt)
      .putString("refresh_token", session.refreshToken.orEmpty())
      .apply()
  }

  private fun restoreSession(): Session? {
    val token = prefs.getString("token", null) ?: return null
    val expiresAt = prefs.getLong("expires_at", 0L)
    if (expiresAt > 0 && expiresAt <= System.currentTimeMillis()) {
      prefs.edit().clear().apply()
      return null
    }
    return Session(
      token = token,
      user = User(
        id = prefs.getString("user_id", "") ?: "",
        email = prefs.getString("email", "") ?: "",
        displayName = prefs.getString("name", "User") ?: "User"
      ),
      expiresAt = expiresAt,
      refreshToken = prefs.getString("refresh_token", null)
    )
  }

  override suspend fun login(email: String, password: String): Result<Session> = withContext(Dispatchers.IO) {
    try {
      if (email.isBlank() || password.isBlank()) return@withContext Result.failure(IllegalArgumentException("Email and password are required."))
      val body = JSONObject().apply { put("email", email.trim()); put("password", password) }
      val request = Request.Builder().url("$baseUrl/v1/auth/login")
        .post(body.toString().toRequestBody("application/json".toMediaType())).build()
      okHttpClient.newCall(request).execute().use { response ->
        val raw = response.body?.string().orEmpty()
        if (!response.isSuccessful) return@withContext Result.failure(Exception(JSONObject(raw).optString("error", "Authentication failed (${response.code})")))
        val json = JSONObject(raw)
        val userObj = json.getJSONObject("user")
        val session = Session(json.getString("token"), User(userObj.getString("id"), userObj.optString("email", email), userObj.optString("name", email.substringBefore("@"))), json.optLong("expiresAt", System.currentTimeMillis()+3600000), json.optString("refreshToken").takeIf { it.isNotBlank() })
        persistSession(session)
        Result.success(session)
      }
    } catch (e: Exception) { Result.failure(e) }
  }

  override suspend fun signUp(email: String, password: String, name: String): Result<Session?> = withContext(Dispatchers.IO) {
    try {
      val body = JSONObject().apply { put("email", email.trim()); put("password", password); put("name", name.trim()) }
      val request = Request.Builder().url("$baseUrl/v1/auth/signup")
        .post(body.toString().toRequestBody("application/json".toMediaType())).build()
      okHttpClient.newCall(request).execute().use { response ->
        val raw = response.body?.string().orEmpty(); val json = JSONObject(raw)
        if (!response.isSuccessful) return@withContext Result.failure(Exception(json.optString("error", "Could not create account (${response.code})")))
        val token = json.optString("token", "")
        if (token.isBlank()) return@withContext Result.success(null)
        val u = json.getJSONObject("user")
        val session = Session(token, User(u.getString("id"), u.optString("email", email), u.optString("name", name.ifBlank { "User" })), json.optLong("expiresAt", System.currentTimeMillis()+3600000), json.optString("refreshToken").takeIf { it.isNotBlank() })
        persistSession(session)
        Result.success(session)
      }
    } catch (e: Exception) { Result.failure(e) }
  }

  override suspend fun logout(): Result<Unit> = withContext(Dispatchers.IO) { persistSession(null); Result.success(Unit) }

  override suspend fun getCurrentSession(): Session? = withContext(Dispatchers.IO) {
    if (activeSession == null) activeSession = restoreSession()
    val current = activeSession ?: return@withContext null
    if (current.refreshToken.isNullOrBlank()) return@withContext current
    if (current.expiresAt - System.currentTimeMillis() > 5 * 60 * 1000) return@withContext current

    try {
      val body = JSONObject().put("refreshToken", current.refreshToken)
      val request = Request.Builder()
        .url("$baseUrl/v1/auth/refresh")
        .post(body.toString().toRequestBody("application/json".toMediaType()))
        .build()
      okHttpClient.newCall(request).execute().use { response ->
        if (!response.isSuccessful) return@withContext current
        val json = JSONObject(response.body?.string().orEmpty())
        val refreshed = Session(
          token = json.getString("token"),
          user = current.user,
          expiresAt = json.optLong("expiresAt", System.currentTimeMillis() + 3600000),
          refreshToken = json.optString("refreshToken").takeIf { it.isNotBlank() } ?: current.refreshToken
        )
        persistSession(refreshed)
        refreshed
      }
    } catch (_: Exception) { current }
  }

  override suspend fun resetPassword(email: String): Result<Unit> = withContext(Dispatchers.IO) {
    try {
      if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
        return@withContext Result.failure(IllegalArgumentException("Please enter a valid email address."))
      }
      val jsonBody = JSONObject().apply { put("email", email) }
      val request = Request.Builder()
        .url("$baseUrl/v1/auth/forgot-password")
        .post(jsonBody.toString().toRequestBody("application/json".toMediaType()))
        .build()

      val response = okHttpClient.newCall(request).execute()
      if (response.isSuccessful) {
        Result.success(Unit)
      } else {
        Result.failure(Exception("Password reset request failed (${response.code})."))
      }
    } catch (e: Exception) {
      Result.failure(Exception("Could not connect to TRAWA server to reset password: ${e.message}"))
    }
  }

  override suspend fun getConversations(): Result<List<Conversation>> = withContext(Dispatchers.IO) {
    try {
      val request = authHeader(Request.Builder().url("$baseUrl/v1/conversations")).get().build()
      okHttpClient.newCall(request).execute().use { response ->
        val raw=response.body?.string().orEmpty(); if(!response.isSuccessful) return@withContext Result.failure(Exception(JSONObject(raw).optString("error","Failed to load conversations (${response.code})")))
        val arr=JSONArray(raw); val list=mutableListOf<Conversation>(); for(i in 0 until arr.length()){ val o=arr.getJSONObject(i); list.add(Conversation(o.getString("id"),o.optString("title","New Chat"),o.optLong("created_at",System.currentTimeMillis()),o.optBoolean("is_temporary",false))) }; Result.success(list)
      }
    } catch(e:Exception){ Result.failure(e) }
  }

  override suspend fun createConversation(title: String, isTemporary: Boolean): Result<Conversation> = withContext(Dispatchers.IO) {
    try { val body=JSONObject().apply{put("title",title.ifBlank{"New Chat"});put("isTemporary",isTemporary)}; val req=authHeader(Request.Builder().url("$baseUrl/v1/conversations")).post(body.toString().toRequestBody("application/json".toMediaType())).build(); okHttpClient.newCall(req).execute().use{r-> val raw=r.body?.string().orEmpty(); if(!r.isSuccessful)return@withContext Result.failure(Exception(JSONObject(raw).optString("error","Could not create conversation"))); val o=JSONObject(raw); Result.success(Conversation(o.getString("id"),o.optString("title","New Chat"),o.optLong("created_at",System.currentTimeMillis()),o.optBoolean("is_temporary",isTemporary))) } } catch(e:Exception){ Result.failure(e) }
  }

  override suspend fun deleteConversation(conversationId: String): Result<Unit> = withContext(Dispatchers.IO) {
    try { val req=authHeader(Request.Builder().url("$baseUrl/v1/conversations/$conversationId")).delete().build(); okHttpClient.newCall(req).execute().use{r-> if(r.isSuccessful) Result.success(Unit) else Result.failure(Exception("Delete failed (${r.code})"))} } catch(e:Exception){ Result.failure(e) }
  }

  override suspend fun getMessages(conversationId: String): Result<List<ChatMessage>> = withContext(Dispatchers.IO) {
    try { val req=authHeader(Request.Builder().url("$baseUrl/v1/conversations/$conversationId/messages")).get().build(); okHttpClient.newCall(req).execute().use{r-> val raw=r.body?.string().orEmpty(); if(!r.isSuccessful)return@withContext Result.failure(Exception(JSONObject(raw).optString("error","Failed to load messages (${r.code})"))); val arr=JSONArray(raw); val list=mutableListOf<ChatMessage>(); for(i in 0 until arr.length()){val o=arr.getJSONObject(i);list.add(ChatMessage(o.getString("id"),conversationId,MessageRole.valueOf(o.getString("role").uppercase()),o.optString("content",""),createdAt=o.optLong("created_at",System.currentTimeMillis())))}; Result.success(list)} } catch(e:Exception){ Result.failure(e) }
  }

  override fun sendChatMessageStream(conversationId: String, message: String, attachments: List<Attachment>, isTemporary: Boolean, clientMessageId: String): Flow<ChatStreamEvent> = flow {
    val body=JSONObject().apply{put("conversationId",conversationId);put("message",message);put("temporary",isTemporary);put("clientMessageId",clientMessageId);put("attachments",JSONArray().apply{attachments.forEach{a->put(JSONObject().apply{put("id",a.id);put("type",a.type.name);put("name",a.name);put("remoteId",a.remoteId)})}})}
    val req=authHeader(Request.Builder().url("$baseUrl/v1/chat")).header("Accept","text/event-stream").post(body.toString().toRequestBody("application/json".toMediaType())).build()
    try { okHttpClient.newCall(req).execute().use{response->
      if(!response.isSuccessful){val raw=response.body?.string().orEmpty();throw Exception(JSONObject(raw).optString("error","Server error ${response.code}"))}
      val reader=BufferedReader(InputStreamReader(response.body?.byteStream()?:throw Exception("Empty response body"))); val id=UUID.randomUUID().toString(); emit(ChatStreamEvent.MessageStart(id,MessageRole.ASSISTANT)); val full=StringBuilder(); var line:String?
      while(reader.readLine().also{line=it}!=null){
        val cur=line?:break
        if(!cur.startsWith("data:")) continue
        val data=cur.removePrefix("data:").trim()
        if(data=="[DONE]") break
        try {
          val j=JSONObject(data)
          when {
            j.has("error") -> {
              emit(ChatStreamEvent.StreamError(j.optString("error", "AI service unavailable"), response.code))
              return@use
            }
            j.has("delta") -> {
              val c=j.getString("delta")
              if(c.isNotEmpty()) { full.append(c); emit(ChatStreamEvent.Delta(c)) }
            }
          }
        } catch(_:Exception) {}
      }
      if(full.isNotBlank()) emit(ChatStreamEvent.MessageEnd(full.toString(),id))
      else emit(ChatStreamEvent.StreamError("3AR V1 Pro returned an empty response.", response.code))
    }} catch(e:Exception){ emit(ChatStreamEvent.StreamError(e.message ?: "Connection to TRAWA server failed.")) }
  }.flowOn(Dispatchers.IO)

  override suspend fun uploadAttachment(attachment: Attachment): Result<Attachment> = withContext(Dispatchers.IO) {
    try {
      val uri = attachment.localUri?.let(android.net.Uri::parse) ?: return@withContext Result.failure(Exception("Attachment has no local URI"))
      val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return@withContext Result.failure(Exception("Could not read attachment"))
      if (bytes.size > 25 * 1024 * 1024) return@withContext Result.failure(Exception("Attachment is larger than 25 MB"))
      val body = okhttp3.RequestBody.create(attachment.mimeType.toMediaType(), bytes)
      val multipart = MultipartBody.Builder().setType(MultipartBody.FORM)
        .addFormDataPart("file", attachment.name, body)
        .addFormDataPart("type", attachment.type.name)
        .build()
      val req = authHeader(Request.Builder().url("$baseUrl/v1/attachments/upload"))
        .post(multipart).build()
      okHttpClient.newCall(req).execute().use { r ->
        val raw = r.body?.string().orEmpty()
        if (!r.isSuccessful) return@withContext Result.failure(Exception(JSONObject(raw).optString("error", "Attachment upload failed (${r.code})")))
        val o = JSONObject(raw)
        Result.success(attachment.copy(remoteId=o.optString("id"), uploadState=UploadState.UPLOADED, uploadProgress=1f))
      }
    } catch(e:Exception) { Result.failure(e) }
  }

  override suspend fun getPersonalization(): Result<PersonalizationSettings> = withContext(Dispatchers.IO) {
    try { val req=authHeader(Request.Builder().url("$baseUrl/v1/profile/personalization")).get().build(); okHttpClient.newCall(req).execute().use{r->val raw=r.body?.string().orEmpty(); if(!r.isSuccessful)return@withContext Result.failure(Exception(JSONObject(raw).optString("error","Failed to load personalization"))); val o=JSONObject(raw); Result.success(PersonalizationSettings(o.optString("customInstructions",""),o.optString("preferredLanguage","Auto (Default)"),o.optString("responseTone","Balanced & Precise")))}} catch(e:Exception){Result.failure(e)}
  }

  override suspend fun updatePersonalization(settings: PersonalizationSettings): Result<Unit> = withContext(Dispatchers.IO) {
    try { val body=JSONObject().apply{put("customInstructions",settings.customInstructions);put("preferredLanguage",settings.preferredLanguage);put("responseTone",settings.responseTone)}; val req=authHeader(Request.Builder().url("$baseUrl/v1/profile/personalization")).put(body.toString().toRequestBody("application/json".toMediaType())).build(); okHttpClient.newCall(req).execute().use{r->if(r.isSuccessful)Result.success(Unit)else Result.failure(Exception("Personalization save failed (${r.code})"))}} catch(e:Exception){Result.failure(e)}
  }

  override suspend fun getMemories(): Result<List<MemoryItem>> = withContext(Dispatchers.IO) {
    try { val req=authHeader(Request.Builder().url("$baseUrl/v1/profile/memories")).get().build(); okHttpClient.newCall(req).execute().use{r->val raw=r.body?.string().orEmpty();if(!r.isSuccessful)return@withContext Result.failure(Exception(JSONObject(raw).optString("error","Failed to load memory")));val arr=JSONArray(raw);val list=mutableListOf<MemoryItem>();for(i in 0 until arr.length()){val o=arr.getJSONObject(i);list.add(MemoryItem(o.getString("id"),o.optString("content",""),o.optLong("createdAt",System.currentTimeMillis()),o.optString("category","general")))};Result.success(list)}}catch(e:Exception){Result.failure(e)}
  }

  override suspend fun addMemory(content: String): Result<MemoryItem> = withContext(Dispatchers.IO) {
    try { val body=JSONObject().apply{put("content",content)};val req=authHeader(Request.Builder().url("$baseUrl/v1/profile/memories")).post(body.toString().toRequestBody("application/json".toMediaType())).build();okHttpClient.newCall(req).execute().use{r->val raw=r.body?.string().orEmpty();if(!r.isSuccessful)return@withContext Result.failure(Exception(JSONObject(raw).optString("error","Memory save failed")));val o=JSONObject(raw);Result.success(MemoryItem(o.getString("id"),o.optString("content",content),o.optLong("createdAt",System.currentTimeMillis()),o.optString("category","general")))}}catch(e:Exception){Result.failure(e)}
  }

  override suspend fun deleteMemory(memoryId: String): Result<Unit> = withContext(Dispatchers.IO) {
    try {val req=authHeader(Request.Builder().url("$baseUrl/v1/profile/memories/$memoryId")).delete().build();okHttpClient.newCall(req).execute().use{r->if(r.isSuccessful)Result.success(Unit)else Result.failure(Exception("Memory delete failed (${r.code})"))}}catch(e:Exception){Result.failure(e)}
  }
}
