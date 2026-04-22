package com.sterndu.multicore

abstract class TaskHandler(
	millis: Long = 0,
	isFixedDelay: Boolean = false,
	nextRun: Long = 0,
	canRunSimultaneously: Boolean = false,
): Task(millis, isFixedDelay, nextRun, canRunSimultaneously) {

	internal open fun internalGetTask(): (() -> Unit)? = getTask()

	protected abstract fun getTask(): (() -> Unit)?

	final override val task: () -> Unit get() = getTask() ?: NullTaskHandler.internalGetTask()

	abstract fun hasTask(): Boolean

}