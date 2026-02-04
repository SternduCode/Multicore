@file:JvmName("OneTaskMultipleData")
package com.sterndu.multicore

import java.util.*

class OneTaskMultipleData<T, E, O> : TaskHandler {
	fun interface Task<E, O> {
		fun run(e: Array<out E>): O
	}

	private val task: Task<E, O>
	private val results: MutableMap<T, O>
	private val paramsList: MutableList<Pair<T, Array<out E>>>
	private val lock = Any()
	private var activeTasks = 0

	constructor(task: Task<E, O>) {
		this.task = task
		results = HashMap()
		paramsList = LinkedList()
	}

	constructor(task: Task<E, O>, priorityMultiplier: Double) : super(priorityMultiplier) {
		this.task = task
		results = HashMap()
		paramsList = LinkedList()
	}

	private val paramsFromList: Pair<T, Array<out E>>?
		get() {
			if (paramsList.isNotEmpty()) synchronized(paramsList) {
				synchronized(lock) { activeTasks++ }
				return paramsList.removeAt(0)
			} else return null
		}

	private fun putResult(key: T, res: O) {
		synchronized(lock) { activeTasks-- }
		results[key] = res
	}

	override fun hasTask(): Boolean {
		return paramsList.isNotEmpty()
	}

	fun getResults(): Map<T, O> {
		while (paramsList.isNotEmpty() || activeTasks != 0) try {
			Thread.sleep(2)
		} catch (e: InterruptedException) {
			e.printStackTrace()
		}
		return results
	}

	override fun getTask(): Runnable? {
		val entry = paramsFromList
		return if (entry != null) Runnable {
			val ta = task
			val res = ta.run(entry.second)
			putResult(entry.first, res)
		} else null
	}

	fun pushParams(key: T, vararg e: E) {
		synchronized(paramsList) { paramsList.add(key to e) }
	}
}