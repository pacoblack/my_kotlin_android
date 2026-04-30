package com.find.gang.app.widget

import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.view.ViewCompat
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.snackbar.Snackbar


class BottomNavigationBehavior : CoordinatorLayout.Behavior<BottomNavigationView> {
  constructor() : super()

  constructor(context: Context?, attrs: AttributeSet?) : super(context, attrs)

  @SuppressLint("RestrictedApi")
  override fun layoutDependsOn(parent: CoordinatorLayout, child: BottomNavigationView, dependency: View): Boolean {
    return dependency is Snackbar.SnackbarLayout
  }

  override fun onStartNestedScroll(
    coordinatorLayout: CoordinatorLayout,
    child: BottomNavigationView,
    directTargetChild: View,
    target: View,
    axes: Int,
    type: Int
  ): Boolean {
    return axes == ViewCompat.SCROLL_AXIS_VERTICAL
  }

  override fun onNestedPreScroll(
    coordinatorLayout: CoordinatorLayout,
    child: BottomNavigationView,
    target: View,
    dx: Int, dy: Int,
    consumed: IntArray,
    type: Int
  ) {
    super.onNestedPreScroll(coordinatorLayout, child, target, dx, dy, consumed, type)

    if (dy > 0) {
      // 向上滑动 - 隐藏底部导航
      hideBottomNavigationView(child)
    } else if (dy < 0) {
      // 向下滑动 - 显示底部导航
      showBottomNavigationView(child)
    }
  }

  private fun hideBottomNavigationView(view: BottomNavigationView) {
    if (view.translationY == 0f) {
      view.animate()
        .translationY(view.height.toFloat())
        .setDuration(300)
        .setInterpolator(AccelerateDecelerateInterpolator())
        .start()
    }
  }

  private fun showBottomNavigationView(view: BottomNavigationView) {
    if (view.translationY > 0) {
      view.animate()
        .translationY(0f)
        .setDuration(300)
        .setInterpolator(AccelerateDecelerateInterpolator())
        .start()
    }
  }
}