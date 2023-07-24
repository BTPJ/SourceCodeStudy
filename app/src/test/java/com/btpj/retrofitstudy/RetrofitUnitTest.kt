package com.btpj.retrofitstudy

import kotlinx.coroutines.runBlocking
import org.junit.Test
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
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
        // runBlocking启动的协程会阻塞住线程
        runBlocking {
            val response = api.getBanner()
            println(response)

            val response2 = api.getBanner2()
            println(response2.body())
        }

        // Retrofit异步请求
        api.getBanner3().enqueue(object : Callback<ApiResponse<List<Banner>>> {
            override fun onResponse(
                call: Call<ApiResponse<List<Banner>>>,
                response: Response<ApiResponse<List<Banner>>>
            ) {
                println(response)
            }

            override fun onFailure(call: Call<ApiResponse<List<Banner>>>, t: Throwable) {
            }
        })
        // 异步请求需要阻塞住主线程以等待请求完毕
        Thread.sleep(3000)

        // Retrofit同步请求
        val response = api.getBanner3().execute()
        println(response)
    }
}