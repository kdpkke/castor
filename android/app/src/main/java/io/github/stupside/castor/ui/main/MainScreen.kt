package io.github.stupside.castor.ui.main

import android.content.ClipData
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import castor.v1.DeviceOuterClass.*
import castor.v1.DeviceOuterClass.Target
import io.github.stupside.castor.BrowserResolver
import io.github.stupside.castor.R
import kotlinx.coroutines.launch

@Composable
fun MainScreen(
  viewModel: MainScreenViewModel,
  browser: BrowserResolver?,
  sharedUrl: String,
  close: () -> Unit,
  restart: () -> Unit = {},
) {
  val state by viewModel.uiState.collectAsStateWithLifecycle()
  val clipboard = LocalClipboard.current
  val scope = rememberCoroutineScope()
  var url by rememberSaveable(sharedUrl) { mutableStateOf(sharedUrl) }
  var page by rememberSaveable { mutableStateOf(true) }
  var address by rememberSaveable { mutableStateOf("") }
  var deviceId by rememberSaveable { mutableStateOf("") }
  var family by rememberSaveable { mutableStateOf(DeviceType.DEVICE_TYPE_DLNA) }
  var height by rememberSaveable { mutableStateOf("1080") }
  var subtitles by rememberSaveable { mutableStateOf("off") }
  val extracting = browser?.active?.collectAsStateWithLifecycle()?.value == true
  Column(
    Modifier.fillMaxSize()
      .safeDrawingPadding()
      .verticalScroll(rememberScrollState())
      .padding(24.dp),
    verticalArrangement = Arrangement.spacedBy(16.dp),
  ) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Image(painterResource(R.drawable.ic_launcher_foreground), null, Modifier.size(72.dp))
      Column {
        Text("Castor", style = MaterialTheme.typography.headlineLarge)
        Text("Web video. On your TV.", style = MaterialTheme.typography.titleMedium)
      }
    }
    Text(
      "Everything runs on this phone. Connect to the same Wi-Fi as your TV.",
      style = MaterialTheme.typography.bodyMedium,
    )
    HorizontalDivider()
    Text("1 · Choose a video", style = MaterialTheme.typography.titleLarge)
    OutlinedTextField(
      url,
      { url = it },
      label = { Text("Video or page URL") },
      modifier = Modifier.fillMaxWidth(),
      keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
      enabled = state.castId == null,
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      FilterChip(
        page,
        { page = true },
        label = { Text("Web page") },
        enabled = state.castId == null,
      )
      FilterChip(
        !page,
        { page = false },
        label = { Text("Direct stream") },
        enabled = state.castId == null,
      )
    }
    Text("2 · Choose your TV", style = MaterialTheme.typography.titleLarge)
    OutlinedButton(
      onClick = { viewModel.discover() },
      enabled = state.ready && !state.discovering,
    ) {
      if (state.discovering) {
        CircularProgressIndicator(Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
      }
      Text(if (state.discovering) "Searching…" else "Find TVs")
    }
    state.devices.forEach { device ->
      FilterChip(
        deviceId == device.id,
        {
          deviceId = device.id
          address = ""
        },
        label = { Text("${device.name} · ${device.type.name.removePrefix("DEVICE_TYPE_")}") },
      )
    }
    OutlinedTextField(
      address,
      {
        address = it
        deviceId = ""
      },
      label = { Text("Or enter TV IP / description URL") },
      supportingText = {
        Text("Use the full DLNA description URL if the TV does not answer discovery.")
      },
      modifier = Modifier.fillMaxWidth(),
      singleLine = true,
    )
    if (deviceId.isEmpty()) {
      FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(
            DeviceType.DEVICE_TYPE_DLNA,
            DeviceType.DEVICE_TYPE_CHROMECAST,
            DeviceType.DEVICE_TYPE_ROKU,
          )
          .forEach { type ->
            FilterChip(
              family == type,
              { family = type },
              label = { Text(type.name.removePrefix("DEVICE_TYPE_")) },
            )
          }
      }
    }
    OutlinedTextField(
      height,
      { height = it },
      label = { Text("Maximum video height (pixels)") },
      singleLine = true,
      keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
      modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
      subtitles,
      { subtitles = it },
      label = { Text("Subtitles: off, auto, or language code") },
      supportingText = {
        Text(
          "Speech subtitles download a model on first use and require served playback, such as DLNA.",
        )
      },
      singleLine = true,
      modifier = Modifier.fillMaxWidth(),
    )
    Text(
      state.status,
      modifier = Modifier.fillMaxWidth(),
      style = MaterialTheme.typography.bodyLarge,
    )
    state.error?.let { error ->
      SelectionContainer { Text(error, color = MaterialTheme.colorScheme.error) }
      TextButton(
        onClick = {
          scope.launch {
            clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("Castor error", error)))
          }
        },
      ) {
        Text("Copy error")
      }
    }
    if (!state.ready && state.error != null) {
      OutlinedButton(onClick = restart) { Text("Restart Castor") }
    }
    if (state.castId == null) {
      Button(
        onClick = {
          val target =
            Target.newBuilder()
              .apply {
                if (deviceId.isNotBlank()) setDeviceId(deviceId)
                else
                  setPinned(Target.Pinned.newBuilder().setType(family).setAddress(address.trim()))
              }
              .build()
          viewModel.cast(url.trim(), page, target, height, subtitles.trim())
        },
        enabled = state.ready && !state.submitting,
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
      ) {
        Text(if (state.submitting) "Connecting…" else "Cast video")
      }
    } else
      OutlinedButton(onClick = { viewModel.stop() }, modifier = Modifier.fillMaxWidth()) {
        Text("Stop casting")
      }
    if (extracting) {
      Text("Page browser", style = MaterialTheme.typography.titleMedium)
      AndroidView(
        factory = {
          browser.webView.also { (it.parent as? android.view.ViewGroup)?.removeView(it) }
        },
        modifier = Modifier.fillMaxWidth().height(320.dp),
        onRelease = { (it.parent as? android.view.ViewGroup)?.removeView(it) },
      )
    }
    Text(
      "Use content you are authorized to access. Protected DRM video is unsupported.",
      style = MaterialTheme.typography.bodySmall,
    )
    TextButton(onClick = close) { Text("Stop Castor") }
  }
}
