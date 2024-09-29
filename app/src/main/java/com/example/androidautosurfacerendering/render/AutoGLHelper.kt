package com.example.androidautosurfacerendering.render

import android.opengl.GLDebugHelper
import android.opengl.GLSurfaceView
import android.util.Log
import java.io.Writer
import java.lang.ref.WeakReference
import javax.microedition.khronos.egl.EGL10
import javax.microedition.khronos.egl.EGL11
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.egl.EGLContext
import javax.microedition.khronos.egl.EGLDisplay
import javax.microedition.khronos.egl.EGLSurface
import javax.microedition.khronos.opengles.GL

internal class AutoGLHelper(private val mGLSurfaceViewWeakRef: WeakReference<AutoSurfaceRendererHolder>) {

    var mEgl: EGL10? = null
    var mEglDisplay: EGLDisplay? = null
    var mEglSurface: EGLSurface? = null
    var mEglConfig: EGLConfig? = null
    var mEglContext: EGLContext? = null

    /**
     * Initialize EGL for a given configuration spec.
     * @param configSpec
     */
    fun start() {
        if (AutoSurfaceRendererHolder.LOG_EGL) {
            Log.w("EglHelper", "start() tid=" + Thread.currentThread().id)
        }
        /*
             * Get an EGL instance
             */
        mEgl = EGLContext.getEGL() as EGL10

        /*
             * Get to the default display.
             */
        mEglDisplay = mEgl!!.eglGetDisplay(EGL10.EGL_DEFAULT_DISPLAY)

        if (mEglDisplay === EGL10.EGL_NO_DISPLAY) {
            throw RuntimeException("eglGetDisplay failed")
        }

        /*
             * We can now initialize EGL for that display
             */
        val version = IntArray(2)
        if (!mEgl!!.eglInitialize(mEglDisplay, version)) {
            throw RuntimeException("eglInitialize failed")
        }
        val renderer = mGLSurfaceViewWeakRef.get()
        if (renderer == null) {
            mEglConfig = null
            mEglContext = null
        } else {
            mEglConfig = renderer.mEGLConfigChooser.chooseConfig(mEgl!!, mEglDisplay)

            /*
                * Create an EGL context. We want to do this as rarely as we can, because an
                * EGL context is a somewhat heavy object.
                */
            mEglContext = renderer.mEGLContextFactory.createContext(mEgl!!, mEglDisplay, mEglConfig)
        }
        if (mEglContext == null || mEglContext === EGL10.EGL_NO_CONTEXT) {
            mEglContext = null
            throwEglException("createContext")
        }
        if (AutoSurfaceRendererHolder.LOG_EGL) {
            Log.w("EglHelper", "createContext " + mEglContext + " tid=" + Thread.currentThread().id)
        }

        mEglSurface = null
    }

    /**
     * Create an egl surface for the current SurfaceHolder surface. If a surface
     * already exists, destroy it before creating the new surface.
     *
     * @return true if the surface was created successfully.
     */
    fun createSurface(): Boolean {
        if (AutoSurfaceRendererHolder.LOG_EGL) {
            Log.e("godgod", "EGLHelper  createSurface()  tid=" + Thread.currentThread().id)
        }
        /*
             * Check preconditions.
             */
        if (mEgl == null) {
            throw RuntimeException("egl not initialized")
        }
        if (mEglDisplay == null) {
            throw RuntimeException("eglDisplay not initialized")
        }
        if (mEglConfig == null) {
            throw RuntimeException("mEglConfig not initialized")
        }

        /*
             *  The window size has changed, so we need to create a new
             *  surface.
             */
        destroySurfaceImp()

        /*
             * Create an EGL surface we can render into.
             */
        val renderer = mGLSurfaceViewWeakRef.get()
        mEglSurface = renderer?.mEGLWindowSurfaceFactory?.createWindowSurface(
            mEgl, mEglDisplay, mEglConfig, renderer.surface
        )

        if (mEglSurface == null || mEglSurface === EGL10.EGL_NO_SURFACE) {
            val error = mEgl!!.eglGetError()
            if (error == EGL10.EGL_BAD_NATIVE_WINDOW) {
                Log.e("godgod", "EglHelper  createWindowSurface returned EGL_BAD_NATIVE_WINDOW.")
            }
            return false
        }

        /*
             * Before we can issue GL commands, we need to make sure
             * the context is current and bound to a surface.
             */
        if (!mEgl!!.eglMakeCurrent(mEglDisplay, mEglSurface, mEglSurface, mEglContext)) {
            /*
                 * Could not make the context current, probably because the underlying
                 * SurfaceView surface has been destroyed.
                 */
            logEglErrorAsWarning("godgod", "EGLHelper  eglMakeCurrent", mEgl!!.eglGetError())
            return false
        }

        return true
    }

    /**
     * Create a GL object for the current EGL context.
     * @return
     */
    fun createGL(): GL {
        var gl = mEglContext!!.gl
        val view = mGLSurfaceViewWeakRef.get()
        if (view != null) {
            if (view.mGLWrapper != null) {
                gl = view.mGLWrapper!!.wrap(gl)
            }

            if ((view.mDebugFlags and (GLSurfaceView.DEBUG_CHECK_GL_ERROR or GLSurfaceView.DEBUG_LOG_GL_CALLS)) != 0) {
                var configFlags = 0
                var log: Writer? = null
                if ((view.mDebugFlags and GLSurfaceView.DEBUG_CHECK_GL_ERROR) != 0) {
                    configFlags = configFlags or GLDebugHelper.CONFIG_CHECK_GL_ERROR
                }
                if ((view.mDebugFlags and GLSurfaceView.DEBUG_LOG_GL_CALLS) != 0) {
                    log = AutoSurfaceRendererHolder.LogWriter()
                }
                gl = GLDebugHelper.wrap(gl, configFlags, log)
            }
        }
        return gl
    }

    /**
     * Display the current render surface.
     * @return the EGL error code from eglSwapBuffers.
     */
    fun swap(): Int {
        if (!mEgl!!.eglSwapBuffers(mEglDisplay, mEglSurface)) {
            return mEgl!!.eglGetError()
        }
        return EGL10.EGL_SUCCESS
    }

    fun destroySurface() {
        if (AutoSurfaceRendererHolder.LOG_EGL) {
            Log.e("godgod", "EGLHelper   destroySurface()  tid=" + Thread.currentThread().id)
        }
        destroySurfaceImp()
    }

    private fun destroySurfaceImp() {
        if (mEglSurface != null && mEglSurface !== EGL10.EGL_NO_SURFACE) {
            mEgl!!.eglMakeCurrent(
                mEglDisplay, EGL10.EGL_NO_SURFACE,
                EGL10.EGL_NO_SURFACE,
                EGL10.EGL_NO_CONTEXT
            )
            val view = mGLSurfaceViewWeakRef.get()
            view?.mEGLWindowSurfaceFactory?.destroySurface(mEgl, mEglDisplay, mEglSurface)
            mEglSurface = null
        }
    }

    fun finish() {
        if (AutoSurfaceRendererHolder.LOG_EGL) {
            Log.e("godgod", "EglHelper   finish() tid=" + Thread.currentThread().id)
        }
        if (mEglContext != null) {
            val view = mGLSurfaceViewWeakRef.get()
            view?.mEGLContextFactory?.destroyContext(mEgl!!, mEglDisplay!!, mEglContext!!)
            mEglContext = null
        }
        if (mEglDisplay != null) {
            mEgl!!.eglTerminate(mEglDisplay)
            mEglDisplay = null
        }
    }

    private fun throwEglException(function: String) {
        throwEglException(function, mEgl!!.eglGetError())
    }

    companion object {
        fun throwEglException(function: String, error: Int) {
            val message = formatEglError(function, error)
            if (AutoSurfaceRendererHolder.LOG_THREADS) {
                Log.e(
                    "godgod", "EglHelper  throwEglException tid=" + Thread.currentThread().id + " "
                            + message
                )
            }
            throw RuntimeException(message)
        }

        fun logEglErrorAsWarning(tag: String?, function: String, error: Int) {
            Log.e(tag, formatEglError(function, error))
        }

        fun formatEglError(function: String, error: Int): String {
            return function + " failed: " + getErrorString(error)
        }

        fun getErrorString(error: Int): String {
            return when (error) {
                EGL10.EGL_SUCCESS -> "EGL_SUCCESS"
                EGL10.EGL_NOT_INITIALIZED -> "EGL_NOT_INITIALIZED"
                EGL10.EGL_BAD_ACCESS -> "EGL_BAD_ACCESS"
                EGL10.EGL_BAD_ALLOC -> "EGL_BAD_ALLOC"
                EGL10.EGL_BAD_ATTRIBUTE -> "EGL_BAD_ATTRIBUTE"
                EGL10.EGL_BAD_CONFIG -> "EGL_BAD_CONFIG"
                EGL10.EGL_BAD_CONTEXT -> "EGL_BAD_CONTEXT"
                EGL10.EGL_BAD_CURRENT_SURFACE -> "EGL_BAD_CURRENT_SURFACE"
                EGL10.EGL_BAD_DISPLAY -> "EGL_BAD_DISPLAY"
                EGL10.EGL_BAD_MATCH -> "EGL_BAD_MATCH"
                EGL10.EGL_BAD_NATIVE_PIXMAP -> "EGL_BAD_NATIVE_PIXMAP"
                EGL10.EGL_BAD_NATIVE_WINDOW -> "EGL_BAD_NATIVE_WINDOW"
                EGL10.EGL_BAD_PARAMETER -> "EGL_BAD_PARAMETER"
                EGL10.EGL_BAD_SURFACE -> "EGL_BAD_SURFACE"
                EGL11.EGL_CONTEXT_LOST -> "EGL_CONTEXT_LOST"
                else -> "0x" + Integer.toHexString(error);
            }
        }

    }
}