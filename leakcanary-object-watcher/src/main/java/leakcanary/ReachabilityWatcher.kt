package leakcanary

/** 函数接口（只包含一个抽象方法,主要为了实现lambda表达式） */
fun interface ReachabilityWatcher {

  /**
   * Expects the provided [watchedObject] to become weakly reachable soon. If not,
   * [watchedObject] will be considered retained.
   */
  fun expectWeaklyReachable(
    watchedObject: Any,
    description: String
  )
}