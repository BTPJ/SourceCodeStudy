package com.btpj.retrofitstudy

import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

/**
 * Retrofit请求网络测试类
 * @author LTP  2023/7/17
 */
class RetrofitUnitTest {

    /**创建retrofit对象 */
    private val retrofit = Retrofit.Builder().baseUrl("https://www.wanandroid.com")
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    @Test
    fun getBanner() {
        val api = retrofit.create(Api::class.java)
        runBlocking {
            val response = api.getBanner()
            println(response)
        }
    }
}