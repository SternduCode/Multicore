@file:JvmName("Updater")
package com.sterndu.multicore

import java.util.concurrent.ConcurrentHashMap
import java.util.logging.Level
import java.util.logging.Logger

object Updater : TaskHandler() {

	internal data class Information(
		val millis: Long = 1L,
		val times: MutableList<Long> = mutableListOf(0),
		val clazz: Class<*>,
		val runnable: Runnable
	) {
		fun avgFreq(): Double {
			return if (times.size < 2) Double.POSITIVE_INFINITY else
				times.mapIndexed { index, value -> if (index > 0) value - times[index - 1] else 0 }.drop(1).average()
		}
	}

	@Suppress("NOTHING_TO_INLINE")
	inline fun getCallingClass(): Class<*> {
		return StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE).callerClass
	}

	internal val logger: Logger

	private val interrupted: MutableList<Exception> = ArrayList()

	internal val taskInformationMap: MutableMap<Any, Information> = ConcurrentHashMap<Any, Information>()

	internal val r: (Information) -> Unit = { information ->
		try {
			// DEBUG logger.info("Running task ${taskInformationMap.entries.first { it.value === information }.key}")
			val times = information.times
			val lastRun = if (times.isEmpty()) 0 else times.last()
			val curr = System.currentTimeMillis()
			if (curr - lastRun >= information.millis) {
				information.runnable.run()
				if (times.size >= 20) times.removeAt(0)
				times.add(curr)
			}
		} catch (e: Exception) {
			synchronized(interrupted) {
				interrupted.add(e)
			}
		}
	}

	init {
		priorityMultiplier = 0.01
		logger = LoggingUtil.getLogger("Updater")
		MultiCore.addTaskHandler(this)
	}

	private fun add(key: Any, i: Information) {
		taskInformationMap[key] = i
	}

	override fun getTask(): Runnable {
		return Runnable {  }
	}

	override fun hasTask(): Boolean {
		return taskInformationMap.isNotEmpty()
	}

	/**
	 * Adds a task to be run periodically
	 *
	 * @param R the type of the task, must be Runnable
	 * @param r the task to run
	 * @param key the key of the task
	 */
	fun add(r: Runnable, key: Any) {
		try {
			val caller = getCallingClass()
			add(key, Information(clazz = caller, runnable = r))
		} catch (e: ClassNotFoundException) {
			logger.log(Level.WARNING, "Updater", e)
		}
	}

	/**
	 * Adds a task to be run periodically after 'millis' milliseconds
	 *
	 * @param R the type of the task, must be either ThrowingRunnable or Runnable
	 * @param r the task to run
	 * @param key the key of the task
	 * @param millis the millis
	 */
	fun add(r: Runnable, key: Any, millis: Long) {
		try {
			val caller = getCallingClass()
			add(key, Information(millis, clazz = caller, runnable = r))
		} catch (e: ClassNotFoundException) {
			logger.log(Level.WARNING, "Updater", e)
		}
	}

	/**
	 * Change target freq.
	 *
	 * @param key the key
	 * @param millis the millis
	 * @return true, if successful
	 */
	fun changeTargetFreq(key: Any, millis: Long): Boolean {
		return try {
			val caller = getCallingClass()
			val i = taskInformationMap[key]
			(i != null && i.clazz == caller) && taskInformationMap.replace(key, Information(millis, i.times, caller, i.runnable) ) != null
		} catch (e: ClassNotFoundException) {
			logger.log(Level.WARNING, "Updater", e)
			false
		}
	}

	/**
	 * Gets the avg exec freq.
	 *
	 * @param key the key
	 * @return the avg exec freq
	 */
	fun getAvgExecFreq(key: Any): Double {
		return try {
			val caller = getCallingClass()
			val i = taskInformationMap[key]
			if (i == null || i.clazz != caller) 0.0 else i.avgFreq()
		} catch (e: ClassNotFoundException) {
			logger.log(Level.WARNING, "Updater", e)
			0.0
		}
	}

	val exceptions: List<Exception>
		get() = interrupted

	/**
	 * Removes the task corresponding to the given key
	 *
	 * @param key the key of the task to remove
	 * @return true, if successful
	 */
	fun remove(key: Any): Boolean {
		return try {
			val caller = getCallingClass()
			val i = taskInformationMap[key]
			logger.fine("remove $key $caller $i") // TODO sometimes causes ConcurrentModificationException fix
			(i != null && i.clazz == caller) && taskInformationMap.remove(key) != null
		} catch (e: ClassNotFoundException) {
			logger.log(Level.WARNING, "Updater", e)
			false
		}
	}

	/**
	 * Removes the all.
	 */
	fun removeAll() {
		try {
			val caller = getCallingClass()
			taskInformationMap.entries.removeIf { (_, value): Map.Entry<Any, Information> -> value.clazz == caller }
		} catch (e: ClassNotFoundException) {
			logger.log(Level.WARNING, "Updater", e)
		}
	}

	fun printAll(logger: Logger) {
		this.taskInformationMap.forEach { (key, value) -> logger.fine("Print All: $key $value")}
	}

}
