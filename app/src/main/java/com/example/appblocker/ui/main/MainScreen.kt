package com.example.appblocker.ui.main

import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.appblocker.data.AppInfo
import com.example.appblocker.data.AppSource
import com.example.appblocker.data.DefaultDataRepository
import com.example.appblocker.data.InstallLogManager
import com.example.appblocker.theme.AppBlockerTheme

import android.content.ComponentName
import android.text.TextUtils
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.appblocker.ApkBlockerAccessibilityService

fun isAccessibilityServiceEnabled(context: android.content.Context): Boolean {
    val expectedComponentName = ComponentName(context, ApkBlockerAccessibilityService::class.java)
    val enabledServicesSetting = Settings.Secure.getString(
        context.contentResolver,
        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
    ) ?: return false

    val colonSplitter = TextUtils.SimpleStringSplitter(':')
    colonSplitter.setString(enabledServicesSetting)
    while (colonSplitter.hasNext()) {
        val componentNameString = colonSplitter.next()
        val enabledService = ComponentName.unflattenFromString(componentNameString)
        if (enabledService != null && enabledService == expectedComponentName) {
            return true
        }
    }
    return false
}

enum class ActiveScreen { MAIN, APK_LIST, WHITELIST }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
  modifier: Modifier = Modifier,
) {
  val context = LocalContext.current
  val viewModel: MainScreenViewModel = viewModel { MainScreenViewModel(DefaultDataRepository(context)) }
  val state by viewModel.uiState.collectAsStateWithLifecycle()

  var activeScreen by remember { mutableStateOf(ActiveScreen.MAIN) }

  val permissionLauncher = rememberLauncherForActivityResult(
      contract = ActivityResultContracts.StartActivityForResult()
  ) {
      if (Environment.isExternalStorageManager()) {
          activeScreen = ActiveScreen.APK_LIST
      }
  }

  // Sub-screens
  when (activeScreen) {
      ActiveScreen.APK_LIST -> {
          ApkListScreen { activeScreen = ActiveScreen.MAIN }
          return
      }
      ActiveScreen.WHITELIST -> {
          WhitelistScreen { activeScreen = ActiveScreen.MAIN }
          return
      }
      ActiveScreen.MAIN -> { /* fall through to main UI below */ }
  }

  val lifecycleOwner = LocalLifecycleOwner.current
  var isServiceEnabled by remember { mutableStateOf(isAccessibilityServiceEnabled(context)) }

  DisposableEffect(lifecycleOwner) {
      val observer = LifecycleEventObserver { _, event ->
          if (event == Lifecycle.Event.ON_RESUME) {
              isServiceEnabled = isAccessibilityServiceEnabled(context)
          }
      }
      lifecycleOwner.lifecycle.addObserver(observer)
      onDispose {
          lifecycleOwner.lifecycle.removeObserver(observer)
      }
  }

  AppBlockerTheme(darkTheme = false) {
      Surface(
          modifier = modifier.fillMaxSize(),
          color = MaterialTheme.colorScheme.background
      ) {
          Scaffold(
              topBar = {
                  TopAppBar(
                      title = { Text("App Blocker") },
                      actions = {
                          // APK button — scan device for .apk files
                          TextButton(onClick = {
                              if (Environment.isExternalStorageManager()) {
                                  activeScreen = ActiveScreen.APK_LIST
                              } else {
                                  val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                                  intent.data = Uri.parse("package:${context.packageName}")
                                  permissionLauncher.launch(intent)
                              }
                          }) {
                              Text("APK")
                          }
                          // WTL button — view whitelisted APKs
                          TextButton(onClick = {
                              activeScreen = ActiveScreen.WHITELIST
                          }) {
                              Text("WTL")
                          }
                      },
                      colors = TopAppBarDefaults.topAppBarColors(
                          containerColor = MaterialTheme.colorScheme.primaryContainer,
                          titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                      )
                  )
              }
          ) { innerPadding ->
              Column(
                  modifier = Modifier
                      .padding(innerPadding)
                      .fillMaxSize()
              ) {
                  // Accessibility Service Status Warning
                  if (!isServiceEnabled) {
                      Card(
                          modifier = Modifier
                              .fillMaxWidth()
                              .padding(16.dp)
                              .clickable {
                                  context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                              },
                          colors = CardDefaults.cardColors(
                              containerColor = MaterialTheme.colorScheme.errorContainer
                          )
                      ) {
                          Column(modifier = Modifier.padding(16.dp)) {
                              Text(
                                  text = "Action Required",
                                  fontWeight = FontWeight.Bold,
                                  color = MaterialTheme.colorScheme.onErrorContainer
                              )
                              Text(
                                  text = "Please tap here to enable 'App Blocker' in Accessibility Settings to block unknown installations.",
                                  color = MaterialTheme.colorScheme.onErrorContainer
                              )
                          }
                      }
                  }

                  when (state) {
                      MainScreenUiState.Loading -> {
                          Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                              CircularProgressIndicator()
                          }
                      }
                      is MainScreenUiState.Success -> {
                          AppListTabs(data = (state as MainScreenUiState.Success).data)
                      }
                      is MainScreenUiState.Error -> {
                          Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                              Text(
                                  text = "Error loading data: ${(state as MainScreenUiState.Error).throwable.message}",
                                  color = MaterialTheme.colorScheme.error
                              )
                          }
                      }
                  }
              }
          }
      }
  }
}

@Composable
fun AppListTabs(data: List<AppInfo>) {
    var selectedTabIndex by remember { mutableIntStateOf(0) }
    val tabs = listOf("All Apps", "Play Store", "System", "Unknown Sources")

    Column {
        ScrollableTabRow(selectedTabIndex = selectedTabIndex) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTabIndex == index,
                    onClick = { selectedTabIndex = index },
                    text = { Text(title) }
                )
            }
        }

        val filteredData = when (selectedTabIndex) {
            0 -> data                                           // All Apps
            1 -> data.filter { it.source == AppSource.PLAY_STORE }  // Play Store
            2 -> data.filter { it.source == AppSource.SYSTEM }      // System
            3 -> data.filter { it.source == AppSource.UNKNOWN }     // Unknown Sources
            else -> data
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (filteredData.isEmpty()) {
                item {
                    Text(
                        text = "No apps found.",
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                items(filteredData) { appInfo ->
                    AppItemRow(appInfo, showBlockedCount = selectedTabIndex == 3)
                }
            }
        }
    }
}

@Composable
fun AppItemRow(appInfo: AppInfo, showBlockedCount: Boolean = false) {
    val context = LocalContext.current

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = appInfo.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = appInfo.packageName,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))

            val sourceLabel = when (appInfo.source) {
                AppSource.PLAY_STORE -> "Source: Google Play Store"
                AppSource.SYSTEM -> "Source: System / Pre-installed"
                AppSource.UNKNOWN -> "Source: Unknown / Sideloaded"
            }
            val sourceColor = when (appInfo.source) {
                AppSource.PLAY_STORE -> MaterialTheme.colorScheme.primary
                AppSource.SYSTEM -> MaterialTheme.colorScheme.tertiary
                AppSource.UNKNOWN -> MaterialTheme.colorScheme.error
            }
            Text(
                text = sourceLabel,
                style = MaterialTheme.typography.labelMedium,
                color = sourceColor
            )

            // Show blocked attempt count on the Unknown Sources tab
            if (showBlockedCount) {
                val blockedCount = InstallLogManager.getBlockedCount(context, appInfo.name)
                if (blockedCount > 0) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Blocked $blockedCount time${if (blockedCount != 1) "s" else ""}",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}
