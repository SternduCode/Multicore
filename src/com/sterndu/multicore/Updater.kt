@file:JvmName("Updater")
package com.sterndu.multicore

import com.sterndu.multicore.MultiCore.TaskHandler
import com.sterndu.util.interfaces.ThrowingConsumer
import com.sterndu.util.interfaces.ThrowingRunnable
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.collections.ArrayList

class Updater private constructor() : TaskHandler() {
	/**
	 * The  Information.
	 */
	private data class Information(
		val millis: Long = 0L,
		val times: MutableList<Long> = ArrayList(),
		val clazz: Class<*>,
		val tr: ThrowingRunnable
	) {

		override fun equals(other: Any?): Boolean {
			if (other === this) return true
			if (other == null || other.javaClass != this.javaClass) return false
			val that = other as Information
			return millis == that.millis && clazz == that.clazz && tr === that.tr
		}

		override fun hashCode(): Int {
			return Objects.hash(millis, clazz, tr)
		}

		override fun toString(): String {
			return "Information[" +
					"millis=" + millis + ']'
		}

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

	/** The interrupted.  */
	private val interrupted: MutableList<Exception> = ArrayList()

	/** The l.  */
	private val l = ConcurrentHashMap<Any, Information>()

	/**
	 * Instantiates a new updater.
	 */
	init {
		instance = this
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
		return getInstance().l.isNotEmpty()
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
			e.printStackTrace()
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
			e.printStackTrace()
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
			e.printStackTrace()
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
			e.printStackTrace()
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
			(i != null && i.clazz == caller) && l.remove(key) != null
		} catch (e: ClassNotFoundException) {
			e.printStackTrace()
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
			e.printStackTrace()
		}
	}

	companion object {
		/** The instance.  */
		private lateinit var instance: Updater

		init {
			Updater()
		}

		/**
		 * Gets the single instance of Updater.
		 *
		 * @return single instance of Updater
		 */
		@JvmStatic
		fun getInstance(): Updater {
			return instance
		}
	}
}
