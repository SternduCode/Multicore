@file:JvmName("MultiCore")
package com.sterndu.multicore

import com.sterndu.util.interfaces.ThrowingConsumer
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.atomic.AtomicBoolean
import java.util.stream.Stream


object MultiCore {
	/**
	 * The Class TaskHandler.
	 */
	abstract class TaskHandler {

		var prioMult: Double

		var lastAverageTime: Double
			protected set

		private val _times: MutableList<Long>

		val times: List<Long>
			get() = _times.toList()


		protected constructor() {
			prioMult = .1
			lastAverageTime = .0
			_times = ArrayList()
		}

		protected constructor(prioMult: Double) {
			this.prioMult = prioMult
			lastAverageTime = .0
			_times = ArrayList()
		}

		fun addTime(time: Long) {
			synchronized(_times) {
				_times.add(time)
				if (_times.size > 30) _times.removeAt(0)
			}
		}

		abstract fun getTask(): ThrowingConsumer<TaskHandler>?

		abstract fun hasTask(): Boolean
		val averageTime: Double
			get() {
				synchronized(_times) {
					return _times.average().also { lastAverageTime = it }
				}
			}

	}

	/** The ses.  */
	private val ses = Executors.newScheduledThreadPool(0) as ScheduledThreadPoolExecutor

	/** The sim threads lock.  */
	private val simThreadsLock = Any()

	/** The simultaneous threads.  */
	private var simultaneousThreads = 0

	/** The ab.  */
	private val ab: AtomicBoolean

	/** The count.  */
	private var count = 0

	/** The task handler.  */
	private val taskHandler: MutableList<TaskHandler>

	/** The threads.  */
	private val threads: Array<Thread?>

	/** The r.  */
	private val r: Runnable

	init {
		r = Runnable {
			while (true) {
				val (key, data2) = this.task ?: break
				if (key.hasTask()) {
					val st = System.currentTimeMillis()
					try {
						data2.accept(key)
						var et = System.currentTimeMillis()
						et -= st
						key.addTime(et)
						key.averageTime
					} catch (e: Exception) {
						e.printStackTrace()
					}
				} else break
				try {
					Thread.sleep(1)
				} catch (e: InterruptedException) {
					e.printStackTrace()
				}
			}
		}
		ses.maximumPoolSize = Runtime.getRuntime().availableProcessors()
		ab = AtomicBoolean(false)
		taskHandler = ArrayList()
		threads = arrayOfNulls(Runtime.getRuntime().availableProcessors())
		for (i in threads.indices) threads[i] = Thread(r, "MultiCore-Worker=$i")
		Runtime.getRuntime().addShutdownHook(Thread { close() })
	}

	@get:Synchronized
	private val task: Map.Entry<TaskHandler, ThrowingConsumer<TaskHandler>>?
		get() {
			return if (checkIfMoreThreadsAreRequiredAndStartSomeIfNeeded() > 0) {
				if (count == taskHandler.size - 1) count = 0 else count++
				val handler = taskHandler[count]
				if (handler.hasTask()) java.util.Map.entry(handler, handler.getTask()!!)
				else java.util.Map.entry<TaskHandler, ThrowingConsumer<TaskHandler>>(NullTaskHandler, NullTaskHandler.getTask())
			} else if (ab.get() or (activeThreadsCount > 1)) {
				null
			} else {
				try {
					Thread.sleep(2)
				} catch (e: InterruptedException) {
					e.printStackTrace()
					return null
				}
				java.util.Map.entry<TaskHandler, ThrowingConsumer<TaskHandler>>(NullTaskHandler, NullTaskHandler.getTask())
			}
		}


	private fun checkIfMoreThreadsAreRequiredAndStartSomeIfNeeded(): Int {
		val sum = amountOfAvailableTasks
		if (sum > 0) synchronized(this.simThreadsLock) {
			setSimultaneousThreads(getSimultaneousThreads(), sum)
		} else if (activeThreadsCount == 0) setSimultaneousThreads(getSimultaneousThreads(), 1)
		return sum
	}

	private val activeThreadsCount: Int
		get() = Stream.of(*this.threads).filter { obj: Thread? -> obj != null && obj.isAlive }
			.count().toInt()

	private fun reSort() {
		synchronized(this.taskHandler) {
			this.taskHandler.sortWith { d1: TaskHandler, d2: TaskHandler ->
				(d2.lastAverageTime * d2.prioMult).compareTo(d1.lastAverageTime * d1.prioMult)
			}
		}
	}

	fun addTaskHandler(taskHandler: TaskHandler) {
		synchronized(this.taskHandler) {
			this.taskHandler.add(taskHandler)
			reSort()
		}
		checkIfMoreThreadsAreRequiredAndStartSomeIfNeeded()
	}

	fun close() {
		this.ab.set(true)
	}

	val amountOfAvailableTasks: Int
		get() {
			synchronized(this.taskHandler) {
				return this.taskHandler.parallelStream()
					.mapToInt { t: TaskHandler -> if (t.hasTask()) 1 else 0 }
					.sum()
			}
		}

	fun getSimultaneousThreads(): Int {
		return this.simultaneousThreads
	}

	fun removeTaskHandler(taskHandler: TaskHandler): Boolean {
		synchronized(this.taskHandler) {
			val b = this.taskHandler.remove(taskHandler)
			reSort()
			return b
		}
	}

	fun setSimultaneousThreads(amount: Int, vararg data: Int) {
		synchronized(this.simThreadsLock) {
			this.simultaneousThreads = this.threads.size.coerceAtMost(amount)
			for (i in this.threads.indices) if (
				Thread.State.TERMINATED == this.threads[i]!!.state
				) this.threads[i] = Thread(this.r, "MultiCore-Worker=$i")
			val temp = this.simultaneousThreads.coerceAtLeast(if (data.isNotEmpty()) data[0] else this.simultaneousThreads)
			if (activeThreadsCount < temp) {
				var activate = temp - activeThreadsCount
				for (th in this.threads) {
					if (!th!!.isAlive) {
						th.start()
						activate--
					}
					if (activate == 0) break
				}
			}
		}
	}
}
