package com.codex.streetstrength

import android.app.Application
import androidx.room.Room
import com.codex.streetstrength.data.local.AppDatabase
import com.codex.streetstrength.data.local.AppDatabaseMigrations
import com.codex.streetstrength.data.preferences.PreferencesRepository
import com.codex.streetstrength.data.repository.TrainingRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class StreetStrengthApp : Application() {

    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val database: AppDatabase by lazy {
        Room.databaseBuilder(
            applicationContext,
            AppDatabase::class.java,
            "street-strength.db",
        ).addMigrations(*AppDatabaseMigrations.ALL).build()
    }

    val preferencesRepository: PreferencesRepository by lazy {
        PreferencesRepository(applicationContext)
    }

    val trainingRepository: TrainingRepository by lazy {
        TrainingRepository(
            database = database,
            preferencesRepository = preferencesRepository,
        )
    }

    override fun onCreate() {
        super.onCreate()
        applicationScope.launch {
            trainingRepository.seedBuiltIns()
        }
    }
}
