package com.find.gang.app.ui.dialog

import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import androidx.annotation.RequiresApi
import androidx.core.os.bundleOf
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.setFragmentResult
import com.find.gang.app.databinding.DialogInputGenericBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText

class InputDialogFragment : DialogFragment() {

    companion object {
        const val TAG = "InputDialog"
        const val RESULT_KEY_EXTRA = "result_key"
        const val INPUT_TEXT_EXTRA = "input_text"

        fun newInstance(config: InputDialogConfig): InputDialogFragment {
            return InputDialogFragment().apply {
                arguments = bundleOf(
                    "config" to config
                )
            }
        }
    }

    private lateinit var config: InputDialogConfig

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        config = requireArguments().getParcelable("config", InputDialogConfig::class.java)!!
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): android.app.Dialog {
        val binding = DialogInputGenericBinding.inflate(LayoutInflater.from(requireContext()))
        val etInput = binding.etInput

        // 应用配置
        etInput.hint = config.hint
        etInput.inputType = config.inputType
        if (config.prefill != null) {
            etInput.setText(config.prefill)
            etInput.setSelection(config.prefill!!.length)
        }

        // 处理剪贴板建议
        if (config.showClipboardSuggestion) {
            setupClipboardSuggestion(binding, etInput)
        }

        val builder = MaterialAlertDialogBuilder(requireContext())
        if (!config.title.isNullOrEmpty()) {
            builder.setTitle(config.title)
        }
        builder.setView(binding.root)
        builder.setPositiveButton(config.positiveText) { _, _ ->
            val input = etInput.text.toString().trim()
            // 校验（如果有外部 validator，可在此处调用，但无法在这里获取 validator 函数）
            setFragmentResult(config.requestKey, bundleOf(INPUT_TEXT_EXTRA to input))
            dismiss()
        }
        builder.setNegativeButton(config.negativeText) { dialog, _ ->
            dialog.cancel()
        }

        val dialog = builder.create()
        etInput.requestFocus()
        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
        return dialog
    }

    private fun setupClipboardSuggestion(
        binding: DialogInputGenericBinding,
        etInput: TextInputEditText
    ) {
        val clipboardManager =
            requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clipText = clipboardManager.primaryClip?.getItemAt(0)?.text?.toString()

        if (!clipText.isNullOrBlank()) {
            // 截断过长的内容，显示前50个字符
            val displayText = if (clipText.length > 50) {
                clipText.substring(0, 50) + "…"
            } else {
                clipText
            }

            binding.tvClipboardText.text = displayText
            binding.layoutClipboardSuggestion.visibility = View.VISIBLE

            // 点击整个建议条，将完整内容填入输入框
            binding.layoutClipboardSuggestion.setOnClickListener {
                etInput.setText(clipText)
                etInput.setSelection(clipText.length) // 光标移到末尾
                // 可选：填充后自动隐藏建议条
                 binding.layoutClipboardSuggestion.visibility = View.GONE
            }
        }
        // 如果没有文本或为空，则保持隐藏（布局默认 gone）
    }
}