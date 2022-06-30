package com.sterndu.multicore;

import java.lang.Thread.State;
import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;
import com.sterndu.util.interfaces.ThrowingConsumer;

public class MultiCore {

	// TODO max threads

	public static abstract class TaskHandler {
		protected double prioMult;
		protected double lastAverage;
		protected final List<Long> times;

		protected TaskHandler() {
			prioMult=.1d;
			lastAverage=.0d;
			times = new ArrayList<>();
		}

		protected TaskHandler(double prioMult) {
			this.prioMult=prioMult;
			lastAverage=.0d;
			times = new ArrayList<>();
		}

		protected void addTime(long time) {
			synchronized (times) {
				times.add(time);
				if (times.size() > 30)
					times.remove(0);
			}
		}

		protected abstract ThrowingConsumer<TaskHandler> getTask();

		protected abstract boolean hasTask();

		public double getAverageTime() {
			synchronized (times) {
				return lastAverage = times.parallelStream().mapToLong(l -> l).average().getAsDouble();
			}
		}

		public double getLastAverageTime() {
			return lastAverage;
		}

		public double getPrioMult() {
			return prioMult;
		}

		public List<Long> getTimes(){
			return new ArrayList<>(times);
		}
	}

	private static MultiCore multiCore;
	static {
		multiCore = new MultiCore();
	}

	private final Object simThreadsLock = new Object();
	private int simultaneousThreads = 0;

	private final AtomicBoolean ab;
	private int count;

	private final List<TaskHandler> taskHandler;

	private final Thread[] threads;
	private final Runnable r;

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
		ab = new AtomicBoolean(false);
		taskHandler = new ArrayList<>();
		threads=new Thread[Runtime.getRuntime().availableProcessors()];
		for (int i = 0; i < threads.length; i++) threads[i] = new Thread(r, "MultiCore-Worker=" + i);
		Runtime.getRuntime().addShutdownHook(new Thread(() -> {
			close();
		}));
	}

	private static int checkIfMoreThreadsAreRequiredAndStartSomeIfNeeded() {
		int sum = getAmountOfAvailableTasks();
		if (sum > 0) synchronized (multiCore.simThreadsLock) {
			setSimultaneousThreads(getSimultaneousThreads(), sum);
		}
		else if (getActiveThreadsCount() == 0) setSimultaneousThreads(getSimultaneousThreads(), 1);
		return sum;
	}

	private static int getActiveThreadsCount() {
		return (int) Stream.of(MultiCore.multiCore.threads).filter(Thread::isAlive).count();
	}

	private static void reSort() {
		synchronized (multiCore.taskHandler) {
			Collections.sort(multiCore.taskHandler, (d1, d2) -> Double.compare(d2.getLastAverageTime() * d2.prioMult,
					d1.getLastAverageTime() * d1.prioMult));
		}
	}

	public static void addTaskHandler(TaskHandler taskHandler) {
		synchronized (multiCore.taskHandler) {
			multiCore.taskHandler.add(taskHandler);
			reSort();
		}
		checkIfMoreThreadsAreRequiredAndStartSomeIfNeeded();

	}

	public static void close() {
		multiCore.ab.set(true);
	}

	public static int getAmountOfAvailableTasks() {
		synchronized (multiCore.taskHandler) {
			return multiCore.taskHandler.parallelStream().mapToInt(t -> t.hasTask() ? 1 : 0).sum();
		}
	}

	public static int getSimultaneousThreads() {
		return multiCore.simultaneousThreads;
	}

	public static boolean removeTaskHandler(TaskHandler taskHandler) {
		synchronized (multiCore.taskHandler) {
			boolean b = multiCore.taskHandler.remove(taskHandler);
			reSort();
			return b;
		}

	}

	public static void setSimultaneousThreads(int amount, int... data) {
		synchronized (multiCore.simThreadsLock) {
			multiCore.simultaneousThreads = Math.min(multiCore.threads.length, amount);
			for (int i = 0; i < multiCore.threads.length; i++)
				if (multiCore.threads[i].getState().equals(State.TERMINATED))
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
