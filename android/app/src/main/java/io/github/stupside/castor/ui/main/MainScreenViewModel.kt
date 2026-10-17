package io.github.stupside.castor.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import castor.v1.CastOuterClass.*
import castor.v1.DeviceOuterClass.*
import castor.v1.DeviceOuterClass.Target
import castor.v1.StreamOuterClass.Stream
import io.github.stupside.castor.BrowserResolver
import io.github.stupside.castor.data.DataRepository
import io.github.stupside.castor.data.Rpc
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

data class MainScreenUiState(
  val ready: Boolean = false,
  val discovering: Boolean = false,
  val devices: List<Device> = emptyList(),
  val castId: String? = null,
  val submitting: Boolean = false,
  val status: String = "Starting the casting engine…",
  val error: String? = null,
)

class MainScreenViewModel : ViewModel() {
  val uiState = MutableStateFlow(MainScreenUiState())
  private var repository: DataRepository? = null
  private var watch: Job? = null
  private var discovery: Job? = null
  private var submission: Job? = null

  fun connect(api: Rpc?) {
    watch?.cancel()
    discovery?.cancel()
    submission?.cancel()
    repository = api?.let(::DataRepository)
    uiState.value =
      if (api != null) MainScreenUiState(ready = true, status = "Ready to cast")
      else
        uiState.value.copy(
          ready = false,
          castId = null,
          submitting = false,
          status = "Casting engine unavailable",
        )
    val data = repository
    if (data != null)
      viewModelScope.launch {
        runCatching { data.casts().firstOrNull() }
          .onSuccess { cast -> if (cast != null) follow(cast.id) }
          .onFailure { fail(it) }
      }
  }

  fun fail(error: Throwable) {
    if (error !is kotlinx.coroutines.CancellationException)
      uiState.value = uiState.value.copy(error = error.message ?: "Casting failed")
  }

  fun discover() {
    if (discovery?.isActive == true) return
    discovery = viewModelScope.launch {
      val data = repository ?: return@launch
      uiState.value = uiState.value.copy(discovering = true, error = null)
      try {
        val devices = data.devices()
        uiState.value =
          uiState.value.copy(
            devices = devices,
            status =
              if (devices.isEmpty()) "No TVs found. Check Wi-Fi or enter the TV address."
              else "Select a TV",
          )
      } catch (error: Exception) {
        fail(error)
      } finally {
        uiState.value = uiState.value.copy(discovering = false)
      }
    }
  }

  fun cast(url: String, page: Boolean, target: Target, maxHeight: String, subtitles: String) {
    if (submission?.isActive == true || uiState.value.castId != null) return
    submission = viewModelScope.launch {
      val data = repository ?: return@launch
      try {
        val request = request(url, page, target, maxHeight, subtitles)
        uiState.value = uiState.value.copy(error = null, status = "Connecting…", submitting = true)
        follow(data.cast(request))
      } catch (error: Exception) {
        fail(error)
      } finally {
        uiState.value = uiState.value.copy(submitting = false)
      }
    }
  }

  private fun follow(id: String) {
    watch?.cancel()
    uiState.value = uiState.value.copy(castId = id)
    val data = repository ?: return
    watch = viewModelScope.launch {
      try {
        data.watch(id) { update ->
          if (update.hasEnded()) {
            val result = update.ended
            uiState.value =
              uiState.value.copy(
                castId = null,
                status =
                  when {
                    result.hasStopped() -> "Stopped"
                    result.hasCompleted() -> "Playback completed or handed off to the TV"
                    else -> "Casting failed"
                  },
                error = if (result.hasFailed()) result.failed.message else null,
              )
          } else {
            val status = update.status
            val text =
              when {
                status.hasConnecting() -> "Connecting to the TV…"
                status.hasExtracting() -> "Finding video… You can start playback in the browser."
                status.hasMeasuring() -> "Checking ${status.measuring.streams} streams…"
                status.hasCasting() -> "Casting · attempt ${status.casting.attempt}"
                else -> "Waiting for status…"
              }
            uiState.value = uiState.value.copy(status = text)
          }
        }
      } catch (error: Exception) {
        fail(error)
      }
    }
  }

  fun stop() = viewModelScope.launch {
    val id = uiState.value.castId ?: return@launch
    try {
      repository?.stop(id)
    } catch (error: Exception) {
      fail(error)
    }
  }

  companion object {
    internal fun request(
      url: String,
      page: Boolean,
      target: Target,
      maxHeight: String,
      subtitles: String = "off",
    ): CastRequest {
      require(BrowserResolver.isHttp(url) && url.length <= 8192) {
        "Enter a complete HTTP or HTTPS video URL."
      }
      val height = maxHeight.toIntOrNull()
      require(height != null && height >= 2) {
        "Video height must be a whole number of at least 2 pixels."
      }
      require(
        target.hasDeviceId() && target.deviceId.isNotBlank() ||
          target.hasPinned() && target.pinned.address.isNotBlank(),
      ) {
        "Select a TV or enter its address."
      }
      val source = Source.newBuilder()
      if (page) source.pages = Source.Pages.newBuilder().addUrls(url).build()
      else source.stream = Stream.newBuilder().setUrl(url).build()
      val selection = SubtitleSelection.newBuilder()
      when (subtitles) {
        "off" -> selection.setDisabled(com.google.protobuf.Empty.getDefaultInstance())
        "auto" -> selection.setAutoDetect(com.google.protobuf.Empty.getDefaultInstance())
        else -> {
          require(Regex("^[a-z]{2,3}$").matches(subtitles)) {
            "Use off, auto, or a two- or three-letter subtitle language code."
          }
          selection.setLanguage(subtitles)
        }
      }
      return CastRequest.newBuilder()
        .setTarget(target)
        .setSource(source)
        .setPreferences(Preferences.newBuilder().setMaxHeight(height).setSubtitles(selection))
        .build()
    }
  }
}
