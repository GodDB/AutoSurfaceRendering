package com.example.androidautosurfacerendering.render

import android.util.Log

internal class AutoGLThreadManager {
    @Synchronized
    fun threadExiting(thread: AutoGLThread) {
        if (AutoSurfaceRendererHolder.LOG_THREADS) {
            Log.i("GLThread", "exiting tid=" + thread.id)
        }
        thread.mExited = true

        (this as Object).notifyAll()
    }

    /*
         * Releases the EGL context. Requires that we are already in the
         * sGLThreadManager monitor when this is called.
         */
    fun releaseEglContextLocked(thread: AutoGLThread?) {
        (this as Object).notifyAll()
    }

    companion object {
        private const val TAG = "GLThreadManager"
    }
}