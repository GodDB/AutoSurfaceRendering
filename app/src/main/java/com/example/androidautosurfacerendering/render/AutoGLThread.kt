package com.example.androidautosurfacerendering.render

import android.opengl.GLSurfaceView
import android.util.Log
import java.lang.ref.WeakReference
import javax.microedition.khronos.egl.EGL10
import javax.microedition.khronos.egl.EGL11
import javax.microedition.khronos.opengles.GL10

internal class AutoGLThread internal constructor(
    /**
     * Set once at thread construction time, nulled out when the parent view is garbage
     * called. This weak reference allows the GLSurfaceView to be garbage collected while
     * the GLThread is still alive.
     */
    private val mGLSurfaceViewWeakRef: WeakReference<AutoSurfaceRenderer>
) : Thread() {

    override fun run() {
        name = "AutoGLThread $id"
        if (AutoSurfaceRenderer.LOG_THREADS) {
            Log.e("godgod", "AutoGLThread - starting tid=$id")
        }

        try {
            guardedRun()
        } catch (e: InterruptedException) {
            // fall thru and exit normally
        } finally {
            AutoSurfaceRenderer.sGLThreadManager.threadExiting(this)
        }
    }

    /*
         * This private method should only be called inside a
         * synchronized(sGLThreadManager) block.
         */
    private fun stopEglSurfaceLocked() {
        if (mHaveEglSurface) {
            mHaveEglSurface = false
            mEglHelper!!.destroySurface()
        }
    }

    /*
         * This private method should only be called inside a
         * synchronized(sGLThreadManager) block.
         */
    private fun stopEglContextLocked() {
        if (mHaveEglContext) {
            mEglHelper?.finish()
            mHaveEglContext = false
            AutoSurfaceRenderer.sGLThreadManager.releaseEglContextLocked(this)
        }
    }

    @Throws(InterruptedException::class)
    private fun guardedRun() {
        mEglHelper = AutoGLHelper(mGLSurfaceViewWeakRef)
        mHaveEglContext = false
        mHaveEglSurface = false
        mWantRenderNotification = false

        try {
            var gl: GL10? = null
            var createEglContext = false
            var createEglSurface = false
            var createGlInterface = false
            var lostEglContext = false
            var sizeChanged = false
            var wantRenderNotification = false
            var doRenderNotification = false
            var askedToReleaseEglContext = false
            var w = 0
            var h = 0
            var event: Runnable? = null
            var finishDrawingRunnable: Runnable? = null

            while (true) {
                synchronized(AutoSurfaceRenderer.sGLThreadManager) {
                    while (true) {
                        if (mShouldExit) {
                            return
                        }

                        if (mEventQueue.isNotEmpty()) {
                            event = mEventQueue.removeAt(0)
                            break
                        }

                        // Update the pause state.
                        var pausing = false
                        if (mPaused != mRequestPaused) {
                            pausing = mRequestPaused
                            mPaused = mRequestPaused
                            (AutoSurfaceRenderer.sGLThreadManager as Object).notifyAll()
                            if (AutoSurfaceRenderer.LOG_PAUSE_RESUME) {
                                Log.e("godgod", "AutoGLThread mPaused is now $mPaused tid=$id")
                            }
                        }

                        // Do we need to give up the EGL context?
                        if (mShouldReleaseEglContext) {
                            if (AutoSurfaceRenderer.LOG_SURFACE) {
                                Log.e("godgod", "AutoGLThread releasing EGL context because asked to tid=$id")
                            }
                            stopEglSurfaceLocked()
                            stopEglContextLocked()
                            mShouldReleaseEglContext = false
                            askedToReleaseEglContext = true
                        }

                        // Have we lost the EGL context?
                        if (lostEglContext) {
                            stopEglSurfaceLocked()
                            stopEglContextLocked()
                            lostEglContext = false
                        }

                        // When pausing, release the EGL surface:
                        if (pausing && mHaveEglSurface) {
                            if (AutoSurfaceRenderer.LOG_SURFACE) {
                                Log.e(
                                    "godgod",
                                    "AutoGLThread  releasing EGL surface because paused tid=$id"
                                )
                            }
                            stopEglSurfaceLocked()
                        }

                        // When pausing, optionally release the EGL Context:
                        if (pausing && mHaveEglContext) {
                            val renderer = mGLSurfaceViewWeakRef.get()
                            val preserveEglContextOnPause =
                                renderer?.mPreserveEGLContextOnPause ?: false
                            if (!preserveEglContextOnPause) {
                                stopEglContextLocked()
                                if (AutoSurfaceRenderer.LOG_SURFACE) {
                                    Log.e(
                                        "godgod",
                                        "AutoGLThread  releasing EGL context because paused tid=$id"
                                    )
                                }
                            }
                        }

                        // Have we lost the SurfaceView surface?
                        if ((!mHasSurface) && (!mWaitingForSurface)) {
                            if (AutoSurfaceRenderer.LOG_SURFACE) {
                                Log.e(
                                    "godgod",
                                    "AutoGLThread  noticed surfaceView surface lost tid=$id"
                                )
                            }
                            if (mHaveEglSurface) {
                                stopEglSurfaceLocked()
                            }
                            mWaitingForSurface = true
                            mSurfaceIsBad = false
                            (AutoSurfaceRenderer.sGLThreadManager as Object).notifyAll()
                        }

                        // Have we acquired the surface view surface?
                        if (mHasSurface && mWaitingForSurface) {
                            if (AutoSurfaceRenderer.LOG_SURFACE) {
                                Log.e(
                                    "godgod",
                                    "AutoGLThread  noticed surfaceView surface acquired tid=$id"
                                )
                            }
                            mWaitingForSurface = false
                            (AutoSurfaceRenderer.sGLThreadManager as Object).notifyAll()
                        }

                        if (doRenderNotification) {
                            if (AutoSurfaceRenderer.LOG_SURFACE) {
                                Log.e(
                                    "godgod",
                                    "AutoGLThread   sending render notification tid=$id"
                                )
                            }
                            mWantRenderNotification = false
                            doRenderNotification = false
                            mRenderComplete = true
                            (AutoSurfaceRenderer.sGLThreadManager as Object).notifyAll()
                        }

                        if (mFinishDrawingRunnable != null) {
                            finishDrawingRunnable = mFinishDrawingRunnable
                            mFinishDrawingRunnable = null
                        }

                        // Ready to draw?
                        if (readyToDraw()) {
                            // If we don't have an EGL context, try to acquire one.

                            if (!mHaveEglContext) {
                                if (askedToReleaseEglContext) {
                                    askedToReleaseEglContext = false
                                } else {
                                    try {
                                        mEglHelper!!.start()
                                    } catch (t: RuntimeException) {
                                        AutoSurfaceRenderer.sGLThreadManager.releaseEglContextLocked(
                                            this
                                        )
                                        throw t
                                    }
                                    mHaveEglContext = true
                                    createEglContext = true

                                    (AutoSurfaceRenderer.sGLThreadManager as Object).notifyAll()
                                }
                            }

                            if (mHaveEglContext && !mHaveEglSurface) {
                                mHaveEglSurface = true
                                createEglSurface = true
                                createGlInterface = true
                                sizeChanged = true
                            }

                            if (mHaveEglSurface) {
                                if (mSizeChanged) {
                                    sizeChanged = true
                                    w = mWidth
                                    h = mHeight
                                    mWantRenderNotification = true
                                    if (AutoSurfaceRenderer.LOG_SURFACE) {
                                        Log.e(
                                            "godgod",
                                            "AutoGLThread  noticing that we want render notification tid="
                                                    + id
                                        )
                                    }

                                    // Destroy and recreate the EGL surface.
                                    createEglSurface = true

                                    mSizeChanged = false
                                }
                                mRequestRender = false
                                (AutoSurfaceRenderer.sGLThreadManager as Object).notifyAll()
                                if (mWantRenderNotification) {
                                    wantRenderNotification = true
                                }
                                break
                            }
                        } else {
                            if (finishDrawingRunnable != null) {
                                Log.e(
                                    AutoSurfaceRenderer.TAG,
                                    "Warning, !readyToDraw() but waiting for " +
                                            "draw finished! Early reporting draw finished."
                                )
                                finishDrawingRunnable!!.run()
                                finishDrawingRunnable = null
                            }
                        }
                        // By design, this is the only place in a GLThread thread where we wait().
                        if (AutoSurfaceRenderer.LOG_THREADS) {
                            Log.e(
                                "godgod", "AutoGLThread  waiting tid=" + id
                                        + " mHaveEglContext: " + mHaveEglContext
                                        + " mHaveEglSurface: " + mHaveEglSurface
                                        + " mFinishedCreatingEglSurface: " + mFinishedCreatingEglSurface
                                        + " mPaused: " + mPaused
                                        + " mHasSurface: " + mHasSurface
                                        + " mSurfaceIsBad: " + mSurfaceIsBad
                                        + " mWaitingForSurface: " + mWaitingForSurface
                                        + " mWidth: " + mWidth
                                        + " mHeight: " + mHeight
                                        + " mRequestRender: " + mRequestRender
                                        + " mRenderMode: " + mRenderMode
                            )
                        }
                        (AutoSurfaceRenderer.sGLThreadManager as Object).wait()
                    }
                } // end of synchronized(sGLThreadManager)

                if (event != null) {
                    event!!.run()
                    event = null
                    continue
                }

                if (createEglSurface) {
                    if (AutoSurfaceRenderer.LOG_SURFACE) {
                        Log.e("godgod", "AutoGLThread  egl createSurface")
                    }
                    if (mEglHelper!!.createSurface()) {
                        synchronized(AutoSurfaceRenderer.sGLThreadManager) {
                            mFinishedCreatingEglSurface = true
                            (AutoSurfaceRenderer.sGLThreadManager as Object).notifyAll()
                        }
                    } else {
                        synchronized(AutoSurfaceRenderer.sGLThreadManager) {
                            mFinishedCreatingEglSurface = true
                            mSurfaceIsBad = true
                            (AutoSurfaceRenderer.sGLThreadManager as Object).notifyAll()
                        }
                        continue
                    }
                    createEglSurface = false
                }

                if (createGlInterface) {
                    gl = mEglHelper!!.createGL() as GL10

                    createGlInterface = false
                }

                if (createEglContext) {
                    if (AutoSurfaceRenderer.LOG_RENDERER) {
                        Log.e("godgod", "AutoGLThread  ThronSurfaceCreated")
                    }
                    val renderer = mGLSurfaceViewWeakRef.get()
                    if (renderer != null) {
                        try {
                            // Trace.traceBegin(Trace.TRACE_TAG_VIEW, "onSurfaceCreated")
                            renderer.onSurfaceCreated(gl, mEglHelper!!.mEglConfig)
                        } finally {
                            // Trace.traceEnd(Trace.TRACE_TAG_VIEW)
                        }
                    }
                    createEglContext = false
                }

                if (sizeChanged) {
                    if (AutoSurfaceRenderer.LOG_RENDERER) {
                        Log.e("godgod", "AutoGLThread  onSurfaceChanged($w, $h)")
                    }
                    val renderer = mGLSurfaceViewWeakRef.get()
                    if (renderer != null) {
                        try {
                            //  Trace.traceBegin(Trace.TRACE_TAG_VIEW, "onSurfaceChanged")
                            renderer.onSurfaceChanged(gl, w, h)
                        } finally {
                            // Trace.traceEnd(Trace.TRACE_TAG_VIEW)
                        }
                    }
                    sizeChanged = false
                }

                if (AutoSurfaceRenderer.LOG_RENDERER_DRAW_FRAME) {
                    Log.w("godgod", "AutoGLThread  onDrawFrame tid=$id")
                }
                run {
                    val renderer = mGLSurfaceViewWeakRef.get()
                    if (renderer != null) {
                        try {
                            /*Trace.traceBegin(
                                Trace.TRACE_TAG_VIEW,
                                "onDrawFrame"
                            )*/
                            renderer.onDrawFrame(gl)
                            if (finishDrawingRunnable != null) {
                                finishDrawingRunnable!!.run()
                                finishDrawingRunnable = null
                            }
                        } finally {
                            //     Trace.traceEnd(Trace.TRACE_TAG_VIEW)
                        }
                    }
                }
                val swapError: Int = mEglHelper!!.swap()
                when (swapError) {
                    EGL10.EGL_SUCCESS -> {}
                    EGL11.EGL_CONTEXT_LOST -> {
                        if (AutoSurfaceRenderer.LOG_SURFACE) {
                            Log.e("godgod", "AutoGLThread  egl context lost tid=$id")
                        }
                        lostEglContext = true
                    }

                    else -> {
                        // Other errors typically mean that the current surface is bad,
                        // probably because the SurfaceView surface has been destroyed,
                        // but we haven't been notified yet.
                        // Log the error to help developers understand why rendering stopped.
                        AutoGLHelper.logEglErrorAsWarning("godgod", "AutoGLThread  eglSwapBuffers",
                            swapError
                        )

                        synchronized(AutoSurfaceRenderer.sGLThreadManager) {
                            mSurfaceIsBad = true
                            (AutoSurfaceRenderer.sGLThreadManager as Object).notifyAll()
                        }
                    }
                }
                if (wantRenderNotification) {
                    doRenderNotification = true
                    wantRenderNotification = false
                }
            }
        } finally {
            /*
                 * clean-up everything...
                 */
            synchronized(AutoSurfaceRenderer.sGLThreadManager) {
                stopEglSurfaceLocked()
                stopEglContextLocked()
            }
        }
    }

    fun ableToDraw(): Boolean {
        return mHaveEglContext && mHaveEglSurface && readyToDraw()
    }

    private fun readyToDraw(): Boolean {
        return ((!mPaused) && mHasSurface && (!mSurfaceIsBad)
                && (mWidth > 0) && (mHeight > 0)
                && (mRequestRender || (mRenderMode == GLSurfaceView.RENDERMODE_CONTINUOUSLY)))
    }

    var renderMode: Int
        get() {
            synchronized(AutoSurfaceRenderer.sGLThreadManager) {
                return mRenderMode
            }
        }
        set(renderMode) {
            require(((GLSurfaceView.RENDERMODE_WHEN_DIRTY <= renderMode) && (renderMode <= GLSurfaceView.RENDERMODE_CONTINUOUSLY))) { "renderMode" }
            synchronized(AutoSurfaceRenderer.sGLThreadManager) {
                mRenderMode = renderMode
                (AutoSurfaceRenderer.sGLThreadManager as Object).notifyAll()
            }
        }

    fun requestRender() {
        synchronized(AutoSurfaceRenderer.sGLThreadManager) {
            mRequestRender = true
            (AutoSurfaceRenderer.sGLThreadManager as Object).notifyAll()
        }
    }

    fun requestRenderAndNotify(finishDrawing: Runnable?) {
        synchronized(AutoSurfaceRenderer.sGLThreadManager) {
            // If we are already on the GL thread, this means a client callback
            // has caused reentrancy, for example via updating the SurfaceView parameters.
            // We will return to the client rendering code, so here we don't need to
            // do anything.
            if (currentThread() === this) {
                return
            }

            mWantRenderNotification = true
            mRequestRender = true
            mRenderComplete = false
            val oldCallback = mFinishDrawingRunnable
            mFinishDrawingRunnable = Runnable {
                oldCallback?.run()
                finishDrawing?.run()
            }
            (AutoSurfaceRenderer.sGLThreadManager as Object).notifyAll()
        }
    }

    fun surfaceCreated() {
        synchronized(AutoSurfaceRenderer.sGLThreadManager) {
            if (AutoSurfaceRenderer.LOG_THREADS) {
                Log.e("godgod", "AutoGLThread  surfaceCreated tid=$id")
            }
            mHasSurface = true
            mFinishedCreatingEglSurface = false
            (AutoSurfaceRenderer.sGLThreadManager as Object).notifyAll()
            while (mWaitingForSurface
                && !mFinishedCreatingEglSurface
                && !mExited
            ) {
                try {
                    (AutoSurfaceRenderer.sGLThreadManager as Object).wait()
                } catch (e: InterruptedException) {
                    currentThread().interrupt()
                }
            }
        }
    }

    fun surfaceDestroyed() {
        synchronized(AutoSurfaceRenderer.sGLThreadManager) {
            if (AutoSurfaceRenderer.LOG_THREADS) {
                Log.i("godgod", "AutoGLThread  surfaceDestroyed tid=$id")
            }
            mHasSurface = false
            (AutoSurfaceRenderer.sGLThreadManager as Object).notifyAll()
            while ((!mWaitingForSurface) && (!mExited)) {
                try {
                    (AutoSurfaceRenderer.sGLThreadManager as Object).wait()
                } catch (e: InterruptedException) {
                    currentThread().interrupt()
                }
            }
        }
    }

    fun onPause() {
        synchronized(AutoSurfaceRenderer.sGLThreadManager) {
            if (AutoSurfaceRenderer.LOG_PAUSE_RESUME) {
                Log.e("godgod", "AutoGLThread  onPause tid=$id")
            }
            mRequestPaused = true
            (AutoSurfaceRenderer.sGLThreadManager as Object).notifyAll()
            while ((!mExited) && (!mPaused)) {
                if (AutoSurfaceRenderer.LOG_PAUSE_RESUME) {
                    Log.e("godgod", "MAinThread  onPause waiting for mPaused.")
                }
                try {
                    (AutoSurfaceRenderer.sGLThreadManager as Object).wait()
                } catch (ex: InterruptedException) {
                    currentThread().interrupt()
                }
            }
        }
    }

    fun onResume() {
        synchronized(AutoSurfaceRenderer.sGLThreadManager) {
            if (AutoSurfaceRenderer.LOG_PAUSE_RESUME) {
                Log.i("godgod", "AutoGLThread  onResume tid=$id")
            }
            mRequestPaused = false
            mRequestRender = true
            mRenderComplete = false
            (AutoSurfaceRenderer.sGLThreadManager as Object).notifyAll()
            while ((!mExited) && mPaused && (!mRenderComplete)) {
                if (AutoSurfaceRenderer.LOG_PAUSE_RESUME) {
                    Log.i("godgod", "AutoGLThreadd onResume waiting for !mPaused.")
                }
                try {
                    (AutoSurfaceRenderer.sGLThreadManager as Object).wait()
                } catch (ex: InterruptedException) {
                    currentThread().interrupt()
                }
            }
        }
    }

    fun onWindowResize(w: Int, h: Int) {
        synchronized(AutoSurfaceRenderer.sGLThreadManager) {
            mWidth = w
            mHeight = h
            mSizeChanged = true
            mRequestRender = true
            mRenderComplete = false

            // If we are already on the GL thread, this means a client callback
            // has caused reentrancy, for example via updating the SurfaceView parameters.
            // We need to process the size change eventually though and update our EGLSurface.
            // So we set the parameters and return so they can be processed on our
            // next iteration.
            if (currentThread() === this) {
                return
            }

            (AutoSurfaceRenderer.sGLThreadManager as Object).notifyAll()

            // Wait for thread to react to resize and render a frame
            while (!mExited && !mPaused && !mRenderComplete
                && ableToDraw()
            ) {
                if (AutoSurfaceRenderer.LOG_SURFACE) {
                    Log.i(
                        "godgod",
                        "AutoGLThread  onWindowResize waiting for render complete from tid=$id"
                    )
                }
                try {
                    (AutoSurfaceRenderer.sGLThreadManager as Object).wait()
                } catch (ex: InterruptedException) {
                    currentThread().interrupt()
                }
            }
        }
    }

    fun requestExitAndWait() {
        // don't call this from GLThread thread or it is a guaranteed
        // deadlock!
        synchronized(AutoSurfaceRenderer.sGLThreadManager) {
            mShouldExit = true
            (AutoSurfaceRenderer.sGLThreadManager as Object).notifyAll()
            while (!mExited) {
                try {
                    (AutoSurfaceRenderer.sGLThreadManager as Object).wait()
                } catch (ex: InterruptedException) {
                    currentThread().interrupt()
                }
            }
        }
    }

    fun requestReleaseEglContextLocked() {
        mShouldReleaseEglContext = true
        (AutoSurfaceRenderer.sGLThreadManager as Object).notifyAll()
    }

    /**
     * Queue an "event" to be run on the GL rendering thread.
     * @param r the runnable to be run on the GL rendering thread.
     */
    fun queueEvent(r: Runnable?) {
        requireNotNull(r) { "r must not be null" }
        synchronized(AutoSurfaceRenderer.sGLThreadManager) {
            mEventQueue.add(r)
            (AutoSurfaceRenderer.sGLThreadManager as Object).notifyAll()
        }
    }

    // Once the thread is started, all accesses to the following member
    // variables are protected by the sGLThreadManager monitor
    private var mShouldExit = false
    internal var mExited = false
    private var mRequestPaused = false
    private var mPaused = false
    private var mHasSurface = false
    private var mSurfaceIsBad = false
    private var mWaitingForSurface = false
    private var mHaveEglContext = false
    private var mHaveEglSurface = false
    private var mFinishedCreatingEglSurface = false
    private var mShouldReleaseEglContext = false
    private var mWidth = 0
    private var mHeight = 0
    private var mRenderMode: Int
    private var mRequestRender = true
    private var mWantRenderNotification = false
    private var mRenderComplete = false
    private val mEventQueue = ArrayList<Runnable>()
    private var mSizeChanged = true
    private var mFinishDrawingRunnable: Runnable? = null

    // End of member variables protected by the sGLThreadManager monitor.
    private var mEglHelper: AutoGLHelper? = null

    init {
        mRenderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
    }
}