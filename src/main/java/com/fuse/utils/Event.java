package com.fuse.utils;

import java.util.Map;
import java.util.IdentityHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Iterator;
import java.util.function.Consumer;

/**
* @author Mark van de Korput
*
* The Event class implements the Observer pattern
* in a reusable and safe way.
*/
public class Event <T> {
    /** Holds all the registered listeners, mapped by owner */
    private Map<Object, List<Consumer<T>>> listeners = null;
    /** Holds a list of listeners that should be removed after the next notification */
    private List<Consumer<T>> onceListeners = null;
    /** Holds the number of _currently active_ trigger operations (more than 1 means recursive triggers) */
    private int triggerCount = 0;
    /** Holds a list of Mod operations to execute after the event finishes all current notifications */
    private List<Mod> modQueue = null;
    /** Holds a list of events that are currently being forwarded by this event */
    private List<Event<T>> forwardEvents = null;
    /** Holds our listener-logic for forwarding other events */
    private Consumer<T> forwarder = null;
    /** List into which all triggered values are recorded (when enabled) */
    private List<T> history = null;
    private Event<Void> parameterlessEvent = null;

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

    /**
     * Default constructor.
     */
    // public Event() {
    // }

    /**
     * Registers a new listener with default null owner.
     * If this event is currently triggering (thus iterating over its listeners)
     * the specified listener won't actually be registered until the current
     * notifications have finished.
     *
     * @param newListener reference to the listener that should be registered
     */
    public void addListener(Consumer<T> newListener){
        addListener(newListener, null);
    }

    /**
     * Register a new listener.
     * If this event is currently triggering (thus iterating over its listeners)
     * the specified listener won't actually be registered until the current
     * notifications have finished.
     *
     * @param newListener reference to the listener that should be registered
     * @param owner owner of the new listener
     */
    public void addListener(Consumer<T> newListener, Object owner){
        // queue operation if locked
        if(isTriggering()){
            queueMod(new Mod(newListener, owner));
            return;
        }

        // lazy initializing
        if(listeners == null)
            listeners = new IdentityHashMap<>();

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

    /**
     * Add listener that should be called only once (for the first upcoming notification).
     * The listener is removed immediately after the first notification.
     * If this event is currently triggering (thus iterating over its listeners)
     * the specified listener won't actually be registered until the current
     * notifications have finished.
     *
     * @param newListener reference to the listener that should be registered
     * @param owner owner of the new listener
     */
    public void addOnceListener(Consumer<T> newListener, Object owner){
        addListener(newListener, owner);

        if(onceListeners == null)
            onceListeners = new ArrayList<Consumer<T>>();
        onceListeners.add(newListener);
    }

    private void removeOnceListener(Consumer<T> func){
        if(onceListeners == null)
            return;

        if(onceListeners.contains(func))
            onceListeners.remove(func);

        if(onceListeners.isEmpty())
            onceListeners = null;
    }

    /**
     * Remove a specific listener by listener reference
     * If this event is currently triggering (thus iterating over its listeners)
     * the specified listener won't actually be removed until the current
     * notifications have finished.
     *
     * @param listener reference to the actual listener that should be removed
     */
    public void removeListener(Consumer<T> listener){
        if(listeners == null)
            return; // nothing to remove

        // queue operation if locked
        if(isTriggering()){
            queueMod(new Mod(listener));
            return;
        }

        // find listener
        Iterator<Map.Entry<Object, List<Consumer<T>>>> mapIt = listeners.entrySet().iterator();
        while(mapIt.hasNext()){
            Map.Entry<Object, List<Consumer<T>>> pair = mapIt.next();

            Iterator<Consumer<T>> it = pair.getValue().iterator();

            while (it.hasNext()) {
                if(it.next() == listener){
                    // remove it
                    it.remove();
                }
            }

            if(pair.getValue().isEmpty())
                mapIt.remove();
        }

        // also remove from onceListeners list
        removeOnceListener(listener);
    }

    /**
     * Remove all listeners that were registered with the specified owner.
     * If this event is currently triggering (thus iterating over its listeners)
     * the specified listeners won't actually be removed until the current
     * notifications have finished.
     *
     * @param owner owner of the listeners that should be removed
     */
    public void removeListeners(Object owner){
        if(listeners == null)
            return; // nothing to remove

        // queue operation if locked
        if(isTriggering()){
            queueMod(new Mod(owner));
            return;
        }

        // also remove from onceListeners if necessary
        List<Consumer<T>> ownerListeners = listeners.get(owner);
        if(ownerListeners != null)
            for(Consumer<T> func : ownerListeners)
                removeOnceListener(func);

        listeners.remove(owner);

        if(listeners.isEmpty())
            listeners = null;
    }

    /**
     * Start notification of all registered listeners with the given payload
     *
     * @param arg the payload to give to all listeners
     */
    public void trigger(T arg){
        // count the number of (recursive) triggers
        triggerCount++;

        if(listeners != null){
            // call each listener
            for (Map.Entry<Object, List<Consumer<T>>> pair : listeners.entrySet()){
                for(Consumer<T> consumer : pair.getValue()){
                    //System.out.println(e.getKey() + ": " + e.getValue());
                    consumer.accept(arg);
                }
            }
        }

        // also trigger "whenTriggered" callbacks without parameters
        if(parameterlessEvent != null)
            parameterlessEvent.trigger(null);

        // record history, if enabled
        if(history != null)
            history.add(arg);

        // remove all the listeners that should only be called once
        if(onceListeners != null){ // if there's a onceListener, there must be a listener, no need to check listeners != null
            for(Consumer<T> listener : onceListeners){
                // this also removes from onceListeners
                removeListener(listener);
            }
        }

        // this trigger is done, "uncount" it
        triggerCount--;

        // no more (recursive) trigger operations active? process mod queue
        if(!isTriggering())
            processModQueue();
    }

    /**
     * Returns if the event is currently triggering
     * (and thus iterating over it's listeners)
     *
     * @return boolean
     */
    public boolean isTriggering(){
        return triggerCount > 0;
    }

    /**
     * Returns the number of registered listeners
     *
     * @return int
     */
    public int size(){
        int counter=0;
        if(listeners != null)
            for (Map.Entry<Object, List<Consumer<T>>> pair : listeners.entrySet())
                counter += pair.getValue().size();
        return counter;
    }

    /**
     * Registers this event as a listener to the specified event to
     * forward the specified event's notifications to our own listeners
     *
     * @param other source event who's notifications to forward to our own listeners
     */
    public void forward(Event<T> other){
        // we lazily initialize the forwarded (instead of in the constructor),
        // so it doesn't take up any memory in events that don't use it (which is most events)
        if(forwarder == null){
            forwarder = (T value) -> {
                this.trigger(value);
            };
        }

        if(forwardEvents == null){
            forwardEvents = new ArrayList<Event<T>>();
        }

        forwardEvents.add(other);
        other.addListener(this.forwarder, forwarder);
    }

    /**
     * Stop forwarding all events that were being forwarded using .forward();
     */
    public void stopForwards(){
        if(forwardEvents == null)
            return;

        for(Event<T> other : forwardEvents)
            stopForward(other);

        forwardEvents = null;
    }

    /**
     * Stop forwarding specific event that was being forwarded using .forward();
     * @param other source event to stop forwarding
     */
    public void stopForward(Event<T> other){
        other.removeListener(this.forwarder);

        if(forwardEvents == null)
            return;

        forwardEvents.remove(other);

        if(forwardEvents.isEmpty())
            forwardEvents = null;
    }

    /**
     * @param owner The owner for which to check
     * @return boolean True if there are any listeners for the specified owner registered
     */
    public boolean hasOwner(Object owner){
        return listeners == null ? false : listeners.containsKey(owner);
    }

    /**
     * The hasListener method informs the caller if a given Consumer instance is currently registered as listener of this event.
     * @param listener The owner for which to check
     * @return boolean True if there are any listeners for the specified owner registered
     */
    public boolean hasListener(Consumer<T> listener){
        if(listeners == null)
            return false;

        for(List<Consumer<T>> ownerListeners : listeners.values())
            if(ownerListeners.contains(listener))
                return true;
        return false;
    }

    /**
     * Returns the recorded list of values that have been triggered before
     * @return List The recorded history of triggered values
     */
    public List<T> getHistory(){
        return history;
    }

    /** Enables history recording */
    public void enableHistory(){
        enableHistory(true);
    }

    /**
     * Enables history recording
     * @param enable When true; enables history recording, otherwise it disables history recording
     */
    public void enableHistory(boolean enable){
        // disable
        if(!enable){
            history = null;
            return;
        }

        // enable
        if(history == null)
            history = new ArrayList<>();
    }

    /**
     * Returns true if this event is currently recording it history (false by default)
     * @return boolean The current history-recording status
     */
    public boolean isHistoryEnabled(){
        return history != null;
    }

    /**
     * Runs the given logic for all values that are recorded into the internal history (if enabled)
     * and also registers the logic like a "normal" listener.
     * @param func The Listener which should also be invoked for all history values
     */
    public void withAllValues(Consumer<T> func){
        addListener(func);
        enableHistory(true);
        for(T value : history)
            func.accept(value);
    }

    private void queueMod(Mod mod){
        if(modQueue == null)
            modQueue = new ArrayList<Mod>();
        modQueue.add(mod);
    }

    private void processModQueue(){
        if(modQueue == null)
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
        modQueue = null;
    }

    /**
     * Calls whenTriggered with the given runnable and the default null owner, see whenTriggered(Runnable, Object)
     * @param func The ownerless callback
     */
    public void whenTriggered(Runnable func){
        whenTriggered(func, null);
    }

    /**
     * Register parameter-less callback (Runnable instance) that gets invoked
     * every time this event is triggered, just like "normal" listeners,
     * but without parameters
     * @param func The listener to be invoked every time this even is triggered
     * @param owner The owner by which this listener can be removed using stopWhenTriggeredCallbacks
     */
    public void whenTriggered(Runnable func, Object owner){
        if(parameterlessEvent == null)
            parameterlessEvent = new Event<>();

        parameterlessEvent.addListener((Void voi) -> {
            func.run();
        }, owner);
    }

    /** Removes all callbacks registered using the whenTriggered methods */
    public void stopWhenTriggeredCallbacks(){
        parameterlessEvent = null;
    }

    /** Removes all callbacks registered using the whenTriggered for this specific owner */
    public void stopWhenTriggeredCallbacks(Object owner){
        if(parameterlessEvent == null)
            return; // nothing to remove

        parameterlessEvent.removeListeners(owner);

        if(parameterlessEvent.size() == 0)
            parameterlessEvent = null; // cleanup if possible
    }
}
