package com.openclaw.agent.ui.settings

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.openclaw.agent.core.mijia.MijiaApiClient
import com.openclaw.agent.core.mijia.MijiaAuthStore
import com.openclaw.agent.core.mijia.MijiaTokenRefresher
import com.openclaw.agent.core.web.cookie.CookieVault
import com.openclaw.agent.data.preferences.SettingsStore
import com.openclaw.agent.testing.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class SettingsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var settingsStore: SettingsStore
    private lateinit var mijiaAuthStore: MijiaAuthStore
    private lateinit var mijiaApiClient: MijiaApiClient
    private lateinit var mijiaTokenRefresher: MijiaTokenRefresher
    private lateinit var cookieVault: FakeCookieVault
    private lateinit var viewModel: SettingsViewModel
    private lateinit var dataStoreFile: File

    @Before
    fun setUp() {
        dataStoreFile = File(context.cacheDir, "settings-test-${System.nanoTime()}.preferences_pb")
        val dataStore = PreferenceDataStoreFactory.create(
            produceFile = { dataStoreFile }
        )
        settingsStore = SettingsStore(context, dataStore)
        mijiaAuthStore = MijiaAuthStore(context)
        val okHttpClient = OkHttpClient()
        mijiaApiClient = MijiaApiClient(okHttpClient, mijiaAuthStore)
        mijiaTokenRefresher = MijiaTokenRefresher(okHttpClient, mijiaAuthStore)
        cookieVault = FakeCookieVault(listOf("bilibili", "zhihu"))
        viewModel = SettingsViewModel(
            settingsStore = settingsStore,
            mijiaAuthStore = mijiaAuthStore,
            mijiaApiClient = mijiaApiClient,
            mijiaTokenRefresher = mijiaTokenRefresher,
            cookieVault = cookieVault,
        )
    }

    @After
    fun tearDown() {
        dataStoreFile.delete()
        File(context.filesDir, "mijia_auth").deleteRecursively()
        File(context.filesDir, "shared_prefs/mijia_auth.xml").delete()
    }

    @Test
    fun saveApiKey_updatesStateFlowAndStoreSynchronously() {
        viewModel.saveApiKey("test-api-key")

        assertThat(viewModel.apiKey.value).isEqualTo("test-api-key")
        assertThat(settingsStore.getApiKey()).isEqualTo("test-api-key")
    }

    @Test
    fun saveBaseUrl_updatesStateFlowAndStoreSynchronously() {
        val url = "https://example.com/messages"

        viewModel.saveBaseUrl(url)

        assertThat(viewModel.baseUrl.value).isEqualTo(url)
        assertThat(settingsStore.getBaseUrl()).isEqualTo(url)
    }

    @Test
    fun selectModel_persistsToFlow() = runTest {
        viewModel.selectModel("claude-opus-4-6")
        advanceUntilIdle()

        assertThat(viewModel.selectedModel.first { it == "claude-opus-4-6" }).isEqualTo("claude-opus-4-6")
    }

    @Test
    fun setShowToolCalls_persistsToFlow() = runTest {
        viewModel.setShowToolCalls(true)
        advanceUntilIdle()

        assertThat(viewModel.showToolCalls.first { it }).isTrue()
    }

    @Test
    fun refreshAndLogoutSiteAccounts_reflectCookieVaultState() {
        assertThat(viewModel.siteAccounts.value).containsExactly("bilibili", "zhihu").inOrder()

        viewModel.logoutSite("bilibili")

        assertThat(viewModel.siteAccounts.value).containsExactly("zhihu")
        assertThat(cookieVault.loggedOutSites).containsExactly("bilibili")
    }

    @Test
    fun resetTestState_setsConnectionStateToIdle() {
        viewModel.testConnection("claude-opus-4-6")
        viewModel.resetTestState()

        assertThat(viewModel.connectionTestState.value).isEqualTo(ConnectionTestState.Idle)
    }
}

private class FakeCookieVault(initialSites: List<String>) : CookieVault(null) {
    private val sites = initialSites.toMutableList()
    val loggedOutSites = mutableListOf<String>()

    override fun getLoggedInSites(): List<String> = sites.toList()

    override fun logout(site: String) {
        loggedOutSites += site
        sites.remove(site)
    }
}
