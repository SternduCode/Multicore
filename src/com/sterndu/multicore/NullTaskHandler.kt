@file:JvmName("NullTaskHandler")
package com.sterndu.multicore

object NullTaskHandler: TaskHandler() {

	val nullTask: () -> Unit = { }

	override fun internalGetTask(): () -> Unit = nullTask
	override fun getTask(): () -> Unit = nullTask

	override val hasTask: Boolean get() {
		return false
	}

}