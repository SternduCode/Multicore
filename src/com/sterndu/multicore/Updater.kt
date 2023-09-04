@file:JvmName("Updater")
package com.sterndu.multicore

import com.sterndu.multicore.MultiCore.TaskHandler
import com.sterndu.util.interfaces.ThrowingConsumer
import com.sterndu.util.interfaces.ThrowingRunnable
import java.util.concurrent.ConcurrentHashMap
import java.util.logging.Level
import java.util.logging.Logger

object Updater : TaskHandler() {
	/**
	 * The  Information.
	 */
	private data class Information(
		val millis: Long = 0L,
		val times: MutableList<Long> = ArrayList(),
		val clazz: Class<*>,
		val tr: ThrowingRunnable
	) {

		/**
		 * Avg freq.
		 *
		 * @return the double
		 */
		fun avgFreq(): Double {
			return if (times.size < 2) Double.POSITIVE_INFINITY else
				times.mapIndexed { index, value -> if (index > 0) value - times[index - 1] else 0 }.drop(1).average()
		}
	}

	private val logger: Logger = LoggingUtil.getLogger("Updater")

	/** The interrupted.  */
	private val interrupted: MutableList<Exception> = ArrayList()

	/** The l.  */
	private val l = ConcurrentHashMap<Any, Information>()

	/**
	 * Instantiates a new updater.
	 */
	init {
		MultiCore.addTaskHandler(this)
	}

	/**
	 * Adds the.
	 *
	 * @param key the key
	 * @param i the i
	 */
	private fun add(key: Any, i: Information) {
		l[key] = i
	}

	/**
	 * Gets the task.
	 *
	 * @return the task
	 */
	override fun getTask(): ThrowingConsumer<TaskHandler> {
		return ThrowingConsumer {
			synchronized(this) {
				for ((_, i) in l) try {
					val times = i.times
					val lastRun = if (times.isEmpty()) 0 else times.last()
					val curr = System.currentTimeMillis()
					if (curr - lastRun >= i.millis) {
						i.tr.run()
						if (times.size >= 20) times.removeAt(0)
						times.add(curr)
					}
				} catch (e: Exception) {
					interrupted.add(e)
				}
			}
		}
	}

	/**
	 * Checks for task.
	 *
	 * @return true, if successful
	 */
	override fun hasTask(): Boolean {
		return l.isNotEmpty()
	}

	/**
	 * Adds the.
	 *
	 * @param <R> the generic type
	 * @param r the r
	 * @param key the key
	</R> */
	fun <R> add(r: R, key: Any) {
		try {
			val caller = Class.forName(Thread.currentThread().stackTrace[2].className)
			when (r) {
				is ThrowingRunnable -> add(key, Information(clazz = caller, tr = r))
				is Runnable -> add(key, Information(clazz = caller, tr = r::run))
				else -> throw NotImplementedError()
			}
		} catch (e: ClassNotFoundException) {
			logger.log(Level.WARNING, "Updater", e)
		}
	}

	/**
	 * Adds the.
	 *
	 * @param <R> the generic type
	 * @param r the r
	 * @param key the key
	 * @param millis the millis
	</R> */
	fun <R> add(r: R, key: Any, millis: Long) {
		try {
			val caller = Class.forName(Thread.currentThread().stackTrace[2].className)
			when (r) {
				is ThrowingRunnable -> add(key, Information(millis, clazz = caller, tr = r))
				is Runnable -> add(key, Information(millis, clazz = caller, tr = r::run))
				else -> throw NotImplementedError()
			}
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
			val caller = Class.forName(Thread.currentThread().stackTrace[2].className)
			val i = l[key]
			(i != null && i.clazz == caller) && l.replace(key, Information(millis, i.times, caller, i.tr) ) != null
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
			val caller = Class.forName(Thread.currentThread().stackTrace[2].className)
			val i = l[key]
			if (i == null || i.clazz != caller) 0.0 else i.avgFreq()
		} catch (e: ClassNotFoundException) {
			logger.log(Level.WARNING, "Updater", e)
			0.0
		}
	}

	val exceptions: List<Exception>
		get() = interrupted

	/**
	 * Removes the.
	 *
	 * @param key the key
	 * @return true, if successful
	 */
	fun remove(key: Any): Boolean {
		return try {
			val caller = Class.forName(Thread.currentThread().stackTrace[2].className)
			val i = l[key]
			logger.fine("$key $caller $i")
			(i != null && i.clazz == caller) && l.remove(key) != null
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
			val caller = Class.forName(Thread.currentThread().stackTrace[2].className)
			l.entries.removeIf { (_, value): Map.Entry<Any, Information> -> value.clazz == caller }
		} catch (e: ClassNotFoundException) {
			logger.log(Level.WARNING, "Updater", e)
		}
	}

}
