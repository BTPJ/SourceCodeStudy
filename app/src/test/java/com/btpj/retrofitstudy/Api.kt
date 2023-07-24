package com.btpj.retrofitstudy

import retrofit2.Call
import retrofit2.Response
import retrofit2.SkipCallbackExecutor
import retrofit2.http.GET

/**
 * 接口服务
 * @author LTP  2023/7/18
 */
interface Api {

    /** 获取首页banner数据（协程方式返回无包装的实体） */
    @GET("banner/json")
    suspend fun getBanner(): ApiResponse<List<Banner>>

    /** 获取首页banner数据（使用协程返回Response包装的实体） */
    @GET("banner/json")
    suspend fun getBanner2(): Response<ApiResponse<List<Banner>>>

    /** 获取首页banner数据（普通方式返回Call包装的实体） */
    @GET("banner/json")
    fun getBanner3(): Call<ApiResponse<List<Banner>>>
}