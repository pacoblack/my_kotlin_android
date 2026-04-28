package com.find.gang.app.ui.dialog

import kotlinx.parcelize.Parcelize
import android.os.Parcelable
// 布局中用到 Material 组件，若项目未集成可用普通 EditText 替代

/**
 * 通用输入弹窗
 * @param requestKey 用于通过 Fragment Result API 回传结果的 key
 * @param title 弹窗标题，空则不显示
 * @param hint 输入框提示文字
 * @param prefill 预填充文本
 * @param inputType 输入类型，默认 textUri
 * @param positiveText 确定按钮文字
 * @param negativeText 取消按钮文字
 * @param validator 输入校验函数，返回 null 表示通过，返回错误信息则展示且阻止关闭
 */
@Parcelize
data class InputDialogConfig(
    val requestKey: String,
    val title: String? = "请输入",
    val hint: String = "https://...",
    val prefill: String? = null,
    val inputType: Int = android.text.InputType.TYPE_TEXT_VARIATION_URI,
    val positiveText: String = "确定",
    val negativeText: String = "取消",
    val showClipboardSuggestion: Boolean = true,
    val validator: ((String) -> String?)? = null // 返回 null 表示合法
) : Parcelable