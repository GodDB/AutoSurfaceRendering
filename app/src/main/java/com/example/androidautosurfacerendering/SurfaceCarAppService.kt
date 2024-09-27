package com.example.androidautosurfacerendering

import android.content.Intent
import androidx.car.app.CarAppService
import androidx.car.app.Screen
import androidx.car.app.Session
import androidx.car.app.validation.HostValidator

class SurfaceCarAppService : CarAppService() {

    override fun createHostValidator(): HostValidator {
        return HostValidator.ALLOW_ALL_HOSTS_VALIDATOR
    }

    override fun onCreateSession(): Session {
        return SurfaceSession()
    }

}

class SurfaceSession : Session() {
    override fun onCreateScreen(intent: Intent): Screen {
        return SurfaceScreen(carContext)
    }
}