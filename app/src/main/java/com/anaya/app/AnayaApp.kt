package com.anaya.app

import android.app.Application
import com.anaya.app.data.local.AppDatabase
import com.anaya.app.data.local.SeedData
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

@HiltAndroidApp
class AnayaApp : Application() {

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        applicationScope.launch {
            val database = AppDatabase.getInstance(this@AnayaApp)
            SeedData.seedIfEmpty(database)
        }
    }
}
