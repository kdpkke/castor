package io.github.stupside.castor

import android.Manifest
import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import io.github.stupside.castor.theme.CastorTheme
import io.github.stupside.castor.ui.main.MainScreen
import io.github.stupside.castor.ui.main.MainScreenViewModel

class MainActivity : ComponentActivity() {
  private var service by mutableStateOf<CastorService?>(null)
  private var permissionDenied by mutableStateOf(false)
  private var bound = false
  private lateinit var model: MainScreenViewModel
  private val permissions =
    registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
      permissionDenied =
        Build.VERSION.SDK_INT >= 37 &&
          granted["android.permission.ACCESS_LOCAL_NETWORK"] != true &&
          checkSelfPermission("android.permission.ACCESS_LOCAL_NETWORK") !=
            PackageManager.PERMISSION_GRANTED
      if (!permissionDenied) startEngine()
    }
  private val connection =
    object : ServiceConnection {
      override fun onServiceConnected(name: ComponentName, binder: IBinder) {
        service = (binder as CastorService.LocalBinder).service
      }

      override fun onServiceDisconnected(name: ComponentName) {
        service = null
        model.connect(null)
      }
    }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    model = ViewModelProvider(this)[MainScreenViewModel::class.java]
    enableEdgeToEdge()
    setContent {
      LaunchedEffect(service) { service?.endpoint?.collect { model.connect(it) } }
      LaunchedEffect(service) {
        service?.failure?.collect { message ->
          message?.let { model.fail(IllegalStateException(it)) }
        }
      }
      CastorTheme {
        LaunchedEffect(service) {
          val current = service ?: return@LaunchedEffect
          current.stopped.collect { stopped ->
            if (stopped) {
              if (bound) {
                unbindService(connection)
                bound = false
              }
              service = null
              model.connect(null)
              model.fail(
                IllegalStateException(current.failure.value ?: "The casting engine stopped."),
              )
            }
          }
        }
        Surface(Modifier.fillMaxSize()) {
          if (permissionDenied)
            Column(
              Modifier.safeDrawingPadding().padding(24.dp),
              verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
              Text("Allow access to your TV", style = MaterialTheme.typography.headlineMedium)
              Text("Castor needs local network access to discover TVs and send them video.")
              Button(onClick = { requestPermissions() }) { Text("Allow local network") }
              TextButton(
                onClick = {
                  startActivity(
                    Intent(
                      android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                      android.net.Uri.parse("package:$packageName"),
                    ),
                  )
                },
              ) {
                Text("Open app settings")
              }
            }
          else
            MainScreen(
              model,
              service?.browser,
              intent.getStringExtra(Intent.EXTRA_TEXT).orEmpty(),
              restart = { startEngine() },
              close = {
                stopService(Intent(this, CastorService::class.java))
                model.connect(null)
                finish()
              },
            )
        }
      }
    }
    requestPermissions()
  }

  private fun requestPermissions() {
    val needed = buildList {
      if (
        Build.VERSION.SDK_INT >= 37 &&
          checkSelfPermission("android.permission.ACCESS_LOCAL_NETWORK") !=
            PackageManager.PERMISSION_GRANTED
      )
        add("android.permission.ACCESS_LOCAL_NETWORK")
      if (
        Build.VERSION.SDK_INT >= 33 &&
          checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
      )
        add(Manifest.permission.POST_NOTIFICATIONS)
    }
    if (needed.isEmpty()) {
      permissionDenied = false
      startEngine()
    } else permissions.launch(needed.toTypedArray())
  }

  private fun startEngine() {
    if (bound) return
    val intent = Intent(this, CastorService::class.java)
    startForegroundService(intent)
    bound = bindService(intent, connection, BIND_AUTO_CREATE)
  }

  override fun onResume() {
    super.onResume()
    if (
      permissionDenied &&
        Build.VERSION.SDK_INT >= 37 &&
        checkSelfPermission("android.permission.ACCESS_LOCAL_NETWORK") ==
          PackageManager.PERMISSION_GRANTED
    ) {
      permissionDenied = false
      startEngine()
    }
  }

  override fun onDestroy() {
    if (bound) unbindService(connection)
    super.onDestroy()
  }
}
