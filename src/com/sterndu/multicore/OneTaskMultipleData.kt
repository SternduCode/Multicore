@file:JvmName("OneTaskMultipleData")
package com.sterndu.multicore

import com.sterndu.util.interfaces.ThrowingRunnable
import java.util.*

class OneTaskMultipleData<T, E, O> : TaskHandler {
	fun interface Task<E, O> {
		fun run(e: Array<out E>): O
	}

	private val task: Task<E, O>
	private val results: MutableMap<T, O>
	private val params_list: MutableList<Pair<T, Array<out E>>>
	private val lock = Any()
	private var active_tasks = 0

	constructor(task: Task<E, O>) {
		this.task = task
		results = HashMap()
		params_list = LinkedList()
	}

	constructor(task: Task<E, O>, priorityMultiplier: Double) : super(priorityMultiplier) {
		this.task = task
		results = HashMap()
		params_list = LinkedList()
	}

	private val paramsFromList: Pair<T, Array<out E>>?
		get() {
			if (params_list.isNotEmpty()) synchronized(params_list) {
				synchronized(lock) { active_tasks++ }
				return params_list.removeAt(0)
			} else return null
		}

	private fun putResult(key: T, res: O) {
		synchronized(lock) { active_tasks-- }
		results[key] = res
	}

	override fun hasTask(): Boolean {
		return params_list.isNotEmpty()
	}

	fun getResults(): Map<T, O> {
		while (params_list.isNotEmpty() || active_tasks != 0) try {
			Thread.sleep(2)
		} catch (e: InterruptedException) {
			e.printStackTrace()
		}
		return results
	}

	override fun getTask(): ThrowingRunnable? {
		val entry = paramsFromList
		return if (entry != null) ThrowingRunnable {
			val ta = task
			val res = ta.run(entry.second)
			putResult(entry.first, res)
		} else null
	}

	fun pushParams(key: T, vararg e: E) {
		synchronized(params_list) { params_list.add(key to e) }
	}
}