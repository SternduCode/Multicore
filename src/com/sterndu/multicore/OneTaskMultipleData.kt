package com.sterndu.multicore;

import java.util.*;
import java.util.Map.Entry;
import com.sterndu.multicore.MultiCore.TaskHandler;
import com.sterndu.util.interfaces.ThrowingConsumer;

public class OneTaskMultipleData<T, E, O> extends TaskHandler {

	@FunctionalInterface
	public interface Task<E, O> {

		O run(E[] e);

	}
	private final Task<E, O> task;
	private final Map<T, O> results;
	private final List<Entry<T, E[]>> params_list;

	private final Object lock = new Object();
	private int active_tasks = 0;

	public OneTaskMultipleData(Task<E, O> task) {
		this.task = task;
		results = new HashMap<>();
		params_list = new LinkedList<>();
	}

	public OneTaskMultipleData(Task<E, O> task,double prioMult) {
		super(prioMult);
		this.task = task;
		results = new HashMap<>();
		params_list = new LinkedList<>();
	}

	private Entry<T,E[]> getParamsFromList(){
		if (params_list.size()>0) synchronized (params_list) {
			synchronized (lock) {
				active_tasks++;
			}
			Entry<T,E[]> e=params_list.get(0);
			params_list.remove(0);
			return e;
		}
		else return null;
	}

	private void putResult(T key, O res) {
		synchronized (lock) {
			active_tasks--;
		}
		results.put(key, res);
	}

	@Override
	protected boolean hasTask() {
		return params_list.size()>0;
	}

	public Map<T, O> getResults() {

		while (params_list.size() > 0 || active_tasks != 0) try {
			Thread.sleep(2);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		return results;
	}

	@Override
	public ThrowingConsumer<TaskHandler> getTask() { return t->{
		Entry<T,E[]> entry=getParamsFromList();
		Task<E,O> ta=task;
		O res=ta.run(entry.getValue());
		putResult(entry.getKey(), res);
	}; }

	@SuppressWarnings("unchecked")
	public void pushParams(T key, E... e) {
		synchronized (this.params_list) {
			this.params_list.add(Map.entry(key, e));
		}
	}

}