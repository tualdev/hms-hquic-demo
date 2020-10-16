package com.inception.hquicdemo

import android.annotation.SuppressLint
import android.os.AsyncTask
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.request.RequestOptions
import okhttp3.OkHttpClient
import okhttp3.Request
import okio.buffer
import okio.sink
import okio.source
import org.chromium.net.CronetEngine
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit


class MainActivity : AppCompatActivity() {

    private lateinit var mEngine: CronetEngine
    private lateinit var mOkhttpClent: OkHttpClient
    private lateinit var mOkhttpClentWithQUIC: OkHttpClient

    private val mTvResultHttp by lazy { findViewById<TextView>(R.id.tv_http_result) }
    private val mTvResultQuic by lazy { findViewById<TextView>(R.id.tv_quic_result) }
    private val mTvResultQuicOverOKHttp by lazy { findViewById<TextView>(R.id.tv_quic_ok_result) }

    private var imageListLyt: LinearLayout? = null

    companion object {
        val TAG = MainActivity::class.java.simpleName


        const val TEST_TYPE_QUIC = 1
        const val TEST_TYPE_HTTP = 2
        const val TEST_TYPE_QUIC_OVER_OKHTTP = 3
    }

    private val IMGS = arrayOf(
        "https://stgwhttp2.kof.qq.com/1.jpg",
        "https://stgwhttp2.kof.qq.com/2.jpg",
        "https://stgwhttp2.kof.qq.com/3.jpg",
        "https://stgwhttp2.kof.qq.com/4.jpg",
        "https://stgwhttp2.kof.qq.com/5.jpg",
        "https://stgwhttp2.kof.qq.com/6.jpg",
        "https://stgwhttp2.kof.qq.com/7.jpg",
        "https://stgwhttp2.kof.qq.com/8.jpg",
        "https://stgwhttp2.kof.qq.com/01.jpg",
        "https://stgwhttp2.kof.qq.com/02.jpg",
        "https://stgwhttp2.kof.qq.com/03.jpg",
        "https://stgwhttp2.kof.qq.com/04.jpg",
        "https://stgwhttp2.kof.qq.com/05.jpg",
        "https://stgwhttp2.kof.qq.com/06.jpg",
        "https://stgwhttp2.kof.qq.com/07.jpg",
        "https://stgwhttp2.kof.qq.com/08.jpg"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        HQuicService.init(this)

        mEngine = HQuicService.engine

        mOkhttpClentWithQUIC = OkHttpClient.Builder()
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .connectTimeout(30, TimeUnit.SECONDS)
            .cache(null)
            .addInterceptor(QUICInterceptor())
            .build()
        mOkhttpClent = OkHttpClient.Builder()
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .connectTimeout(30, TimeUnit.SECONDS)
            .cache(null)
            .build()

        imageListLyt = findViewById(R.id.imageListLyt)

        findViewById<Button>(R.id.btn_quic_test).setOnClickListener {
            clearImagesFromView()
            doTest(TEST_TYPE_QUIC)
        }

        findViewById<Button>(R.id.btn_http_test).setOnClickListener {
            clearImagesFromView()
            doTest(TEST_TYPE_HTTP)
        }
        findViewById<Button>(R.id.btn_quic_ok_test).setOnClickListener {
            clearImagesFromView()
            doTest(TEST_TYPE_QUIC_OVER_OKHTTP)
        }
    }

    @SuppressLint("StaticFieldLeak")
    private fun doTest(type: Int) {
        object : AsyncTask<Void, Void, Long>() {
            override fun doInBackground(vararg params: Void?): Long {
                Log.i(TAG, "start test:")
                var cost = 0L
                for (i in 0 until IMGS.size) {
                    try {
                        cost += when(type) {

                            TEST_TYPE_HTTP -> okhttpJob(mOkhttpClent, IMGS[i])
                            TEST_TYPE_QUIC -> quicJob(IMGS[i])
                            TEST_TYPE_QUIC_OVER_OKHTTP -> okhttpJob(mOkhttpClentWithQUIC, IMGS[i])
                            else -> 0
                        }
                    } catch (e: Throwable) {
                        Log.e(TAG, "job error", e)
                    }
                }

                return cost
            }

            override fun onPostExecute(result: Long) {
                super.onPostExecute(result)
                val label = "download img ${IMGS.size} times and cost: $result ms"
                when(type) {

                    TEST_TYPE_HTTP -> mTvResultHttp.text = label
                    TEST_TYPE_QUIC -> mTvResultQuic.text = label
                    TEST_TYPE_QUIC_OVER_OKHTTP -> mTvResultQuicOverOKHttp.text = label
                }

            }

        }.execute()
    }

    private fun quicJob(img: String) : Long {

        val time = System.currentTimeMillis()


        val connection = mEngine.openConnection(URL(img)) as HttpURLConnection

        // can also hook system default
        // URL.setURLStreamHandlerFactory(mEngine.createURLStreamHandlerFactory());

        connection.requestMethod = "GET"
        connection.connect()

        val source = connection.inputStream.source().buffer()
        val downloadedFile = File(this.externalCacheDir, "a.jpg")
        val sink = downloadedFile.sink().buffer()
        sink.writeAll(source)
        sink.close()
        source.close()

        val cost = System.currentTimeMillis() - time
        Log.i(TAG, "download complete $img cost = ${System.currentTimeMillis() - time}")

        runOnUiThread {
            addImageToView(img)
        }

        return cost
    }

    private fun okhttpJob(client: OkHttpClient, img: String) : Long {
        val time = System.currentTimeMillis()

        val req = Request.Builder()
            .url(img)
            .build()

        val resp = client.newCall(req).execute()

        val source = resp.body!!.source()
        val downloadedFile = File(this.externalCacheDir, "b.jpg")
        val sink = downloadedFile.sink().buffer()
        sink.writeAll(source)
        sink.close()
        source.close()

        val cost = System.currentTimeMillis() - time
        Log.i(TAG, "download complete $img cost = ${System.currentTimeMillis() - time}")

        runOnUiThread {
            addImageToView(img)
        }

        return cost
    }

    private fun clearImagesFromView(){
        imageListLyt?.removeAllViews()
    }

    private fun addImageToView(img: String){

        val imageView = ImageView(applicationContext)

        val params = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )

        params.setMargins(3, 3, 3, 3)
        imageView.layoutParams = params

        imageView.id = View.generateViewId()

        imageListLyt?.addView(imageView)

        loadImage(imageView, img)
    }

    private fun loadImage(imageView: ImageView, img: String){
        Glide.with(this)
            .load(img)
            .diskCacheStrategy(DiskCacheStrategy.NONE)
            .skipMemoryCache(true)
            .apply(RequestOptions().override(300, 300))
            .into(imageView)
    }
}