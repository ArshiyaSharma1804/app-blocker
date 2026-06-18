package com.example.appblocker.ui.main

import android.content.pm.PackageManager
import android.os.Environment
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
import com.example.appblocker.data.WhitelistManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

data class ApkItem(val file: File, val appName: String, val packageName: String, val version: String)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ApkListScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var apkList by remember { mutableStateOf<List<ApkItem>?>(null) }
    var isScanning by remember { mutableStateOf(true) }
    var selectedApk by remember { mutableStateOf<ApkItem?>(null) }
    var showSuccessMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val apks = mutableListOf<ApkItem>()
            val root = Environment.getExternalStorageDirectory()
            val pm = context.packageManager

            // Recursive walk to find all APKs on device
            try {
                root.walkTopDown().forEach { file ->
                    if (file.isFile && file.extension.equals("apk", ignoreCase = true)) {
                        try {
                            val packageInfo = pm.getPackageArchiveInfo(file.absolutePath, 0)
                            if (packageInfo != null && packageInfo.applicationInfo != null) {
                                packageInfo.applicationInfo!!.sourceDir = file.absolutePath
                                packageInfo.applicationInfo!!.publicSourceDir = file.absolutePath
                                val appName = packageInfo.applicationInfo!!.loadLabel(pm).toString()
                                apks.add(ApkItem(file, appName, packageInfo.packageName, packageInfo.versionName ?: "Unknown"))
                            }
                        } catch (e: Exception) {
                            // Skip unparseable APKs
                        }
                    }
                }
            } catch (e: Exception) {
                // Ignore access errors on certain directories
            }
            apkList = apks
            isScanning = false
        }
    }

    // Whitelist confirmation dialog
    selectedApk?.let { apk ->
        AlertDialog(
            onDismissRequest = { selectedApk = null },
            title = { Text("Add to Whitelist") },
            text = {
                Column {
                    Text("Do you want to whitelist this APK?")
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = apk.appName, fontWeight = FontWeight.Bold)
                    Text(text = apk.packageName, style = MaterialTheme.typography.bodySmall)
                    Text(text = apk.file.absolutePath, style = MaterialTheme.typography.labelSmall)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    WhitelistManager.addToWhitelist(context, apk.packageName, apk.appName, apk.file.absolutePath)
                    showSuccessMessage = apk.appName
                    selectedApk = null
                }) {
                    Text("Add to Whitelist")
                }
            },
            dismissButton = {
                TextButton(onClick = { selectedApk = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Success snackbar
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(showSuccessMessage) {
        showSuccessMessage?.let {
            snackbarHostState.showSnackbar("\"$it\" added to whitelist")
            showSuccessMessage = null
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("APK Files on Device") },
                navigationIcon = {
                    TextButton(onClick = onBack) {
                        Text("Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            )
        }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
            if (isScanning) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Scanning storage for APKs...")
                }
            } else if (apkList.isNullOrEmpty()) {
                Text(
                    "No APK files found on your device.",
                    modifier = Modifier.align(Alignment.Center)
                )
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(apkList!!) { apkItem ->
                        val isWhitelisted = WhitelistManager.isPackageWhitelisted(context, apkItem.packageName)
                        ApkItemCard(apkItem, isWhitelisted) {
                            selectedApk = apkItem
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ApkItemCard(apkItem: ApkItem, isWhitelisted: Boolean, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = if (isWhitelisted) {
            CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
        } else {
            CardDefaults.cardColors()
        }
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = apkItem.appName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                if (isWhitelisted) {
                    Text(
                        text = "WHITELISTED",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Text(text = apkItem.packageName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = "Path: ${apkItem.file.absolutePath}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(text = "Version: ${apkItem.version}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
