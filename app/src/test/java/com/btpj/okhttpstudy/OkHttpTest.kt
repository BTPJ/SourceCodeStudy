package com.btpj.okhttpstudy

import android.util.Log
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.junit.Test
import java.io.IOException

/**
 * @author LTP  2023/6/29
 */
class OkHttpTest {

    private val okHttpClient = OkHttpClient()

    /**
     * 同步请求
     */
    @Test
    fun getRequestWithSync() {
        val request = Request.Builder().url("http://www.baidu.com").build()

        okHttpClient.newCall(request).execute().use {
            print(it.body?.string())
        }
    }

    /**
     * 异步请求
     */
    @Test
    fun getRequestWithASync() {
        val request = Request.Builder().url("http://www.baidu.com").build()

        okHttpClient.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                e.printStackTrace()
            }

            override fun onResponse(call: Call, response: Response) {
                print(response.body?.string()?:"123")
            }
        })

        // 得阻塞一会儿线程不让其结束以拿到请求结果
        Thread.sleep(2000)
    }
}