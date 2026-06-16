package com.example.appblocker.ui.main

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.appblocker.data.AppInfo
import com.example.appblocker.data.DefaultDataRepository
import com.example.appblocker.theme.AppBlockerTheme

import android.content.ComponentName
import android.text.TextUtils
import androidx.compose.runtime.DisposableEffect
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
  modifier: Modifier = Modifier,
) {
  val context = LocalContext.current
  val viewModel: MainScreenViewModel = viewModel { MainScreenViewModel(DefaultDataRepository(context)) }
  val state by viewModel.uiState.collectAsStateWithLifecycle()
  var isDarkTheme by remember { mutableStateOf(false) }

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

  AppBlockerTheme(darkTheme = isDarkTheme) {
      Surface(
          modifier = modifier.fillMaxSize(),
          color = MaterialTheme.colorScheme.background
      ) {
          Scaffold(
              topBar = {
                  TopAppBar(
                      title = { Text("App Blocker") },
                      actions = {
                          Row(verticalAlignment = Alignment.CenterVertically) {
                              Text("Dark Mode", modifier = Modifier.padding(end = 8.dp))
                              Switch(
                                  checked = isDarkTheme,
                                  onCheckedChange = { isDarkTheme = it }
                              )
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
    val tabs = listOf("Play Store Apps", "Unknown Sources")

    Column {
        TabRow(selectedTabIndex = selectedTabIndex) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTabIndex == index,
                    onClick = { selectedTabIndex = index },
                    text = { Text(title) }
                )
            }
        }

        val filteredData = if (selectedTabIndex == 0) {
            data.filter { it.isPlayStore }
        } else {
            data.filter { !it.isPlayStore }
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
                    AppItemRow(appInfo)
                }
            }
        }
    }
}

@Composable
fun AppItemRow(appInfo: AppInfo) {
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
            Text(
                text = if (appInfo.isPlayStore) "Source: Google Play Store" else "Source: Unknown / Sideloaded",
                style = MaterialTheme.typography.labelMedium,
                color = if (appInfo.isPlayStore) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
            )
        }
    }
}
