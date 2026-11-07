package com.posprint.app

import android.app.Application
import com.posprint.app.data.TemplateRepository
import com.posprint.app.di.ApplicationScope
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class PosPrintApp : Application() {
    @Inject lateinit var templates: TemplateRepository
    @Inject @ApplicationScope lateinit var scope: CoroutineScope

    override fun onCreate() {
        super.onCreate()
        scope.launch { templates.seedIfFirstLaunch() } // sample templates on first launch
    }
}
