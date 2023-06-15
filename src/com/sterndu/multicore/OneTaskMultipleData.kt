@file:JvmName("OneTaskMultipleData")
package com.sterndu.multicore

import com.sterndu.multicore.MultiCore.TaskHandler
import com.sterndu.util.interfaces.ThrowingConsumer
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

	constructor(task: Task<E, O>, prioMult: Double) : super(prioMult) {
		this.task = task
		results = HashMap()
		params_list = LinkedList()
	}

	private val paramsFromList: Pair<T, Array<out E>>?
		get() {
			if (params_list.size > 0) synchronized(params_list) {
				synchronized(lock) { active_tasks++ }
				return params_list.removeAt(0)
			} else return null
		}

	private fun putResult(key: T, res: O) {
		synchronized(lock) { active_tasks-- }
		results[key] = res
	}

	override fun hasTask(): Boolean {
		return params_list.size > 0
	}

	fun getResults(): Map<T, O> {
		while (params_list.size > 0 || active_tasks != 0) try {
			Thread.sleep(2)
		} catch (e: InterruptedException) {
			e.printStackTrace()
		}
		return results
	}

	override fun getTask(): ThrowingConsumer<TaskHandler> {
		return ThrowingConsumer {
			val entry = paramsFromList
			val ta = task
			val res = ta.run(entry!!.second)
			putResult(entry.first, res)
		}
	}

	fun pushParams(key: T, vararg e: E) {
		synchronized(params_list) { params_list.add(key to e) }
	}
}