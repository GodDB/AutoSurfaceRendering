package com.example.androidautosurfacerendering

import android.graphics.Rect
import android.opengl.GLSurfaceView
import android.util.Log
import androidx.car.app.AppManager
import androidx.car.app.CarContext
import androidx.car.app.SurfaceCallback
import androidx.car.app.SurfaceContainer
import com.example.androidautosurfacerendering.render.AutoSurfaceRendererHolder

class SurfaceRenderer {

    private var autoSurfaceRendererHolder: AutoSurfaceRendererHolder? = null

    private val surfaceCallback: SurfaceCallback = object : SurfaceCallback {
        override fun onSurfaceAvailable(surfaceContainer: SurfaceContainer) {
            synchronized(this@SurfaceRenderer) {
                Log.e("godgod", "onSurfaceAvailable")
                autoSurfaceRendererHolder = AutoSurfaceRendererHolder(surfaceContainer.surface!!, surfaceContainer.width, surfaceContainer.height)
                autoSurfaceRendererHolder?.setEGLContextClientVersion(3)
                autoSurfaceRendererHolder?.renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
                autoSurfaceRendererHolder?.onCreate()
            }
        }

        override fun onVisibleAreaChanged(visibleArea: Rect) {
            synchronized(this@SurfaceRenderer) {
                Log.e("godgod", "onVisibleAreaChanged")
            }
        }

        override fun onStableAreaChanged(stableArea: Rect) {
            synchronized(this@SurfaceRenderer) {
                Log.e("godgod", "onStableAreaChanged")
            }
        }

        override fun onSurfaceDestroyed(surfaceContainer: SurfaceContainer) {
            synchronized(this@SurfaceRenderer) {
                Log.e("godgod", "onSurfaceDestroyed")
                autoSurfaceRendererHolder?.onDestroy()
                autoSurfaceRendererHolder = null
            }
        }

        override fun onScroll(distanceX: Float, distanceY: Float) {
            synchronized(this@SurfaceRenderer) {
                Log.e("godgod", "onScroll")
            }
        }

        override fun onScale(focusX: Float, focusY: Float, scaleFactor: Float) {
            synchronized(this@SurfaceRenderer) {
                Log.e("godgod", "onScale")
            }
        }

        override fun onFling(velocityX: Float, velocityY: Float) {
            synchronized(this@SurfaceRenderer) {
                Log.e("godgod", "onFling")
            }
        }

        override fun onClick(x: Float, y: Float) {
            super.onClick(x, y)
            Log.e("godgod", "onClick")
        }
    }

    fun init(carContext: CarContext) {
        carContext.getCarService(AppManager::class.java).setSurfaceCallback(surfaceCallback)
    }
}