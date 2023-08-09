package leakcanary

import android.app.Activity
import android.app.Application
import leakcanary.internal.friendly.noOpDelegate

/**
 * Expects activities to become weakly reachable soon after they receive the [Activity.onDestroy]
 * callback.
 */
/** Activity的泄漏监听 */
class ActivityWatcher(
  private val application: Application,
  private val reachabilityWatcher: ReachabilityWatcher
) : InstallableWatcher {

  private val lifecycleCallbacks =
    object : Application.ActivityLifecycleCallbacks by noOpDelegate() {
      override fun onActivityDestroyed(activity: Activity) {
        // 监听Activity.onDestroy()交给ObjectWatcher分析
        reachabilityWatcher.expectWeaklyReachable(
          activity, "${activity::class.java.name} received Activity#onDestroy() callback"
        )
      }
    }

  override fun install() {
    application.registerActivityLifecycleCallbacks(lifecycleCallbacks)
  }

  override fun uninstall() {
    application.unregisterActivityLifecycleCallbacks(lifecycleCallbacks)
  }
}
