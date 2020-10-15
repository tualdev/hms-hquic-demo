package com.inception.hquicdemo

import okhttp3.Interceptor
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.internal.http.RealResponseBody
import okio.buffer
import okio.sink
import okio.source
import java.net.HttpURLConnection

class QUICInterceptor: Interceptor {

    private val TAG = QUICInterceptor::class.java.simpleName

    override fun intercept(chain: Interceptor.Chain): Response {
        if (!HQuicService.enable) {
            return chain.proceed(chain.request())
        }

        val req = chain.request()

        val url = req.url.toUrl()

        val connection = HQuicService.engine.openConnection(url) as HttpURLConnection

        req.headers.names().forEach {
            connection.addRequestProperty(it, req.headers[it])
        }

        connection.requestMethod = req.method

        req.body?.let {
            it.contentType()?.let {
                connection.setRequestProperty("Content-Type", it.toString())
            }

            connection.doOutput = true
            val os = connection.outputStream
            val sink = os.sink().buffer()
            it.writeTo(sink)
            sink.flush()
            os.close()
        }

        val statusCode = connection.responseCode

        if (statusCode in 300..310) {
            return chain.proceed(req)
        }

        val respBuilder = Response.Builder()
        respBuilder
            .request(req)
            .protocol(Protocol.QUIC)
            .code(statusCode)
            .message(connection.responseMessage ?: "")

        val respHeaders = connection.headerFields
        respHeaders.entries.forEach {
            it.value.forEach {
                    value ->
                //                headerBuilder[it.key] = value
                respBuilder.addHeader(it.key, value)

            }
        }

        val bodySource = (if (statusCode in 200..399) connection.inputStream else connection.errorStream).source().buffer()
        respBuilder.body(
            RealResponseBody(respHeaders["Content-Type"]?.last(),
                respHeaders["Content-Length"]?.last()?.toLong() ?: 0,
                bodySource)
        )
        val resp = respBuilder.build()

        return resp
    }

}