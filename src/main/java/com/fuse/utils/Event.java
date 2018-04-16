package com.fuse.utils;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Consumer;

import com.fuse.utils.extensions.EventExtension;
import com.fuse.utils.extensions.EventHistory;
import com.fuse.utils.extensions.OnceListener;

/**
* @author Mark van de Korput
*
* The Event class implements the Observer pattern
* in a reusable and safe way.
*/
public class Event <T> {
    /* optimized -flat- list over all callable listeners */
    private List<Consumer<T>> listeners = null;
    /** holds the owner of every registered listener */
    private Map<Consumer<T>, Object> owners = null;
    /** Holds the number of _currently active_ trigger operations (more than 1 means recursive triggers) */
    private int triggerCount = 0;
    /** Holds a list of Mod operations to execute after the event finishes all current notifications */
    private ConcurrentLinkedQueue<Mod> modQueue = null;
    /** Holds a list of events that are currently being forwarded by this event */
    private List<Event<T>> forwardEvents = null;
    /** Holds our listener-logic for forwarding other events */
    private Consumer<T> forwarder = null;
    private Event<Void> parameterlessEvent = null;

    private List<EventExtension<T>> extensions = null;

    private class Mod {
        public Consumer<T> addListener;
        public Object addOwner;
        public Consumer<T> removeListener;
        public Object removeListenersOwner;
        public boolean destroy = false;
    };

    // public Event() {
    // }

    public void destroy(){
        if(isTriggering()){
            Mod m = new Mod();
            m.destroy = true;
            queueMod(m);
            return;
        }

        if(extensions != null)
            for(int i=extensions.size()-1; i>=0; i--)
                removeExtension(extensions.get(i));

        stopForwards();
        forwarder = null;
        modQueue = null;

        if(parameterlessEvent != null){
            parameterlessEvent.destroy();
            parameterlessEvent = null;
        }

        // brute-force these removals
        if(listeners != null){
            listeners.clear();
            listeners = null;
        }

        if(owners != null){
            owners.clear();
            owners = null;
        }
    }

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
            Mod m = new Mod();
            m.addListener = newListener;
            m.addOwner = owner;
            queueMod(m);
            return;
        }

        // lazy initializing
        if(listeners == null)
            listeners = new ArrayList<>();

        listeners.add(newListener);

        // create owner collection if necessary
        if(owners == null)
            owners = new IdentityHashMap<>();

        owners.put(newListener, owner);
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
        if(owners == null || listeners == null)
            return; // nothing to remove

        // queue operation if locked
        if(isTriggering()){
            Mod m = new Mod();
            m.removeListener = listener;
            queueMod(m);
            return;
        }

        if(listeners.remove(listener)){
            if(listeners.isEmpty())
                listeners = null;

            if(owners != null){
                if(owners.remove(listener) != null)
                    if(owners.isEmpty())
                        owners = null;
            }
        }
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
        if(owners == null)
            return;

        // queue operation if locked
        if(isTriggering()){
            Mod m = new Mod();
            m.removeListenersOwner = owner;
            queueMod(m);
            return;
        }

        if(listeners != null){
            for(int idx=listeners.size()-1; idx>=0; idx--){
                Consumer<T> listener = listeners.get(idx);
                if(owners.get(listener) == owner)
                    removeListener(listener);
            }
        }
    }

    /**
     * Start notification of all registered listeners with the given payload
     *
     * @param arg the payload to give to all listeners
     */
    public void trigger(T arg){
        // count the number of (recursive) triggers; locks this event from modifications
        triggerCount++;

        if(listeners != null){
            for(int idx=0; idx<listeners.size(); idx++){
                listeners.get(idx).accept(arg);
            }
        }

        // also trigger "whenTriggered" callbacks without parameters
        if(parameterlessEvent != null)
            parameterlessEvent.trigger(null);

        // this trigger is done, "uncount" it
        triggerCount--;

        // no more (recursive) trigger operations active? process mod queue
        if(!isTriggering() && this.modQueue != null)
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
            counter += listeners.size();

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

        // note; stopForward will set forwardEvents to null if it becomes empty
        while(forwardEvents != null && !forwardEvents.isEmpty())
            stopForward(forwardEvents.get(0));

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
        return (owners != null && owners.containsValue(owner));
    }

    /**
     * The hasListener method informs the caller if a given Consumer instance is currently registered as listener of this event.
     * @param listener The owner for which to check
     * @return boolean True if there are any listeners for the specified owner registered
     */
    public boolean hasListener(Consumer<T> listener){
        return (listeners != null && listeners.contains(listener));
    }

    private void queueMod(Mod mod){
        if(modQueue == null)
            modQueue = new ConcurrentLinkedQueue<Mod>();
        modQueue.add(mod);
    }

    private void processModQueue(){
        while(true) {
        	Mod m = this.modQueue.poll();
        	
        	if(m == null)
        		return;

            if(m.destroy){
                this.destroy();
                return;
            }

            if(m.addListener != null)
                this.addListener(m.addListener, m.addOwner);

            if(m.removeListener != null)
                this.removeListener(m.removeListener);

            if(m.removeListenersOwner != null)
                this.removeListeners(m.removeListenersOwner);
        }
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
        if(parameterlessEvent != null){
            parameterlessEvent.destroy();
            parameterlessEvent = null;
        }
    }

    /** Removes all callbacks registered using the whenTriggered for this specific owner */
    public void stopWhenTriggeredCallbacks(Object owner){
        if(parameterlessEvent == null)
            return; // nothing to remove

        parameterlessEvent.removeListeners(owner);

        if(parameterlessEvent.size() == 0){
            parameterlessEvent.destroy();
            parameterlessEvent = null; // cleanup if possible
        }
    }

    //
    // extensions
    //

    public void addExtension(EventExtension<T> ext){
        if(extensions == null) // lazy-init
            extensions = new ArrayList<>();

        extensions.add(ext);

        // do some maintenance
        removeDoneExtensions();
    }

    public boolean removeExtension(EventExtension<T> ext){
        if(extensions == null)
            return false; // nothing to remove

        boolean result = extensions.remove(ext);

        if(extensions.isEmpty())
            extensions = null; // cleanup

        return result;
    }

    public void enable(EventExtension<T> ext){
        this.addExtension(ext);
        ext.enable();
    }

    private void removeDoneExtensions(){
        if(this.extensions == null)
            return;

        for(int i=extensions.size()-1; i>=0; i--){
            EventExtension<T> ext = extensions.get(i);
            if(ext.isDone()){
                removeExtension(ext);
                // removeExtension sets extensions to null when it's empty
                if(extensions == null)
                    return;
            }
        }
    }

    //
    // OnceListener extension
    //

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
        this.enable(new OnceListener<T>(this, newListener, owner));
    }

    //
    // EventHistory extensions
    //

    private EventHistory<T> getHistoryExtension(){
        if(extensions == null) return null;

        for(int i=0; i<extensions.size(); i++){
            EventExtension<T> ext = extensions.get(i);
            if(EventHistory.class.isInstance(ext))
                return (EventHistory<T>)ext;
        }

        return null;
    }
    /**
     * Returns the recorded list of values that have been triggered before
     * @return List The recorded history of triggered values
     */
    public List<T> getHistory(){
        EventHistory<T> ext = getHistoryExtension();
        return ext == null ? new ArrayList<>() : ext.getValues();
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
        EventHistory<T> ext = getHistoryExtension();

        // disable
        if(!enable){
            if(ext != null)
                ext.disable();

            return;
        }

        // enable
        if(ext == null)
            this.enable(new EventHistory<T>(this));
        else
            ext.enable();
    }

    /**
     * Returns true if this event is currently recording it history (false by default)
     * @return boolean The current history-recording status
     */
    public boolean isHistoryEnabled(){
        EventHistory<T> ext = getHistoryExtension();
        return ext != null && ext.isEnabled();
    }

    /**
     * Runs the given logic for all values that are recorded into the internal history (if enabled)
     * and also registers the logic like a "normal" listener.
     * @param func The Listener which should also be invoked for all history values
     */
    public void withAllValues(Consumer<T> func){
        List<T> values = this.getHistory();

        if(values != null){
            for(int i=0; i<values.size(); i++){
                T value = values.get(i);
                func.accept(value);
            }
        }

        addListener(func);
    }
}
