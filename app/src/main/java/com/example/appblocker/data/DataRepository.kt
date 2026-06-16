package com.example.appblocker.data

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

data class AppInfo(
    val name: String,
    val packageName: String,
    val isPlayStore: Boolean
)

interface DataRepository {
  val data: Flow<List<AppInfo>>
}

class DefaultDataRepository(private val context: Context) : DataRepository {
  override val data: Flow<List<AppInfo>> = flow {
      val pm = context.packageManager
      val packages = pm.getInstalledPackages(0)
      val appList = mutableListOf<AppInfo>()
      
      for (packageInfo in packages) {
          val appInfoObj = packageInfo.applicationInfo ?: continue
          // Skip system apps
          if ((appInfoObj.flags and ApplicationInfo.FLAG_SYSTEM) != 0) {
              continue
          }
          val appName = appInfoObj.loadLabel(pm).toString()
          val packageName = packageInfo.packageName
          
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
          
          val isPlayStore = installer == "com.android.vending"
          appList.add(AppInfo(appName, packageName, isPlayStore))
      }
      
      emit(appList.sortedBy { it.name })
  }.flowOn(Dispatchers.IO)
}
