@file:JvmName("NullTaskHandler")
package com.sterndu.multicore

import com.sterndu.util.interfaces.ThrowingConsumer

object NullTaskHandler : TaskHandler() {

	val nullTask = ThrowingConsumer<TaskHandler> { }

	override fun getTask(): ThrowingConsumer<TaskHandler> = nullTask

	override fun hasTask(): Boolean {
		return false
	}

}