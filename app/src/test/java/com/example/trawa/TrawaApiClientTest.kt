package com.example.trawa

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.trawa.data.network.TrawaApiClientImpl
import org.junit.Assert.*
import org.junit.Test

class TrawaApiClientTest {

  private val context: Context = ApplicationProvider.getApplicationContext()


  @Test
  fun `default production url is valid and production ready`() {
    val client = TrawaApiClientImpl(context)
    assertEquals("https://api.trawa.ai", client.getBaseUrl())
    assertTrue("Should be production ready", client.isProductionReady())
  }

  @Test
  fun `setting staging url succeeds`() {
    val client = TrawaApiClientImpl(context)
    client.setBaseUrl("https://staging-api.trawa.ai/")
    assertEquals("https://staging-api.trawa.ai", client.getBaseUrl())
    assertTrue(client.isProductionReady())
  }

  @Test(expected = IllegalArgumentException::class)
  fun `setting localhost is strictly forbidden in production`() {
    val client = TrawaApiClientImpl(context)
    client.setBaseUrl("http://localhost:8080")
  }

  @Test(expected = IllegalArgumentException::class)
  fun `setting 127_0_0_1 is strictly forbidden in production`() {
    val client = TrawaApiClientImpl(context)
    client.setBaseUrl("http://127.0.0.1:3000")
  }

  @Test(expected = IllegalArgumentException::class)
  fun `setting 192_168 LAN IP is strictly forbidden in production`() {
    val client = TrawaApiClientImpl(context)
    client.setBaseUrl("http://192.168.1.4:5173")
  }

  @Test(expected = IllegalArgumentException::class)
  fun `setting port 5173 Vite dev server is strictly forbidden`() {
    val client = TrawaApiClientImpl(context)
    client.setBaseUrl("http://my-server.com:5173")
  }
}
