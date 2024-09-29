package com.example.androidautosurfacerendering.render

import android.opengl.EGL14
import android.opengl.EGLExt
import android.opengl.GLSurfaceView
import javax.microedition.khronos.egl.EGL10
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.egl.EGLDisplay

internal abstract class AutoBaseConfigChooser(configSpec: IntArray, ) : GLSurfaceView.EGLConfigChooser {

    var mEGLContextClientVersion : Int = 2

    override fun chooseConfig(egl: EGL10, display: EGLDisplay?): EGLConfig {
        val num_config = IntArray(1)
        require(
            egl.eglChooseConfig(
                display, mConfigSpec, null, 0,
                num_config
            )
        ) { "eglChooseConfig failed" }

        val numConfigs = num_config[0]

        require(numConfigs > 0) { "No configs match configSpec" }

        val configs = arrayOfNulls<EGLConfig>(numConfigs)
        require(
            egl.eglChooseConfig(
                display, mConfigSpec, configs, numConfigs,
                num_config
            )
        ) { "eglChooseConfig#2 failed" }
        val config = chooseConfig(egl, display, configs)
            ?: throw IllegalArgumentException("No config chosen")
        return config
    }

    abstract fun chooseConfig(
        egl: EGL10?, display: EGLDisplay?,
        configs: Array<EGLConfig?>?
    ): EGLConfig?

    protected var mConfigSpec: IntArray

    init {
        mConfigSpec = filterConfigSpec(configSpec)
    }

    private fun filterConfigSpec(configSpec: IntArray): IntArray {
        if (mEGLContextClientVersion != 2 && mEGLContextClientVersion != 3) {
            return configSpec
        }
        /* We know none of the subclasses define EGL_RENDERABLE_TYPE.
             * And we know the configSpec is well formed.
             */
        val len = configSpec.size
        val newConfigSpec = IntArray(len + 2)
        System.arraycopy(configSpec, 0, newConfigSpec, 0, len - 1)
        newConfigSpec[len - 1] = EGL10.EGL_RENDERABLE_TYPE
        if (mEGLContextClientVersion == 2) {
            newConfigSpec[len] = EGL14.EGL_OPENGL_ES2_BIT /* EGL_OPENGL_ES2_BIT */
        } else {
            newConfigSpec[len] = EGLExt.EGL_OPENGL_ES3_BIT_KHR /* EGL_OPENGL_ES3_BIT_KHR */
        }
        newConfigSpec[len + 1] = EGL10.EGL_NONE
        return newConfigSpec
    }
}

/**
 * Choose a configuration with exactly the specified r,g,b,a sizes,
 * and at least the specified depth and stencil sizes.
 */
internal open class AutoComponentSizeChooser(// Subclasses can adjust these values:
    protected var mRedSize: Int,
    protected var mGreenSize: Int,
    protected var mBlueSize: Int,
    protected var mAlphaSize: Int,
    protected var mDepthSize: Int,
    protected var mStencilSize: Int
) : AutoBaseConfigChooser(
    intArrayOf(
        EGL10.EGL_RED_SIZE,
        mRedSize,
        EGL10.EGL_GREEN_SIZE, mGreenSize,
        EGL10.EGL_BLUE_SIZE, mBlueSize,
        EGL10.EGL_ALPHA_SIZE, mAlphaSize,
        EGL10.EGL_DEPTH_SIZE, mDepthSize,
        EGL10.EGL_STENCIL_SIZE, mStencilSize,
        EGL10.EGL_NONE
    )
) {

    override fun chooseConfig(
        egl: EGL10?,
        display: EGLDisplay?,
        configs: Array<EGLConfig?>?
    ): EGLConfig? {
        for (config in configs!!) {
            val d = findConfigAttrib(
                egl!!, display!!, config!!,
                EGL10.EGL_DEPTH_SIZE, 0
            )
            val s = findConfigAttrib(
                egl, display, config,
                EGL10.EGL_STENCIL_SIZE, 0
            )
            if ((d >= mDepthSize) && (s >= mStencilSize)) {
                val r = findConfigAttrib(
                    egl, display, config,
                    EGL10.EGL_RED_SIZE, 0
                )
                val g = findConfigAttrib(
                    egl, display, config,
                    EGL10.EGL_GREEN_SIZE, 0
                )
                val b = findConfigAttrib(
                    egl, display, config,
                    EGL10.EGL_BLUE_SIZE, 0
                )
                val a = findConfigAttrib(
                    egl, display, config,
                    EGL10.EGL_ALPHA_SIZE, 0
                )
                if ((r == mRedSize) && (g == mGreenSize)
                    && (b == mBlueSize) && (a == mAlphaSize)
                ) {
                    return config
                }
            }
        }
        return null
    }

    private fun findConfigAttrib(
        egl: EGL10, display: EGLDisplay,
        config: EGLConfig, attribute: Int, defaultValue: Int
    ): Int {
        if (egl.eglGetConfigAttrib(display, config, attribute, mValue)) {
            return mValue[0]
        }
        return defaultValue
    }

    private val mValue = IntArray(1)
}

/**
 * This class will choose a RGB_888 surface with
 * or without a depth buffer.
 *
 */
internal class AutoSimpleEGLConfigChooser(withDepthBuffer: Boolean) : AutoComponentSizeChooser(8, 8, 8, 0, if (withDepthBuffer) 16 else 0, 0)