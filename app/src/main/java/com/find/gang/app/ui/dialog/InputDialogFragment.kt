package com.find.gang.app.ui.dialog

import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.WindowManager
import androidx.annotation.RequiresApi
import androidx.core.os.bundleOf
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.setFragmentResult
import com.find.gang.app.databinding.DialogInputGenericBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder

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
        val etInput = binding.etInput  // 自动生成，对应 id: et_input

        // 应用配置
        etInput.hint = config.hint
        etInput.inputType = config.inputType
        if (config.prefill != null) {
            etInput.setText(config.prefill)
            etInput.setSelection(config.prefill!!.length)
        }

        val builder = MaterialAlertDialogBuilder(requireContext())
        if (!config.title.isNullOrEmpty()) {
            builder.setTitle(config.title)
        }
        builder.setView(binding.root)
        builder.setPositiveButton(config.positiveText) { _, _ ->
            val input = etInput.text.toString().trim()
            // 执行校验
            val error = config.validator?.invoke(input)
            if (error != null) {
                // 显示错误并阻止关闭（这里简化为 Toast，实际可设置 EditText 错误）
                etInput.error = error
                return@setPositiveButton
            }
            // 通过 Fragment Result API 传回结果
            setFragmentResult(config.requestKey, bundleOf(INPUT_TEXT_EXTRA to input))
            dismiss()
        }
        builder.setNegativeButton(config.negativeText) { dialog, _ ->
            dialog.cancel()
        }

        val dialog = builder.create()

        // 自动弹出键盘
        etInput.requestFocus()
        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)

        return dialog
    }
}