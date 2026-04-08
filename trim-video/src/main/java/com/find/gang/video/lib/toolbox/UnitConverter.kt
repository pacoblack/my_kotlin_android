package com.find.gang.video.lib.toolbox

import android.content.Context
import android.util.TypedValue

object UnitConverter {

    /**
     * 将 dp 转换为 px
     * @param dp dp 值
     * @param context 上下文，用于获取 resources
     * @return 对应的 px 值
     */
    fun dpToPx(dp: Int, context: Context): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp.toFloat(),
            context.resources.displayMetrics
        ).toInt()
    }

    // 重载支持 Float
    fun dpToPx(dp: Float, context: Context): Float {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp,
            context.resources.displayMetrics
        )
    }
}
