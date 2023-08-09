package leakcanary

import android.annotation.SuppressLint
import android.app.Service
import android.os.Build
import android.os.Handler
import android.os.IBinder
import leakcanary.internal.friendly.checkMainThread
import shark.SharkLog
import java.lang.ref.WeakReference
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Proxy
import java.util.WeakHashMap

/**
 * Expects services to become weakly reachable soon after they receive the [Service.onDestroy]
 * callback.
 */
@SuppressLint("PrivateApi")
class ServiceWatcher(private val reachabilityWatcher: ReachabilityWatcher) : InstallableWatcher {

  private val servicesToBeDestroyed = WeakHashMap<IBinder, WeakReference<Service>>()

  private val activityThreadClass by lazy { Class.forName("android.app.ActivityThread") }

  private val activityThreadInstance by lazy {
    activityThreadClass.getDeclaredMethod("currentActivityThread").invoke(null)!!
  }

  private val activityThreadServices by lazy {
    val mServicesField =
      activityThreadClass.getDeclaredField("mServices").apply { isAccessible = true }

    @Suppress("UNCHECKED_CAST")
    mServicesField[activityThreadInstance] as Map<IBinder, Service>
  }

  private var uninstallActivityThreadHandlerCallback: (() -> Unit)? = null
  private var uninstallActivityManager: (() -> Unit)? = null

  override fun install() {
    checkMainThread()
    check(uninstallActivityThreadHandlerCallback == null) {
      "ServiceWatcher already installed"
    }
    check(uninstallActivityManager == null) {
      "ServiceWatcher already installed"
    }
    try {
      // Hook ActivityThread类中的mH.mCallback
      swapActivityThreadHandlerCallback { mCallback ->
        uninstallActivityThreadHandlerCallback = {
          swapActivityThreadHandlerCallback {
            mCallback
          }
        }
        Handler.Callback { msg ->
          // https://github.com/square/leakcanary/issues/2114
          // On some Motorola devices (Moto E5 and G6), the msg.obj returns an ActivityClientRecord
          // instead of an IBinder. This crashes on a ClassCastException. Adding a type check
          // here to prevent the crash.
          if (msg.obj !is IBinder) {
            return@Callback false
          }

          // 监听Service.onStop()事件消息
          if (msg.what == STOP_SERVICE) {
            val key = msg.obj as IBinder
            // activityThreadServices是通过反射获取的ActivityThread类中的mServices成员变量<IBinder, Service>
            activityThreadServices[key]?.let {
              // 服务销毁前的处理，这里主要是暂存
              onServicePreDestroy(key, it)
            }
          }
          // Hook后继续执行Framework本身的逻辑
          mCallback?.handleMessage(msg) ?: false
        }
      }

      // Hook AMS IActivityManager
      swapActivityManager { activityManagerInterface, activityManagerInstance ->
        uninstallActivityManager = {
          swapActivityManager { _, _ ->
            activityManagerInstance
          }
        }
        Proxy.newProxyInstance(
          activityManagerInterface.classLoader, arrayOf(activityManagerInterface)
        ) { _, method, args ->
          // 代理serviceDoneExecuting()方法
          if (METHOD_SERVICE_DONE_EXECUTING == method.name) {
            val token = args!![0] as IBinder
            if (servicesToBeDestroyed.containsKey(token)) {
              // 处理Service销毁，主要是将service交给ObjectWatcher进行监控
              onServiceDestroyed(token)
            }
          }
          // 继续执行serviceDoneExecuting()本身的方法
          try {
            if (args == null) {
              method.invoke(activityManagerInstance)
            } else {
              method.invoke(activityManagerInstance, *args)
            }
          } catch (invocationException: InvocationTargetException) {
            throw invocationException.targetException
          }
        }
      }
    } catch (ignored: Throwable) {
      SharkLog.d(ignored) { "Could not watch destroyed services" }
    }
  }

  override fun uninstall() {
    checkMainThread()
    uninstallActivityManager?.invoke()
    uninstallActivityThreadHandlerCallback?.invoke()
    uninstallActivityManager = null
    uninstallActivityThreadHandlerCallback = null
  }

  private fun onServicePreDestroy(
    token: IBinder,
    service: Service
  ) {
    servicesToBeDestroyed[token] = WeakReference(service)
  }

  private fun onServiceDestroyed(token: IBinder) {
    servicesToBeDestroyed.remove(token)?.also { serviceWeakReference ->
      serviceWeakReference.get()?.let { service ->
        reachabilityWatcher.expectWeaklyReachable(
          service, "${service::class.java.name} received Service#onDestroy() callback"
        )
      }
    }
  }

  /**
   * Hook修改ActivityThread类中的mH.mCallback
   * swap 是一个 lambda 表达式，参数为原对象，返回值为注入的新对象
   */
  private fun swapActivityThreadHandlerCallback(swap: (Handler.Callback?) -> Handler.Callback?) {
    val mHField =
      activityThreadClass.getDeclaredField("mH").apply { isAccessible = true }
    val mH = mHField[activityThreadInstance] as Handler

    val mCallbackField =
      Handler::class.java.getDeclaredField("mCallback").apply { isAccessible = true }
    val mCallback = mCallbackField[mH] as Handler.Callback?
    mCallbackField[mH] = swap(mCallback)
  }

  /**
   * Hook修改ActivityThread类中的mH.mCallback
   * swap 是一个 lambda 表达式，参数为 IActivityManager 的 Class 对象和接口原实现对象，返回值为注入的新对象
   */
  @SuppressLint("PrivateApi")
  private fun swapActivityManager(swap: (Class<*>, Any) -> Any) {
    val singletonClass = Class.forName("android.util.Singleton")
    val mInstanceField =
      singletonClass.getDeclaredField("mInstance").apply { isAccessible = true }

    val singletonGetMethod = singletonClass.getDeclaredMethod("get")

    val (className, fieldName) = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      "android.app.ActivityManager" to "IActivityManagerSingleton"
    } else {
      "android.app.ActivityManagerNative" to "gDefault"
    }

    val activityManagerClass = Class.forName(className)
    val activityManagerSingletonField =
      activityManagerClass.getDeclaredField(fieldName).apply { isAccessible = true }
    val activityManagerSingletonInstance = activityManagerSingletonField[activityManagerClass]

    // Calling get() instead of reading from the field directly to ensure the singleton is
    // created.
    val activityManagerInstance = singletonGetMethod.invoke(activityManagerSingletonInstance)

    val iActivityManagerInterface = Class.forName("android.app.IActivityManager")
    // 将swap的返回值作为新对象，实现 Hook
    mInstanceField[activityManagerSingletonInstance] =
      swap(iActivityManagerInterface, activityManagerInstance!!)
  }

  companion object {
    private const val STOP_SERVICE = 116

    private const val METHOD_SERVICE_DONE_EXECUTING = "serviceDoneExecuting"
  }
}