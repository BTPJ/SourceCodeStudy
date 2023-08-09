/*
 * Copyright (C) 2015 Square, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package leakcanary

import android.app.Activity
import android.app.Dialog
import android.view.View
import android.view.View.OnAttachStateChangeListener
import com.squareup.leakcanary.objectwatcher.core.R
import curtains.Curtains
import curtains.OnRootViewAddedListener
import curtains.WindowType.PHONE_WINDOW
import curtains.WindowType.POPUP_WINDOW
import curtains.WindowType.TOAST
import curtains.WindowType.TOOLTIP
import curtains.WindowType.UNKNOWN
import curtains.phoneWindow
import curtains.windowType
import curtains.wrappedCallback
import leakcanary.internal.friendly.mainHandler

/**
 * Expects root views to become weakly reachable soon after they are removed from the window
 * manager.
 */
/**
 * RootView泄漏监听，主要利用了Curtains库实现对
 */
class RootViewWatcher(
  private val reachabilityWatcher: ReachabilityWatcher
) : InstallableWatcher {

  private val listener = OnRootViewAddedListener { rootView ->
    // 判断rootView的窗口类型
    // 是否需要使用RootViewWatcher监听
    val trackDetached = when(rootView.windowType) {
      PHONE_WINDOW -> {
        when (rootView.phoneWindow?.callback?.wrappedCallback) {
          // Activities are already tracked by ActivityWatcher
          // 由于Activity已经有ActivityWatcher监听，这里直接返回false,即无需通过RootViewWatcher监听
          is Activity -> false
          is Dialog -> {
            // Use app context resources to avoid NotFoundException
            // https://github.com/square/leakcanary/issues/2137
            // 通过配置开启，默认不开启
            val resources = rootView.context.applicationContext.resources
            resources.getBoolean(R.bool.leak_canary_watcher_watch_dismissed_dialogs)
          }
          // Probably a DreamService
          // 屏保等
          else -> true
        }
      }
      // Android widgets keep detached popup window instances around.
      POPUP_WINDOW -> false
      // Tooltip、Toast等进行监听
      TOOLTIP, TOAST, UNKNOWN -> true
    }
    if (trackDetached) {
      // 注册监听事件
      rootView.addOnAttachStateChangeListener(object : OnAttachStateChangeListener {

        val watchDetachedView = Runnable {
          // 接收发送的消息，使用ObjectWatcher对rootView进行监听
          reachabilityWatcher.expectWeaklyReachable(
            rootView, "${rootView::class.java.name} received View#onDetachedFromWindow() callback"
          )
        }

        override fun onViewAttachedToWindow(v: View) {
          // 添加时移除消息
          mainHandler.removeCallbacks(watchDetachedView)
        }

        override fun onViewDetachedFromWindow(v: View) {
          // 监听RootView的移除事件，使用Handler发送消息处理
          mainHandler.post(watchDetachedView)
        }
      })
    }
  }

  override fun install() {
    // 注册RootView监听
    Curtains.onRootViewsChangedListeners += listener
  }

  override fun uninstall() {
    Curtains.onRootViewsChangedListeners -= listener
  }
}
