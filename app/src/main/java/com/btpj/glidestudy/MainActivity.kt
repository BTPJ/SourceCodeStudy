package com.btpj.glidestudy

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import com.bumptech.glide.Glide

/**
 * Glide源码学习测试页面（下载Glide源码，只导入需要的module）
 *
 * @author LTP 2023/8/15
 */
class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        Glide.with(this).load("https://t7.baidu.com/it/u=1595072465,3644073269&fm=193&f=GIF")
            .into(findViewById(R.id.iv))
    }
}