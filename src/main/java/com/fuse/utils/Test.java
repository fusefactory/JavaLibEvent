package com.fuse.utils;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

public class Test <T> {
	private Map<Object, List<Predicate<T>>> listeners;
	private List<Predicate<T>> onceListeners;
	private int activeTestCount;
	private List<Mod> modQueue;
	private List<Test<T>> forwardEvents;
	private Predicate<T> forwarder;

	private class Mod {
		public Predicate<T> addListener;
		public Object addOwner;
		public Object removeOwner;
		public Predicate<T> removeListener;

		public Mod(Object owner){
			removeOwner = owner;
		}

		public Mod(Predicate<T> listener){
			removeListener = listener;
		}

		public Mod(Predicate<T> listener, Object owner){
			addListener = listener;
			addOwner = owner;
		}
	};

	public Test() {
		// initialize empty list of listeners
		listeners = new IdentityHashMap<Object, List<Predicate<T>>>();
		modQueue = new ArrayList<Mod>();
		onceListeners = new ArrayList<Predicate<T>>();
		activeTestCount = 0;
		forwardEvents = new ArrayList<Test<T>>();
		forwarder = (T value) -> {
			return this.test(value);
		};
	}

	public void addListener(Predicate<T> newListener){
		addListener(newListener, null);
	}

	public void addListener(Predicate<T> newListener, Object owner){
		// queue operation if locked
		if(isTesting()){
			Mod m = new Mod(newListener, owner);
			modQueue.add(m);
			return;
		}

		// create owner collection if necessary
		if(listeners.get(owner) == null){
			listeners.put(owner, new ArrayList<Predicate<T>>());
		}

		// add to owner collection
		listeners.get(owner).add(newListener);
	}

	public void addOnceListener(Predicate<T> newListener){
		addOnceListener(newListener, null);
	}

	public void addOnceListener(Predicate<T> newListener, Object owner){
		addListener(newListener, owner);
		onceListeners.add(newListener);
	}

	public void removeListener(Predicate<T> listener){
		// queue operation if locked
		if(isTesting()){
			Mod m = new Mod(listener);
			modQueue.add(m);
			return;
		}

		// find listener
		for (Map.Entry<Object, List<Predicate<T>>> pair : listeners.entrySet()){
			Iterator<Predicate<T>> it = pair.getValue().iterator();
            while (it.hasNext()) {
            	if(it.next() == listener)
                	// remove it
            		it.remove();
            }
		}
	}

	public void removeListeners(Object owner){
		if(!isTesting()){
			listeners.remove(owner);
			return;
		}

		// queue operation if locked
		Mod m = new Mod(owner);
		modQueue.add(m);
	}

	public boolean test(T arg){
		// count the number of (recursive) tests
		activeTestCount++;

		boolean allGood = true;

		// call each listener
		for (Map.Entry<Object, List<Predicate<T>>> pair : listeners.entrySet()){
			for(Predicate<T> predicat : pair.getValue()){
				//System.out.println(e.getKey() + ": " + e.getValue());
				if(!predicat.test(arg)){
					allGood = false;
					break;
				}
			}

			if(!allGood)
				break;
		}

		// remove all the listeners that should only be called once
		for(Predicate<T> listener : onceListeners){
			removeListener(listener); // also removes from onceListeners
		}

		// this test is done, "uncount" it
		activeTestCount--;

		// currently still in the process of testing the event?
		if(isTesting())
			return allGood;

		for(Mod m : modQueue){
			if(m.removeListener != null)
				removeListener(m.removeListener);
			if(m.removeOwner != null)
				removeListeners(m.removeOwner);
			if(m.addListener != null)
				addListener(m.addListener, m.addOwner);
		}

		// clear queue
		modQueue.clear();
		return allGood;
	}

	public boolean isTesting(){
		return activeTestCount > 0;
	}

	public int size(){
		int counter=0;
		for (Map.Entry<Object, List<Predicate<T>>> pair : listeners.entrySet())
			counter += pair.getValue().size();
		return counter;
	}

	public void forward(Test<T> other){
		forwardEvents.add(other);
		other.addListener(this.forwarder, this);
	}

	public void stopForwards(){
		for(Test<T> other : forwardEvents){
			stopForward(other);
		}
	}

	public void stopForward(Test<T> other){
		other.removeListeners(this);
		this.forwardEvents.remove(other);
	}
}
