package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.model.ConnectionStatus
import com.example.model.ProxyConfig
import com.example.model.ProxyProtocol
import com.example.model.ProxyState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("DH PROXY", appName)
  }

  @Test
  fun `test proxy config and timer formatted`() {
    val config = ProxyConfig(
      name = "Test SOCKS5",
      host = "127.0.0.1",
      port = 1080,
      protocol = ProxyProtocol.SOCKS5
    )
    assertEquals("Test SOCKS5", config.name)
    assertEquals(1080, config.port)

    val state = ProxyState(
      status = ConnectionStatus.CONNECTED,
      activeConfig = config,
      elapsedSeconds = 3665 // 1h 1m 5s
    )
    assertEquals("01:01:05", state.formattedTime())
  }
  @Test
  fun `test strict config validation`() {
    val invalidHost = ProxyConfig(host = "", port = 1080)
    val errHost = com.example.service.ProxyController.validateConfig(invalidHost)
    assertNotNull(errHost)

    val invalidPort = ProxyConfig(host = "1.2.3.4", port = 70000)
    val errPort = com.example.service.ProxyController.validateConfig(invalidPort)
    assertNotNull(errPort)

    val missingAuth = ProxyConfig(
      host = "1.2.3.4",
      port = 1080,
      requiresAuth = true,
      username = "admin",
      password = ""
    )
    val errAuth = com.example.service.ProxyController.validateConfig(missingAuth)
    assertNotNull(errAuth)

    val validConfig = ProxyConfig(
      host = "1.2.3.4",
      port = 1080,
      requiresAuth = true,
      username = "admin",
      password = "secretpassword"
    )
    assertEquals(null, com.example.service.ProxyController.validateConfig(validConfig))
  }

  @Test
  fun `test profile repository save and load with password preservation`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val repository = com.example.repository.ProxyRepository(context)

    val testProfile = ProxyConfig(
      id = "custom_test_id",
      name = "Secured SOCKS5",
      host = "proxy.example.com",
      port = 1080,
      protocol = ProxyProtocol.SOCKS5,
      requiresAuth = true,
      username = "testuser",
      password = "mySecretPassword123"
    )

    repository.addOrUpdateProfile(testProfile)

    val loadedProfiles = repository.getProfiles()
    val found = loadedProfiles.firstOrNull { it.id == "custom_test_id" }
    assertNotNull(found)
    assertEquals("Secured SOCKS5", found?.name)
    assertEquals("testuser", found?.username)
    assertEquals("mySecretPassword123", found?.password)
    assertEquals(true, found?.requiresAuth)
  }

  @Test
  fun `test state formatted bytes`() {
    val state = ProxyState(
      bytesIn = 1024L * 500, // 500 KB
      bytesOut = 1024L * 1024 * 5 // 5 MB
    )
    assertEquals("500.0 KB", state.formattedDataIn())
    assertEquals("5.00 MB", state.formattedDataOut())
  }

  @Test
  fun `test notifyServiceError updates state accurately`() {
    com.example.service.ProxyController.notifyServiceError("Authentication rejected")
    val state = com.example.service.ProxyController.state.value
    assertEquals(com.example.model.ConnectionStatus.ERROR, state.status)
    assertEquals("Authentication rejected", state.errorMessage)
  }

  @Test
  fun `test proxy probe data and engineStatus`() {
    val probeData = com.example.repository.ProxyRepository.ProxyProbeData(
      latencyMs = 42L,
      resolvedIp = "198.51.100.1",
      supportsUdp = true
    )
    assertEquals(42L, probeData.latencyMs)
    assertEquals("198.51.100.1", probeData.resolvedIp)
    assertEquals(true, probeData.supportsUdp)

    val state = ProxyState(
      status = ConnectionStatus.CONNECTED,
      engineStatus = "tun2socks active (MTU 1400 • TCP+UDP)"
    )
    assertEquals("tun2socks active (MTU 1400 • TCP+UDP)", state.engineStatus)
  }

  @Test
  fun `test telegram URLs and configuration`() {
    assertEquals("https://t.me/dh_method", com.example.util.TelegramHelper.CHANNEL_URL)
    assertEquals("dh_method", com.example.util.TelegramHelper.CHANNEL_USERNAME)
    assertEquals("https://t.me/argentina1918", com.example.util.TelegramHelper.SUPPORT_URL)
    assertEquals("argentina1918", com.example.util.TelegramHelper.SUPPORT_USERNAME)
  }

  @Test
  fun `test notifyServiceConnected updates state to CONNECTED`() {
    com.example.service.ProxyController.notifyServiceConnected(
      latencyMs = 28L,
      resolvedIp = "203.0.113.195",
      supportsUdp = false
    )
    val state = com.example.service.ProxyController.state.value
    assertEquals(com.example.model.ConnectionStatus.CONNECTED, state.status)
    assertEquals(28L, state.latencyMs)
    assertEquals("203.0.113.195", state.externalIp)
    assertEquals("tun2socks active (MTU 1400 • TCP-only • DNS Auto)", state.engineStatus)
  }

  @Test
  fun `test getRoutesExcludingIp produces 32 subnets excluding target IP`() {
    val excludedIp = "1.1.1.1"
    val routes = com.example.service.ProxyVpnService.getRoutesExcludingIp(excludedIp)
    assertEquals(32, routes.size)

    // Helper to check if an IP matches a CIDR prefix
    fun ipMatchesPrefix(ipStr: String, prefixIp: String, prefixLen: Int): Boolean {
      val ipBytes = java.net.InetAddress.getByName(ipStr).address
      val prefixBytes = java.net.InetAddress.getByName(prefixIp).address
      var ipInt = 0L
      var prefInt = 0L
      for (i in 0..3) {
        ipInt = (ipInt shl 8) or (ipBytes[i].toLong() and 0xFFL)
        prefInt = (prefInt shl 8) or (prefixBytes[i].toLong() and 0xFFL)
      }
      val mask = if (prefixLen == 0) 0L else (-1L shl (32 - prefixLen)) and 0xFFFFFFFFL
      return (ipInt and mask) == (prefInt and mask)
    }

    // 1. Target excluded IP must NOT match any of the 32 routes
    val excludedMatchesAny = routes.any { (prefixIp, prefixLen) ->
      ipMatchesPrefix(excludedIp, prefixIp, prefixLen)
    }
    assertEquals(false, excludedMatchesAny)

    // 2. YouTube IP (142.250.190.46) MUST match one of the routes
    val youtubeIp = "142.250.190.46"
    val youtubeMatchesAny = routes.any { (prefixIp, prefixLen) ->
      ipMatchesPrefix(youtubeIp, prefixIp, prefixLen)
    }
    assertEquals(true, youtubeMatchesAny)

    // 3. Google DNS (8.8.8.8) MUST match one of the routes
    val googleDns = "8.8.8.8"
    val googleDnsMatchesAny = routes.any { (prefixIp, prefixLen) ->
      ipMatchesPrefix(googleDns, prefixIp, prefixLen)
    }
    assertEquals(true, googleDnsMatchesAny)
  }

  @Test
  fun `test dns caching returns consistent IP`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val repository = com.example.repository.ProxyRepository(context)
    val ip1 = repository.resolveHostWithCache("127.0.0.1")
    val ip2 = repository.resolveHostWithCache("127.0.0.1")
    assertEquals("127.0.0.1", ip1)
    assertEquals(ip1, ip2)
  }
}

