@file:JvmName("NullTaskHandler")
package com.sterndu.multicore

import com.sterndu.util.interfaces.ThrowingConsumer

object NullTaskHandler : MultiCore.TaskHandler() {

	override fun getTask(): ThrowingConsumer<MultiCore.TaskHandler> = ThrowingConsumer { }

	override fun hasTask(): Boolean {
		return false
	}

}