package com.sterndu.multicore;

import java.lang.Thread.State;
import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

import com.sterndu.util.interfaces.ThrowingConsumer;

// TODO: Auto-generated Javadoc
/**
 * The Class MultiCore.
 */
public class MultiCore {

	/**
	 * The Class TaskHandler.
	 */
	public static abstract class TaskHandler {

		/** The prio mult. */
		protected double prioMult;

		/** The last average. */
		protected double lastAverage;

		/** The times. */
		protected final List<Long> times;

		/**
		 * Instantiates a new task handler.
		 */
		protected TaskHandler() {
			prioMult=.1d;
			lastAverage=.0d;
			times = new ArrayList<>();
		}

		/**
		 * Instantiates a new task handler.
		 *
		 * @param prioMult the prio mult
		 */
		protected TaskHandler(double prioMult) {
			this.prioMult=prioMult;
			lastAverage=.0d;
			times = new ArrayList<>();
		}

		/**
		 * Adds the time.
		 *
		 * @param time the time
		 */
		protected void addTime(long time) {
			synchronized (times) {
				times.add(time);
				if (times.size() > 30)
					times.remove(0);
			}
		}

		/**
		 * Gets the task.
		 *
		 * @return the task
		 */
		protected abstract ThrowingConsumer<TaskHandler> getTask();

		/**
		 * Checks for task.
		 *
		 * @return true, if successful
		 */
		protected abstract boolean hasTask();

		/**
		 * Gets the average time.
		 *
		 * @return the average time
		 */
		public double getAverageTime() {
			synchronized (times) {
				return lastAverage = times.parallelStream().mapToLong(l -> l).average().getAsDouble();
			}
		}

		/**
		 * Gets the last average time.
		 *
		 * @return the last average time
		 */
		public double getLastAverageTime() {
			return lastAverage;
		}

		/**
		 * Gets the prio mult.
		 *
		 * @return the prio mult
		 */
		public double getPrioMult() {
			return prioMult;
		}

		/**
		 * Gets the times.
		 *
		 * @return the times
		 */
		public List<Long> getTimes(){
			return new ArrayList<>(times);
		}
	}

	/** The multi core. */
	private static MultiCore multiCore;
	static {
		multiCore = new MultiCore();
	}

	/** The ses. */
	private final ScheduledThreadPoolExecutor ses = (ScheduledThreadPoolExecutor) Executors.newScheduledThreadPool(0);

	/** The sim threads lock. */
	private final Object simThreadsLock = new Object();

	/** The simultaneous threads. */
	private int simultaneousThreads = 0;

	/** The ab. */
	private final AtomicBoolean ab;

	/** The count. */
	private int count;

	/** The task handler. */
	private final List<TaskHandler> taskHandler;

	/** The threads. */
	private final Thread[] threads;

	/** The r. */
	private final Runnable r;

	/**
	 * Instantiates a new multi core.
	 */
	private MultiCore() {
		r = () -> {
			while (true) {
				Entry<TaskHandler, ThrowingConsumer<TaskHandler>> data = multiCore.getTask();
				if (data == null)
					break;
				ThrowingConsumer<TaskHandler> data2 = data.getValue();
				if (data2 != null) {
					long st = System.currentTimeMillis();
					try {
						data2.accept(data.getKey());
						long et = System.currentTimeMillis();
						et -= st;
						data.getKey().addTime(et);
						data.getKey().getAverageTime();
					} catch (Exception e) {
						e.printStackTrace();
					}
				} else break;
				try {
					Thread.sleep(1);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}
		};
		ses.setMaximumPoolSize(Runtime.getRuntime().availableProcessors());
		ab = new AtomicBoolean(false);
		taskHandler = new ArrayList<>();
		threads=new Thread[Runtime.getRuntime().availableProcessors()];
		for (int i = 0; i < threads.length; i++) threads[i] = new Thread(r, "MultiCore-Worker=" + i);
		Runtime.getRuntime().addShutdownHook(new Thread(() -> {
			close();
		}));
	}

	/**
	 * Check if more threads are required and start some if needed.
	 *
	 * @return the int
	 */
	private static int checkIfMoreThreadsAreRequiredAndStartSomeIfNeeded() {
		int sum = getAmountOfAvailableTasks();
		if (sum > 0) synchronized (multiCore.simThreadsLock) {
			setSimultaneousThreads(getSimultaneousThreads(), sum);
		}
		else if (getActiveThreadsCount() == 0) setSimultaneousThreads(getSimultaneousThreads(), 1);
		return sum;
	}

	/**
	 * Gets the active threads count.
	 *
	 * @return the active threads count
	 */
	private static int getActiveThreadsCount() {
		return (int) Stream.of(MultiCore.multiCore.threads).filter(Thread::isAlive).count();
	}

	/**
	 * Re sort.
	 */
	private static void reSort() {
		synchronized (multiCore.taskHandler) {
			Collections.sort(multiCore.taskHandler, (d1, d2) -> Double.compare(d2.getLastAverageTime() * d2.prioMult,
					d1.getLastAverageTime() * d1.prioMult));
		}
	}

	/**
	 * Adds the task handler.
	 *
	 * @param taskHandler the task handler
	 */
	public static void addTaskHandler(TaskHandler taskHandler) {
		synchronized (multiCore.taskHandler) {
			multiCore.taskHandler.add(taskHandler);
			reSort();
		}
		checkIfMoreThreadsAreRequiredAndStartSomeIfNeeded();

	}

	/**
	 * Close.
	 */
	public static void close() {
		multiCore.ab.set(true);
	}

	/**
	 * Gets the amount of available tasks.
	 *
	 * @return the amount of available tasks
	 */
	public static int getAmountOfAvailableTasks() {
		synchronized (multiCore.taskHandler) {
			return multiCore.taskHandler.parallelStream().mapToInt(t -> t.hasTask() ? 1 : 0).sum();
		}
	}

	/**
	 * Gets the simultaneous threads.
	 *
	 * @return the simultaneous threads
	 */
	public static int getSimultaneousThreads() {
		return multiCore.simultaneousThreads;
	}

	/**
	 * Removes the task handler.
	 *
	 * @param taskHandler the task handler
	 * @return true, if successful
	 */
	public static boolean removeTaskHandler(TaskHandler taskHandler) {
		synchronized (multiCore.taskHandler) {
			boolean b = multiCore.taskHandler.remove(taskHandler);
			reSort();
			return b;
		}

	}

	/**
	 * Sets the simultaneous threads.
	 *
	 * @param amount the amount
	 * @param data the data
	 */
	public static void setSimultaneousThreads(int amount, int... data) {
		synchronized (multiCore.simThreadsLock) {
			multiCore.simultaneousThreads = Math.min(multiCore.threads.length, amount);
			for (int i = 0; i < multiCore.threads.length; i++)
				if (State.TERMINATED.equals(multiCore.threads[i].getState()))
					multiCore.threads[i] = new Thread(multiCore.r, "MultiCore-Worker=" + i);
			int temp = Math.max(multiCore.simultaneousThreads,
					data != null && data.length > 0 ? data[0] : multiCore.simultaneousThreads);
			if (MultiCore.getActiveThreadsCount() < temp) {
				int activate = temp - MultiCore.getActiveThreadsCount();
				for (Thread th: multiCore.threads) {
					if (!th.isAlive()) {
						th.start();
						activate--;
					}
					if (activate == 0)
						break;
				}
			}
		}
	}

	/**
	 * Gets the task.
	 *
	 * @return the task
	 */
	private synchronized Entry<TaskHandler, ThrowingConsumer<TaskHandler>> getTask() {

		if (checkIfMoreThreadsAreRequiredAndStartSomeIfNeeded() > 0) {
			if (count == taskHandler.size() - 1) count = 0;
			else count++;
			TaskHandler handler = taskHandler.get(count);
			if (handler.hasTask()) return Map.entry(handler, handler.getTask());
		} else if (ab.get() | MultiCore.getActiveThreadsCount() > 1) return null;
		try {
			Thread.sleep(2);
		} catch (InterruptedException e) {
			e.printStackTrace();
			return null;
		}
		return Map.entry(new TaskHandler() {

			@Override
			protected ThrowingConsumer<TaskHandler> getTask() { return null; }

			@Override
			protected boolean hasTask() {
				return false;
			}
		}, th -> {});
	}

}
