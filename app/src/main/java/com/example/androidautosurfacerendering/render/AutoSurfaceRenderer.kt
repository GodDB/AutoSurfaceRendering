package com.example.androidautosurfacerendering.render

import android.opengl.GLSurfaceView
import android.util.Log
import android.view.Surface
import java.io.Writer
import java.lang.ref.WeakReference
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10


class AutoSurfaceRenderer(
    internal val surface: Surface,
    internal val width: Int,
    internal val height: Int
) : GLSurfaceView.Renderer {

    companion object {
        internal const val LOG_THREADS = true
        internal const val LOG_EGL = true
        internal const val LOG_PAUSE_RESUME = true
        internal const val LOG_SURFACE = true
        internal const val LOG_RENDERER = true
        internal const val LOG_RENDERER_DRAW_FRAME = true
        internal const val LOG_ATTACH_DETACH = true
        internal const val TAG = "godgod"

        internal val sGLThreadManager = AutoGLThreadManager()
    }

    internal var mPreserveEGLContextOnPause = false
    internal val mEGLConfigChooser: AutoBaseConfigChooser = AutoSimpleEGLConfigChooser(true)
    internal val mEGLWindowSurfaceFactory: GLSurfaceView.EGLWindowSurfaceFactory =
        AutoDefaultWindowSurfaceFactory()
    internal val mEGLContextFactory: AutoDefaultContextFactory = AutoDefaultContextFactory()
    internal var mGLWrapper: GLSurfaceView.GLWrapper? = null
    internal val mDebugFlags = 0
    private var mEGLContextClientVersion: Int = 2
    private val me: WeakReference<AutoSurfaceRenderer> = WeakReference(this)
    private var mGLThread: AutoGLThread? = null
    private var mDetached: Boolean = true
    var renderMode: Int = GLSurfaceView.RENDERMODE_WHEN_DIRTY
        set(value) {
            field = value
            mGLThread?.renderMode = value
        }


    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        Log.e("godgod", "onSurfaceCreated")
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        Log.e("godgod", "onSurfaceChanged  ${width}  ${height}")
    }

    override fun onDrawFrame(gl: GL10?) {
        Log.e("godgod", "onDrawFrame")
    }


    /**
     * Pause the rendering thread, optionally tearing down the EGL context
     * depending upon the value of [.setPreserveEGLContextOnPause].
     *
     * This method should be called when it is no longer desirable for the
     * GLSurfaceView to continue rendering, such as in response to
     * [Activity.onStop][android.app.Activity.onStop].
     *
     * Must not be called before a renderer has been set.
     */
    fun onPause() {
        mGLThread!!.onPause()
    }

    /**
     * Resumes the rendering thread, re-creating the OpenGL context if necessary. It
     * is the counterpart to [.onPause].
     *
     * This method should typically be called in
     * [Activity.onStart][android.app.Activity.onStart].
     *
     * Must not be called before a renderer has been set.
     */
    fun onResume() {
        mGLThread!!.onResume()
    }

    /**
     * Queue a runnable to be run on the GL rendering thread. This can be used
     * to communicate with the Renderer on the rendering thread.
     * Must not be called before a renderer has been set.
     * @param r the runnable to be run on the GL rendering thread.
     */
    fun queueEvent(r: Runnable?) {
        mGLThread!!.queueEvent(r)
    }

    fun onCreate() {
        if (AutoSurfaceRenderer.LOG_ATTACH_DETACH) {
            Log.e(AutoSurfaceRenderer.TAG, "onAttachedToWindow reattach =$mDetached")
        }
        if (mDetached) {
            mGLThread = AutoGLThread(me)
            if (renderMode != GLSurfaceView.RENDERMODE_CONTINUOUSLY) {
                mGLThread!!.renderMode = renderMode
            }
            mGLThread!!.start()
            mGLThread!!.surfaceCreated()
            mGLThread!!.onWindowResize(width, height)
        }
        mDetached = false
    }

    fun onDestroy() {
        if (AutoSurfaceRenderer.LOG_ATTACH_DETACH) {
            Log.e(AutoSurfaceRenderer.TAG, "onDetachedFromWindow")
        }
        if (mGLThread != null) {
            mGLThread!!.surfaceDestroyed()
            mGLThread!!.requestExitAndWait()
        }
        mDetached = true
    }

    fun setEGLContextClientVersion(version: Int) {
        checkRenderThreadState()
        mEGLContextClientVersion = version
        mEGLContextFactory.mEGLContextClientVersion = mEGLContextClientVersion
        mEGLConfigChooser.mEGLContextClientVersion = mEGLContextClientVersion
    }

    fun setGLWrapper(glWrapper: GLSurfaceView.GLWrapper) {
        mGLWrapper = glWrapper
    }


    private fun checkRenderThreadState() {
        check(mGLThread == null) { "setRenderer has already been called for this instance." }
    }


    class LogWriter : Writer() {
        override fun close() {
            flushBuilder()
        }

        override fun flush() {
            flushBuilder()
        }

        override fun write(buf: CharArray, offset: Int, count: Int) {
            for (i in 0 until count) {
                val c = buf[offset + i]
                if (c == '\n') {
                    flushBuilder()
                } else {
                    mBuilder.append(c)
                }
            }
        }

        private fun flushBuilder() {
            if (mBuilder.length > 0) {
                Log.v("GLSurfaceView", mBuilder.toString())
                mBuilder.delete(0, mBuilder.length)
            }
        }

        private val mBuilder = StringBuilder()
    }
}