package io.github.lijoeallen.kkdatastore

import android.app.Application
import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import io.github.lijoeallen.kk.PersistedStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "kkdatastore_prefs")

class App : Application() {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        PersistedStore.attach(dataStore, appScope)
        // 等首批数据落表，保证属性同步读即时生效（之后由 Flow 收集器持续同步）
        runBlocking { PersistedStore.awaitFirstLoad() }
    }
}
