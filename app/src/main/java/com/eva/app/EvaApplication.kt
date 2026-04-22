package com.eva.app

import android.app.Application
import com.eva.app.data.local.TokenManager
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class EvaApplication : Application() {
    @Inject lateinit var tokenManager: TokenManager

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        appScope.launch {
            runCatching {
                tokenManager.token.collect { token ->
                    tokenManager.cachedToken = token
                }
            }.onFailure { e ->
                android.util.Log.e("EvaApplication", "Ошибка чтения токена из DataStore", e)
            }
        }
    }
}
