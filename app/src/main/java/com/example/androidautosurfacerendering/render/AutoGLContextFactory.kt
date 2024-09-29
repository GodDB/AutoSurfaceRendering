package com.example.androidautosurfacerendering.render

import android.opengl.GLSurfaceView
import android.util.Log
import javax.microedition.khronos.egl.EGL10
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.egl.EGLContext
import javax.microedition.khronos.egl.EGLDisplay

internal class AutoDefaultContextFactory : GLSurfaceView.EGLContextFactory {
    private val EGL_CONTEXT_CLIENT_VERSION = 0x3098

    var mEGLContextClientVersion : Int = 2

    override fun createContext(egl: EGL10, display: EGLDisplay?, config: EGLConfig?): EGLContext {
        val attrib_list = intArrayOf(
            EGL_CONTEXT_CLIENT_VERSION, mEGLContextClientVersion,
            EGL10.EGL_NONE
        )

        return egl.eglCreateContext(
            display, config, EGL10.EGL_NO_CONTEXT,
            if (mEGLContextClientVersion != 0) attrib_list else null
        )
    }

    override fun destroyContext(
        egl: EGL10, display: EGLDisplay,
        context: EGLContext
    ) {
        if (!egl.eglDestroyContext(display, context)) {
            Log.e("DefaultContextFactory", "display:$display context: $context")
            if (AutoSurfaceRendererHolder.LOG_THREADS) {
                Log.i("DefaultContextFactory", "tid=" + Thread.currentThread().id)
            }
            AutoGLHelper.throwEglException("eglDestroyContex", egl.eglGetError())
        }
    }
}
