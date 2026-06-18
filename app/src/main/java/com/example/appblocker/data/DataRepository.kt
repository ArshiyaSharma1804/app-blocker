package com.example.appblocker.data

import android.content.Context
import android.content.pm.ApplicationInfo
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.net.HttpURLConnection
import java.net.URL

enum class AppSource { PLAY_STORE, SYSTEM, UNKNOWN }

data class AppInfo(
    val name: String,
    val packageName: String,
    val source: AppSource
)

interface DataRepository {
  val data: Flow<List<AppInfo>>
}

class DefaultDataRepository(private val context: Context) : DataRepository {
  override val data: Flow<List<AppInfo>> = flow {
      val pm = context.packageManager
      val packages = pm.getInstalledPackages(0)
      val appList = mutableListOf<AppInfo>()
      val isOnline = isNetworkAvailable(context)

      for (packageInfo in packages) {
          val appInfoObj = packageInfo.applicationInfo ?: continue
          val appName = appInfoObj.loadLabel(pm).toString()
          val packageName = packageInfo.packageName

          // --- User Requested Classification Logic ---
          val oemInstallers = listOf(
              "com.samsung.android.packageinstaller",
              "com.sec.android.app.samsungapps",
              "com.miui.packageinstaller",
              "com.xiaomi.mipicks",
              "com.coloros.packageinstaller",
              "com.oppo.packageinstaller",
              "com.realme.packageinstaller",
              "com.oneplus.packageinstaller",
              "com.heytap.market",
              "com.oppo.market",
              "com.huawei.packageinstaller",
              "com.huawei.appmarket",
              "com.honor.appmarket",
              "com.vivo.packageinstaller",
              "com.bbk.appstore",
              "com.motorola.packageinstaller",
              "com.nokia.packageinstaller",
              "com.sonyericsson.android.packageinstaller",
              "com.sony.packageinstaller",
              "com.lge.packageinstaller",
              "com.asus.packageinstaller",
              "com.amazon.venezia"
          )

          val isSystemApp = (appInfoObj.flags and ApplicationInfo.FLAG_SYSTEM) != 0
          val installer = try {
              if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                  pm.getInstallSourceInfo(packageName).installingPackageName
              } else {
                  @Suppress("DEPRECATION")
                  pm.getInstallerPackageName(packageName)
              }
          } catch (e: Exception) {
              null
          }

          var determinedSource = AppSource.UNKNOWN

          if (installer == "com.android.vending") {
              determinedSource = AppSource.PLAY_STORE
          } else if (installer == "com.google.android.packageinstaller" || installer == "com.android.packageinstaller") {
              determinedSource = AppSource.UNKNOWN
          } else if (installer == null) {
              determinedSource = AppSource.SYSTEM
          } else if (oemInstallers.contains(installer)) {
              determinedSource = if (isSystemApp) AppSource.SYSTEM else AppSource.UNKNOWN
          } else {
              determinedSource = if (isSystemApp) AppSource.SYSTEM else AppSource.UNKNOWN
          }

          // Step 3: Online Play Store URL Validation (only when online and not already verified as Play Store)
          if (isOnline && determinedSource != AppSource.PLAY_STORE && determinedSource != AppSource.SYSTEM) {
              val existsOnPlayStore = checkPlayStoreUrl(packageName)
              if (existsOnPlayStore) {
                  determinedSource = AppSource.PLAY_STORE
              }
          }

          appList.add(AppInfo(appName, packageName, determinedSource))
      }

      emit(appList.sortedBy { it.name })
  }.flowOn(Dispatchers.IO)

    companion object {
        private fun isNetworkAvailable(context: Context): Boolean {
            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val network = connectivityManager.activeNetwork ?: return false
            val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
            return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        }

        private fun checkPlayStoreUrl(packageName: String): Boolean {
            return try {
                val url = URL("https://play.google.com/store/apps/details?id=$packageName")
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.connectTimeout = 5000
                connection.readTimeout = 5000
                connection.instanceFollowRedirects = true
                // Set a browser-like User-Agent to avoid being blocked
                connection.setRequestProperty("User-Agent", "Mozilla/5.0")
                val responseCode = connection.responseCode
                connection.disconnect()
                responseCode == HttpURLConnection.HTTP_OK
            } catch (e: Exception) {
                // Network error or timeout — treat as not verified
                false
            }
        }
    }
}
