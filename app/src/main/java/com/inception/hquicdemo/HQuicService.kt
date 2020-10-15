package com.inception.hquicdemo

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import com.huawei.hms.hquic.HQUICManager
import com.huawei.hms.hquic.HQUICManager.HQUICInitCallback
import org.chromium.net.CronetEngine

@SuppressLint("StaticFieldLeak")

object HQuicService {

    private const val TAG = "HQuicService"
    private lateinit var context: Context
    internal lateinit var engine: CronetEngine

    @Volatile
    var enable: Boolean = true

    fun init(context: Context) {

        HQUICManager.asyncInit(context, object : HQUICInitCallback {
            override fun onSuccess() {
                Log.i(TAG, "HQUICManager asyncInit success")
            }

            override fun onFail(e: Exception) {
                Log.w(TAG, "HQUICManager asyncInit fail")
            }
        })

        init(context, defaultEngine(context))
    }

    private fun init(context: Context, engine: CronetEngine) {
        HQuicService.context = context.applicationContext
        HQuicService.engine = engine
    }

    private fun defaultEngine(context: Context) =
        CronetEngine.Builder(context.applicationContext)
            .enableQuic(true)
            .build()
}