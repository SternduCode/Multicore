package com.sterndu.multicore;

import java.util.*;
import java.util.Map.Entry;
import java.util.function.*;
import java.util.stream.*;
import com.sterndu.multicore.MultiCore.TaskHandler;
import com.sterndu.util.interfaces.*;

public class Updater extends MultiCore.TaskHandler {

	private static record Information(Long milis, List<Long> times, Class<?> clazz, ThrowingRunnable tr) {
		private Information(Class<?> clazz, ThrowingRunnable tr) {
			this(0l, new ArrayList<>(), clazz, tr);
		}

		private Information(Long milis, Class<?> clazz, ThrowingRunnable tr) {
			this(milis, new ArrayList<>(), clazz, tr);
		}

		public Double avgFreq() {
			return times.size() < 2 ? Double.POSITIVE_INFINITY
					: times.stream().collect(new Collector<Long, List<Long>, LongStream>() {

						private Long last = null;

						@Override
						public BiConsumer<List<Long>, Long> accumulator() {
							return (li, l) -> {
								if (last == null)
									last = l;
								else {
									li.add(l - last);
									last = l;
								}
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

	private static Updater instance;

	static {
		getInstance();
	}

	private final List<Exception> interupted = new ArrayList<>();
	private final Map<Object, Information> l = new HashMap<>(), l2 = new HashMap<>();

	private Updater() {
		if (instance == null)
			instance = this;
		MultiCore.addTaskHandler(this);
	}
	public static Updater getInstance() {
		return instance != null ? instance : new Updater();
	}

	private void add(Object key,Information i) {
		synchronized (l2) {
			l2.put(key, i);
		}
	}

	@Override
	protected ThrowingConsumer<TaskHandler> getTask() {
		return t -> {
			synchronized (this) {
				for (final Entry<Object, Information> en: l.entrySet()) try {
					Information i = en.getValue();
					List<Long> times = i.times();
					long lastrun = times.size() == 0 ? 0 : times.get(times.size() - 1), curr = System.currentTimeMillis();
					if (curr - lastrun >= i.milis()) {
						en.getValue().tr().run();
						if (times.size() >= 20) times.remove(0);
						times.add(curr);
					}
				} catch (final Exception e) {
					interupted.add(e);
				}
				synchronized (l2) {
					if (l2.hashCode() != l.hashCode()) {
						l.clear();
						l.putAll(l2);
					}
				}
			}
		};
	}

	@Override
	protected boolean hasTask() {
		return Updater.getInstance().l2.size() > 0;
	}

	public <R> void add(R r, Object key) {
		try {
			final Class<?> caller = Class.forName(Thread.currentThread().getStackTrace()[2].getClassName());
			if (r instanceof ThrowingRunnable tr) add(key, new Information(caller, tr));
			else if (r instanceof Runnable rr) add(key, new Information(caller,() -> rr.run()));
		} catch (ClassNotFoundException e) {
			e.printStackTrace();
		}
	}

	public <R> void add(R r, Object key, long milis) {
		try {
			final Class<?> caller = Class.forName(Thread.currentThread().getStackTrace()[2].getClassName());
			if (r instanceof ThrowingRunnable tr) add(key, new Information(milis, caller, tr));
			else if (r instanceof Runnable rr) add(key, new Information(milis, caller, () -> rr.run()));
		} catch (ClassNotFoundException e) {
			e.printStackTrace();
		}
	}

	public boolean changeTargetFreq(Object key,Long milis) {
		try {
			final Class<?> caller = Class.forName(Thread.currentThread().getStackTrace()[2].getClassName());
			synchronized (l2) {
				Information i = l2.get(key);
				if (i == null) return false;
				if (!i.clazz.equals(caller)) return false;
				return l2.replace(key,new Information(milis, l2.get(key).times(), caller, l2.get(key).tr())) != null;
			}
		} catch (ClassNotFoundException e) {
			e.printStackTrace();
			return false;
		}
	}

	public Double getAvgExecFreq(Object key) {
		try {
			final Class<?> caller = Class.forName(Thread.currentThread().getStackTrace()[2].getClassName());
			synchronized (l2) {
				Information i = l2.get(key);
				if (i == null) return 0.0d;
				if (!i.clazz.equals(caller)) return 0.0d;
				return l2.get(key).avgFreq();
			}
		} catch (ClassNotFoundException e) {
			e.printStackTrace();
			return 0.0d;
		}
	}

	public List<Exception> getExceptions() {return interupted;}

	public boolean remove(Object key) {
		try {
			final Class<?> caller = Class.forName(Thread.currentThread().getStackTrace()[2].getClassName());
			synchronized (l2) {
				Information i = l2.get(key);
				if (i == null) return false;
				if (!i.clazz.equals(caller)) return false;
				return l2.remove(key) != null;
			}
		} catch (ClassNotFoundException e) {
			e.printStackTrace();
			return false;
		}
	}

	public void removeAll() {
		try {
			final Class<?> caller = Class.forName(Thread.currentThread().getStackTrace()[2].getClassName());
			synchronized (l2) {
				l2.entrySet().removeIf(e -> e.getValue().clazz().equals(caller));
			}
		} catch (ClassNotFoundException e) {
			e.printStackTrace();
		}
	}
}
