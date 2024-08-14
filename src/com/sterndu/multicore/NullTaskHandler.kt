@file:JvmName("NullTaskHandler")
package com.sterndu.multicore

import com.sterndu.util.interfaces.ThrowingConsumer

object NullTaskHandler : TaskHandler() {

	override fun getTask(): ThrowingConsumer<TaskHandler> = ThrowingConsumer { }

	override fun hasTask(): Boolean {
		return false
	}

}