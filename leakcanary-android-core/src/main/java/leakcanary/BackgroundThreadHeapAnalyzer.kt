package leakcanary

import android.os.Handler
import android.os.HandlerThread
import leakcanary.EventListener.Event
import leakcanary.EventListener.Event.HeapDump
import leakcanary.internal.AndroidDebugHeapAnalyzer
import leakcanary.internal.InternalLeakCanary

/**
 * Starts heap analysis on a background [HandlerThread] when receiving a [HeapDump] event.
 */
object BackgroundThreadHeapAnalyzer : EventListener {

  internal val heapAnalyzerThreadHandler by lazy {
    val handlerThread = HandlerThread("HeapAnalyzer")
    handlerThread.start()
    Handler(handlerThread.looper)
  }

  override fun onEvent(event: Event) {
    if (event is HeapDump) {
      heapAnalyzerThreadHandler.post {
        // 分析堆快照
        val doneEvent = AndroidDebugHeapAnalyzer.runAnalysisBlocking(event) { event ->
          // 发送分析进度事件
          InternalLeakCanary.sendEvent(event)
        }
        // 发送分析完成事件
        InternalLeakCanary.sendEvent(doneEvent)
      }
    }
  }
}
