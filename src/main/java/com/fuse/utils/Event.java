package com.fuse.utils;

import java.util.Map;
import java.util.IdentityHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Iterator;
import java.util.function.Consumer;

public class Event <T> {
	private Map<Object, List<Consumer<T>>> listeners;
	private List<Consumer<T>> onceListeners;
	private int triggerCount;
	private List<Mod> modQueue;
	private List<Event<T>> forwardEvents;
	private Consumer<T> forwarder;

	private class Mod {
		public Consumer<T> addListener;
		public Object addOwner;
		public Object removeOwner;
		public Consumer<T> removeListener;

		public Mod(Object owner){
			removeOwner = owner;
		}

		public Mod(Consumer<T> listener){
			removeListener = listener;
		}



		public Mod(Consumer<T> listener, Object owner){
			addListener = listener;
			addOwner = owner;
		}
	};

	public Event() {
		// initialize empty list of listeners
		listeners = new IdentityHashMap<Object, List<Consumer<T>>>();
		modQueue = new ArrayList<Mod>();
		onceListeners = new ArrayList<Consumer<T>>();
		triggerCount = 0;
		forwardEvents = new ArrayList<Event<T>>();
		forwarder = (T value) -> {
			this.trigger(value);
		};
	}

	public void addListener(Consumer<T> newListener){
		addListener(newListener, null);
	}

	public void addListener(Consumer<T> newListener, Object owner){
		// queue operation if locked
		if(isTriggering()){
			Mod m = new Mod(newListener, owner);
			modQueue.add(m);
			return;
		}

		// create owner collection if necessary
		if(listeners.get(owner) == null){
			listeners.put(owner, new ArrayList<Consumer<T>>());
		}

		// add to owner collection
		listeners.get(owner).add(newListener);
	}

	public void addOnceListener(Consumer<T> newListener){
		addOnceListener(newListener, null);
	}

	public void addOnceListener(Consumer<T> newListener, Object owner){
		addListener(newListener, owner);
		onceListeners.add(newListener);
	}

	public void removeListener(Consumer<T> listener){
		// queue operation if locked
		if(isTriggering()){
			Mod m = new Mod(listener);
			modQueue.add(m);
			return;
		}

		// find listener
		for (Map.Entry<Object, List<Consumer<T>>> pair : listeners.entrySet()){
			Iterator<Consumer<T>> it = pair.getValue().iterator();
            while (it.hasNext()) {
            	if(it.next() == listener)
                	// remove it
            		it.remove();
            }
		}
	}

	public void removeListeners(Object owner){
		if(!isTriggering()){
			listeners.remove(owner);
			return;
		}

		// queue operation if locked
		Mod m = new Mod(owner);
		modQueue.add(m);
	}

	public void trigger(T arg){
		// count the number of (recursive) triggers
		triggerCount++;

		// call each listener
		for (Map.Entry<Object, List<Consumer<T>>> pair : listeners.entrySet()){
			for(Consumer<T> consumer : pair.getValue()){
				//System.out.println(e.getKey() + ": " + e.getValue());
				consumer.accept(arg);
			}
		}

		// remove all the listeners that should only be called once
		for(Consumer<T> listener : onceListeners){
			removeListener(listener); // also removes from onceListeners
		}

		// this trigger is done, "uncount" it
		triggerCount--;

		// currently still in the process of triggering the event?
		if(isTriggering())
			return;

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
	}

	public boolean isTriggering(){
		return triggerCount > 0;
	}

	public int size(){
		int counter=0;
		for (Map.Entry<Object, List<Consumer<T>>> pair : listeners.entrySet())
			counter += pair.getValue().size();
		return counter;
	}

	public void forward(Event<T> other){
		forwardEvents.add(other);
		other.addListener(this.forwarder, this);
	}

	public void stopForwards(){
		for(Event<T> other : forwardEvents){
			stopForward(other);
		}
	}

	public void stopForward(Event<T> other){
		other.removeListeners(this);
		this.forwardEvents.remove(other);
	}
}
