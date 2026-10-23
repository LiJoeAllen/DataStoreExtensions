package io.github.lijoeallen.datastoreextensions.demo

import android.app.Application
import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import io.github.lijoeallen.datastoreextensions.PersistedStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking

/** DataStore 持久化文件名（存储契约，改名即丢数据） */
private const val DATASTORE_NAME = "kkdatastore_prefs"

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = DATASTORE_NAME)

class App : Application() {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        PersistedStore.attach(dataStore, appScope)
        // 等首批数据落表，保证属性同步读即时生效（之后由 Flow 收集器持续同步）
        runBlocking { PersistedStore.awaitFirstLoad() }
    }
}
