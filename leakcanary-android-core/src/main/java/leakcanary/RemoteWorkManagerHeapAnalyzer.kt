package leakcanary

import androidx.work.Data
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import androidx.work.multiprocess.RemoteListenableWorker.ARGUMENT_CLASS_NAME
import androidx.work.multiprocess.RemoteListenableWorker.ARGUMENT_PACKAGE_NAME
import leakcanary.EventListener.Event
import leakcanary.EventListener.Event.HeapDump
import leakcanary.internal.HeapAnalyzerWorker.Companion.asWorkerInputData
import leakcanary.internal.InternalLeakCanary
import leakcanary.internal.RemoteHeapAnalyzerWorker
import shark.SharkLog

/**
 * When receiving a [HeapDump] event, starts a WorkManager worker that performs heap analysis in
 * a dedicated :leakcanary process
 */
object RemoteWorkManagerHeapAnalyzer : EventListener {

  private const val REMOTE_SERVICE_CLASS_NAME = "leakcanary.internal.RemoteLeakCanaryWorkerService"

  // 这里通过RemoteLeakCanaryWorkerService这个类是否加载成功来判断
  // 是否有'com.squareup.leakcanary:leakcanary-android-process:2.9.1'这个依赖
  internal val remoteLeakCanaryServiceInClasspath by lazy {
    try {
      Class.forName(REMOTE_SERVICE_CLASS_NAME)
      true
    } catch (ignored: Throwable) {
      false
    }
  }

  override fun onEvent(event: Event) {
    if (event is HeapDump) {
      val application = InternalLeakCanary.application
      // 创建并分发 WorkManager 多进程请求
      val heapAnalysisRequest =
        OneTimeWorkRequest.Builder(RemoteHeapAnalyzerWorker::class.java).apply {
          val dataBuilder = Data.Builder()
            .putString(ARGUMENT_PACKAGE_NAME, application.packageName)
            .putString(ARGUMENT_CLASS_NAME, REMOTE_SERVICE_CLASS_NAME)
          setInputData(event.asWorkerInputData(dataBuilder))
          with(WorkManagerHeapAnalyzer) {
            addExpeditedFlag()
          }
        }.build()
      SharkLog.d { "Enqueuing heap analysis for ${event.file} on WorkManager remote worker" }
      val workManager = WorkManager.getInstance(application)
      workManager.enqueue(heapAnalysisRequest)
    }
  }
}
