package com.btpj.retrofitstudy

import retrofit2.http.GET

/**
 * 接口服务
 * @author LTP  2023/7/18
 */
interface Api {

    /** 获取首页banner数据 */
    @GET("banner/json")
    suspend fun getBanner(): ApiResponse<List<Banner>>
}