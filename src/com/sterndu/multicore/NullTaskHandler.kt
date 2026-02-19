@file:JvmName("NullTaskHandler")
package com.sterndu.multicore

object NullTaskHandler: TaskHandler() {

	val nullTask = Runnable { }

	override fun internalGetTask(): Runnable = nullTask
	override fun getTask(): Runnable = nullTask

	override fun hasTask(): Boolean {
		return false
	}

}