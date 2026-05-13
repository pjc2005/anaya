package com.anaya.app

import android.app.Application
import com.anaya.app.data.local.AppDatabase
import com.anaya.app.data.local.SeedData
import com.anaya.app.ml.LocalModelManager
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class AnayaApp : Application() {

    @Inject lateinit var localModel: LocalModelManager

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        applicationScope.launch {
            val database = AppDatabase.getInstance(this@AnayaApp)
            SeedData.seedIfEmpty(database)

            // 预启动本地 0.5B 模型服务
            // 首次启动会从 assets 解压 llama-server 二进制和 GGUF 模型到 filesDir
            localModel.ensureServerRunning()
        }
    }
}
