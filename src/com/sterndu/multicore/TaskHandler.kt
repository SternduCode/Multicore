package com.sterndu.multicore

abstract class TaskHandler {

	var priorityMultiplier: Double

	var lastAverageTime: Double
		protected set

	private val _times: MutableList<Long>

	val times: List<Long>
		get() = _times.toList()


	protected constructor() {
		priorityMultiplier = .1
		lastAverageTime = .0
		_times = ArrayList()
	}

	protected constructor(priorityMultiplier: Double) {
		this.priorityMultiplier = priorityMultiplier
		lastAverageTime = .0
		_times = ArrayList()
	}

	fun addTime(time: Long) {
		synchronized(_times) {
			_times.add(time)
			if (_times.size > 20) _times.removeAt(0)
			averageTime
		}
	}

	internal open fun internalGetTask(): Runnable? = getTask()

	protected abstract fun getTask(): Runnable?

	abstract fun hasTask(): Boolean

	val averageTime: Double
		get() {
			synchronized(_times) {
				return _times.average().also { lastAverageTime = it }
			}
		}

}