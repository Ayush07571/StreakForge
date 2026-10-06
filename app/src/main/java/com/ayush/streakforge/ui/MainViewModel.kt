package com.ayush.streakforge.ui

import android.app.Application
import androidx.glance.appwidget.updateAll
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ayush.streakforge.LegacyMigration
import com.ayush.streakforge.StreakWidget
import com.ayush.streakforge.data.DeviceCodeResponse
import com.ayush.streakforge.data.OAuthPollResult
import com.ayush.streakforge.data.StreakRepository
import com.ayush.streakforge.data.UserFullState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

sealed interface MainUiState {
    data object Loading : MainUiState
    data class NotConnected(val errorMsg: String = "") : MainUiState
    data class Success(
        val userState: UserFullState,
        val isRefreshing: Boolean = false,
        val statusMessage: String = ""
    ) : MainUiState
}

sealed interface OAuthUiState {
    data object Idle : OAuthUiState
    data object Loading : OAuthUiState
    data class CodeReceived(
        val response: DeviceCodeResponse,
        val remainingSeconds: Int
    ) : OAuthUiState
    data class Error(val message: String) : OAuthUiState
}

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = StreakRepository(application)
    private val _statusMsg = MutableStateFlow("")
    private val _isRefreshing = MutableStateFlow(false)

    private val _activeUser = MutableStateFlow(repository.getActiveUser())
    val activeUser: StateFlow<String> = _activeUser.asStateFlow()

    private val _oauthState = MutableStateFlow<OAuthUiState>(OAuthUiState.Idle)
    val oauthState: StateFlow<OAuthUiState> = _oauthState.asStateFlow()

    private var pollJob: Job? = null
    private var countdownJob: Job? = null

    val uiState: StateFlow<MainUiState> = combine(
        _activeUser,
        _isRefreshing,
        _statusMsg
    ) { user, refreshing, msg ->
        Triple(user, refreshing, msg)
    }.flatMapLatest { (user, refreshing, msg) ->
        if (user.isBlank()) {
            flowOf(MainUiState.NotConnected(msg))
        } else {
            repository.getUserStateFlow(user).map { userState ->
                if (userState == null) {
                    MainUiState.NotConnected("Connecting to GitHub...")
                } else {
                    MainUiState.Success(
                        userState = userState,
                        isRefreshing = refreshing,
                        statusMessage = msg
                    )
                }
            }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = MainUiState.Loading
    )

    init {
        LegacyMigration.wipeLegacyStore(application)
        val active = repository.getActiveUser()
        if (active.isNotBlank()) {
            refresh()
        }
    }

    fun startDeviceCodeFlow() {
        viewModelScope.launch {
            _oauthState.value = OAuthUiState.Loading
            val result = repository.requestDeviceCode()
            if (result.isSuccess) {
                val resp = result.getOrThrow()
                _oauthState.value = OAuthUiState.CodeReceived(resp, resp.expiresIn)
                startCountdown(resp.expiresIn)
                startPolling(resp.deviceCode, resp.interval)
            } else {
                _oauthState.value = OAuthUiState.Error(
                    result.exceptionOrNull()?.message ?: "Failed to get device code"
                )
            }
        }
    }

    private fun startCountdown(initialSeconds: Int) {
        countdownJob?.cancel()
        countdownJob = viewModelScope.launch {
            var seconds = initialSeconds
            while (seconds > 0) {
                delay(1000)
                seconds--
                val current = _oauthState.value
                if (current is OAuthUiState.CodeReceived) {
                    _oauthState.value = current.copy(remainingSeconds = seconds)
                } else {
                    break
                }
            }
            if (seconds <= 0) {
                pollJob?.cancel()
                _oauthState.value = OAuthUiState.Error("Code expired. Please request a new code.")
            }
        }
    }

    private fun startPolling(deviceCode: String, initialInterval: Int) {
        pollJob?.cancel()
        pollJob = viewModelScope.launch {
            var currentInterval = initialInterval.coerceAtLeast(5)
            while (true) {
                delay(currentInterval * 1000L)
                val pollResult = repository.pollDeviceToken(deviceCode)
                when (pollResult) {
                    is OAuthPollResult.Success -> {
                        countdownJob?.cancel()
                        _oauthState.value = OAuthUiState.Loading
                        val completeResult = repository.completeOAuthAndFetch(pollResult.accessToken)
                        if (completeResult.isSuccess) {
                            val userLogin = completeResult.getOrThrow()
                            _activeUser.value = userLogin
                            _statusMsg.value = "Successfully signed in as @$userLogin!"
                            _oauthState.value = OAuthUiState.Idle
                            StreakWidget().updateAll(getApplication())
                        } else {
                            _oauthState.value = OAuthUiState.Error(
                                completeResult.exceptionOrNull()?.message ?: "Failed to fetch profile"
                            )
                        }
                        break
                    }
                    is OAuthPollResult.Pending -> {
                        // Keep polling
                    }
                    is OAuthPollResult.SlowDown -> {
                        currentInterval += pollResult.extraSeconds
                    }
                    is OAuthPollResult.Expired -> {
                        countdownJob?.cancel()
                        _oauthState.value = OAuthUiState.Error(pollResult.message)
                        break
                    }
                    is OAuthPollResult.Denied -> {
                        countdownJob?.cancel()
                        _oauthState.value = OAuthUiState.Error(pollResult.message)
                        break
                    }
                    is OAuthPollResult.Error -> {
                        countdownJob?.cancel()
                        _oauthState.value = OAuthUiState.Error(pollResult.message)
                        break
                    }
                }
            }
        }
    }

    fun pausePolling() {
        pollJob?.cancel()
    }

    fun resumePolling() {
        val current = _oauthState.value
        if (current is OAuthUiState.CodeReceived && current.remainingSeconds > 0) {
            startPolling(current.response.deviceCode, current.response.interval)
        }
    }

    fun cancelOAuth() {
        pollJob?.cancel()
        countdownJob?.cancel()
        _oauthState.value = OAuthUiState.Idle
    }

    fun saveLogin(user: String, token: String) {
        viewModelScope.launch {
            _isRefreshing.value = true
            _statusMsg.value = ""
            val result = repository.saveCredentialsAndFetch(user, token)
            _isRefreshing.value = false

            if (result.isSuccess) {
                _activeUser.value = user.trim()
                _statusMsg.value = "Successfully connected!"
                StreakWidget().updateAll(getApplication())
            } else {
                _statusMsg.value = result.exceptionOrNull()?.message ?: "Failed to connect"
            }
        }
    }

    fun refresh() {
        val user = _activeUser.value
        if (user.isBlank()) return
        viewModelScope.launch {
            _isRefreshing.value = true
            _statusMsg.value = ""
            val result = repository.refreshActiveUser()
            _isRefreshing.value = false

            if (result.isSuccess) {
                StreakWidget().updateAll(getApplication())
            } else {
                val err = result.exceptionOrNull()?.message ?: "Failed to refresh"
                _statusMsg.value = err
                if (err.contains("401")) {
                    signOut()
                }
            }
        }
    }

    fun claimReward(day: Int, grantShield: Boolean) {
        val user = _activeUser.value
        if (user.isBlank()) return
        viewModelScope.launch {
            repository.claimReward(user, day, grantShield)
            _statusMsg.value = if (grantShield) "Reward claimed! +1 Streak Shield added 🛡️" else "Reward claimed!"
            StreakWidget().updateAll(getApplication())
        }
    }

    fun saveCustomReward(day: Int, emoji: String, label: String, grantShield: Boolean) {
        val user = _activeUser.value
        if (user.isBlank()) return
        viewModelScope.launch {
            repository.saveCustomReward(user, day, emoji, label, grantShield)
            _statusMsg.value = "Reward updated!"
        }
    }

    fun updateDsaGoal(goal: Int) {
        val user = _activeUser.value
        if (user.isBlank()) return
        viewModelScope.launch {
            repository.updateDsaGoal(user, goal)
        }
    }

    fun setReminders(enabled: Boolean, hour: Int, minute: Int) {
        val user = _activeUser.value
        if (user.isBlank()) return
        viewModelScope.launch {
            repository.setReminders(user, enabled, hour, minute)
            if (enabled) {
                com.ayush.streakforge.scheduleDailyReminder(getApplication(), hour, minute)
                _statusMsg.value = "Daily reminder set for ${String.format("%02d:%02d", hour, minute)}"
            } else {
                com.ayush.streakforge.cancelDailyReminder(getApplication())
                _statusMsg.value = "Daily reminder turned off"
            }
        }
    }

    fun setSolvedDsa(solved: Int) {
        val user = _activeUser.value
        if (user.isBlank()) return
        viewModelScope.launch {
            repository.setSolvedDsa(user, solved)
        }
    }

    fun restoreStreak() {
        val user = _activeUser.value
        if (user.isBlank()) return
        viewModelScope.launch {
            val res = repository.restoreStreak(user)
            if (res.isSuccess) {
                val left = res.getOrDefault(0)
                _statusMsg.value = "Streak restored! You have $left restore(s) left this month."
                StreakWidget().updateAll(getApplication())
            } else {
                _statusMsg.value = res.exceptionOrNull()?.message ?: "Cannot restore streak."
            }
        }
    }

    fun exportData(onResult: (String) -> Unit) {
        val user = _activeUser.value
        if (user.isBlank()) return
        viewModelScope.launch {
            val json = repository.exportUserDataJson(user)
            onResult(json)
        }
    }

    fun signOut() {
        val user = _activeUser.value
        viewModelScope.launch {
            if (user.isNotBlank()) {
                repository.signOut(user)
            }
            _activeUser.value = ""
            _statusMsg.value = "Signed out."
            cancelOAuth()
            StreakWidget().updateAll(getApplication())
        }
    }

    fun checkForUpdates(onResult: (com.ayush.streakforge.data.UpdateCheckResult) -> Unit) {
        viewModelScope.launch {
            val result = repository.checkForUpdates()
            onResult(result)
        }
    }

    fun deleteData() {
        val user = _activeUser.value
        if (user.isBlank()) return
        viewModelScope.launch {
            repository.deleteUserData(user)
            _activeUser.value = ""
            _statusMsg.value = "All data for @$user deleted."
            cancelOAuth()
            StreakWidget().updateAll(getApplication())
        }
    }
}
