package com.sterndu.multicore;

import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.*;
import java.util.stream.*;

import com.sterndu.multicore.MultiCore.TaskHandler;
import com.sterndu.util.interfaces.*;

// TODO: Auto-generated Javadoc
/**
 * The Class Updater.
 */
public class Updater extends MultiCore.TaskHandler {

	/**
	 * The  Information.
	 */
	private static final class Information {
		private final Long millis;
		private final List<Long> times;
		private final Class<?> clazz;
		private final ThrowingRunnable tr;

		public Information(Long millis, List<Long> times, Class<?> clazz, ThrowingRunnable tr) {
			this.millis = millis;
			this.times = times;
			this.clazz = clazz;
			this.tr = tr;
		}

		/**
		 * Instantiates new information.
		 *
		 * @param clazz the clazz
		 * @param tr the tr
		 */
		private Information(Class<?> clazz, ThrowingRunnable tr) {
			this(0L, new ArrayList<>(), clazz, tr);
		}

		/**
		 * Instantiates new information.
		 *
		 * @param millis the millis
		 * @param clazz the clazz
		 * @param tr the tr
		 */
		private Information(Long millis, Class<?> clazz, ThrowingRunnable tr) {
			this(millis, new ArrayList<>(), clazz, tr);
		}

		public Long millis() {
			return millis;
		}

		public List<Long> times() {
			return times;
		}

		public Class<?> clazz() {
			return clazz;
		}

		public ThrowingRunnable tr() {
			return tr;
		}

		@Override
		public boolean equals(Object obj) {
			if (obj == this) return true;
			if (obj == null || obj.getClass() != this.getClass()) return false;
			Information that = (Information) obj;
			return Objects.equals(this.millis, that.millis);
		}

		@Override
		public int hashCode() {
			return Objects.hash(millis);
		}

		@Override
		public String toString() {
			return "Information[" +
					"millis=" + millis + ']';
		}

		/**
		 * Avg freq.
		 *
		 * @return the double
		 */
		public Double avgFreq() {
			return times.size() < 2 ? Double.POSITIVE_INFINITY
					: times.stream().collect(new Collector<Long, List<Long>, LongStream>() {

				private Long last = null;

				@Override
				public BiConsumer<List<Long>, Long> accumulator() {
					return (li, l) -> {
						if ( last != null) li.add(l - last);
						last = l;
					};
				}

				@Override
				public Set<Characteristics> characteristics() {
					return new HashSet<>();
				}

				@Override
				public BinaryOperator<List<Long>> combiner() {
					return (li1, li2) -> {
						li1.addAll(li2);
						return li1;
					};
				}

				@Override
				public Function<List<Long>, LongStream> finisher() {
					return li -> li.parallelStream().mapToLong(l -> l);
				}

				@Override
				public Supplier<List<Long>> supplier() {
					return ArrayList::new;
				}
			}).average().getAsDouble();
		}

	}

	/** The instance. */
	private static Updater instance;

	static {
		getInstance();
	}

	/** The interupted. */
	private final List<Exception> interupted = new ArrayList<>();

	/** The l. */
	private final ConcurrentHashMap<Object, Information> l = new ConcurrentHashMap<>();

	/**
	 * Instantiates a new updater.
	 */
	private Updater() {
		if (instance == null)
			instance = this;
		MultiCore.addTaskHandler(this);
	}

	/**
	 * Gets the single instance of Updater.
	 *
	 * @return single instance of Updater
	 */
	public static Updater getInstance() {
		return instance != null ? instance : new Updater();
	}

	/**
	 * Adds the.
	 *
	 * @param key the key
	 * @param i the i
	 */
	private void add(Object key,Information i) {
		l.put(key, i);
	}

	/**
	 * Gets the task.
	 *
	 * @return the task
	 */
	@Override
	protected ThrowingConsumer<TaskHandler> getTask() {
		return t -> {
			synchronized (this) {
				for (final Entry<Object, Information> en: l.entrySet()) try {
					Information i = en.getValue();
					List<Long> times = i.times();
					long lastrun = times.size() == 0 ? 0 : times.get(times.size() - 1), curr = System.currentTimeMillis();
					if (curr - lastrun >= i.millis()) {
						en.getValue().tr().run();
						if (times.size() >= 20) times.remove(0);
						times.add(curr);
					}
				} catch (final Exception e) {
					interupted.add(e);
				}
			}
		};
	}

	/**
	 * Checks for task.
	 *
	 * @return true, if successful
	 */
	@Override
	protected boolean hasTask() {
		return Updater.getInstance().l.size() > 0;
	}

	/**
	 * Adds the.
	 *
	 * @param <R> the generic type
	 * @param r the r
	 * @param key the key
	 */
	public <R> void add(R r, Object key) {
		try {
			final Class<?> caller = Class.forName(Thread.currentThread().getStackTrace()[2].getClassName());
			if (r instanceof ThrowingRunnable) {
				ThrowingRunnable tr = (ThrowingRunnable) r;
				add(key, new Information(caller, tr));
			} else if (r instanceof Runnable) {
				Runnable rr = (Runnable) r;
				add(key, new Information(caller, rr::run));
			}
		} catch (ClassNotFoundException e) {
			e.printStackTrace();
		}
	}

	/**
	 * Adds the.
	 *
	 * @param <R> the generic type
	 * @param r the r
	 * @param key the key
	 * @param millis the millis
	 */
	public <R> void add(R r, Object key, long millis) {
		try {
			final Class<?> caller = Class.forName(Thread.currentThread().getStackTrace()[2].getClassName());
			if (r instanceof ThrowingRunnable) {
				ThrowingRunnable tr = (ThrowingRunnable) r;
				add(key, new Information(millis, caller, tr));
			} else if (r instanceof Runnable) {
				Runnable rr = (Runnable) r;
				add(key, new Information(millis, caller, rr::run));
			}
		} catch (ClassNotFoundException e) {
			e.printStackTrace();
		}
	}

	/**
	 * Change target freq.
	 *
	 * @param key the key
	 * @param millis the millis
	 * @return true, if successful
	 */
	public boolean changeTargetFreq(Object key,Long millis) {
		try {
			final Class<?> caller = Class.forName(Thread.currentThread().getStackTrace()[2].getClassName());
			Information		i		= l.get(key);
			if (i == null || !i.clazz.equals(caller)) return false;
			return l.replace(key, new Information(millis, l.get(key).times(), caller, l.get(key).tr())) != null;
		} catch (ClassNotFoundException e) {
			e.printStackTrace();
			return false;
		}
	}

	/**
	 * Gets the avg exec freq.
	 *
	 * @param key the key
	 * @return the avg exec freq
	 */
	public Double getAvgExecFreq(Object key) {
		try {
			final Class<?> caller = Class.forName(Thread.currentThread().getStackTrace()[2].getClassName());
			Information		i		= l.get(key);
			if (i == null || !i.clazz.equals(caller)) return 0.0d;
			return l.get(key).avgFreq();
		} catch (ClassNotFoundException e) {
			e.printStackTrace();
			return 0.0d;
		}
	}

	/**
	 * Gets the exceptions.
	 *
	 * @return the exceptions
	 */
	public List<Exception> getExceptions() {return interupted;}

	/**
	 * Removes the.
	 *
	 * @param key the key
	 * @return true, if successful
	 */
	public boolean remove(Object key) {
		try {
			final Class<?> caller = Class.forName(Thread.currentThread().getStackTrace()[2].getClassName());
			Information		i		= l.get(key);
			if (i == null || !i.clazz.equals(caller)) return false;
			return l.remove(key) != null;
		} catch (ClassNotFoundException e) {
			e.printStackTrace();
			return false;
		}
	}

	/**
	 * Removes the all.
	 */
	public void removeAll() {
		try {
			final Class<?> caller = Class.forName(Thread.currentThread().getStackTrace()[2].getClassName());
			l.entrySet().removeIf(e -> e.getValue().clazz().equals(caller));
		} catch (ClassNotFoundException e) {
			e.printStackTrace();
		}
	}
}
