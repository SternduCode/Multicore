@file:JvmName("NullTaskHandler")
package com.sterndu.multicore

import com.sterndu.util.interfaces.ThrowingRunnable

object NullTaskHandler: TaskHandler() {

	val nullTask = ThrowingRunnable { }

	override fun getTask(): ThrowingRunnable = nullTask

	override fun hasTask(): Boolean {
		return false
	}

}