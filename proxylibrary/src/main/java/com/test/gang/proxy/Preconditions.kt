package com.test.gang.proxy

object Preconditions {
    fun <T> checkNotNull(reference: T?): T {
        if (reference == null) {
            throw NullPointerException()
        }
        return reference
    }

    fun checkAllNotNull(vararg references: Any?) {
        for (reference in references) {
            if (reference == null) {
                throw NullPointerException()
            }
        }
    }

    fun <T> checkNotNull(reference: T?, errorMessage: String?): T {
        if (reference == null) {
            throw NullPointerException(errorMessage)
        }
        return reference
    }

    fun checkArgument(expression: Boolean) {
        require(expression)
    }

    fun checkArgument(expression: Boolean, errorMessage: String) {
        require(expression) { errorMessage }
    }
}