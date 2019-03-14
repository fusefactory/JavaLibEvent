package com.fuse.utils;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.LinkedList;
import java.util.function.Consumer;
//import java.util.concurrent.locks.ReentrantLock;

import com.fuse.utils.extensions.EventExtension;
import com.fuse.utils.extensions.EventHistory;
import com.fuse.utils.extensions.OnceListener;
import com.fuse.utils.extensions.ListenerGroupExt;
import com.fuse.utils.extensions.ForwardExt;

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
    private Queue<Runnable> modOpsQueue; // mods to be executed when modification is possible
    private Queue<Consumer<Consumer<T>[]>> postModOpsQueue; // triggers to be executed after modification ends

    private List<EventExtension<T>> extensions = null;
    private final Object listenersLock = new Object();

    public void destroy(){
        this.modify(() -> {

            if(extensions != null)
                for(int i=extensions.size()-1; i>=0; i--) {
                    EventExtension<T> ext = extensions.get(i);
                    ext.disable();
                    removeExtension(ext);
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
        });
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

    private void freeze(Consumer<Consumer<T>[]> func) {
        if (hasActiveModifiers()) {
            if (this.postModOpsQueue == null) this.postModOpsQueue = new LinkedList<>();
            this.postModOpsQueue.add(func);
            return;
        }

        triggerCount++;
        // func.run()
        if (this.listeners != null) {
            Consumer<T>[] ar = this.listeners.toArray(new Consumer[0]);
            func.accept(ar);
        }

        // this trigger is done, "uncount" it
        triggerCount--;
        doEndModBlocker();
    }

    private boolean isFrozen() {
        return triggerCount > 0;
    }

    private boolean canModify() {
        return !(this.isFrozen() || this.hasActiveModifiers());
    }

    private int activeModifiersCount = 0;
    private boolean hasActiveModifiers() { return this.activeModifiersCount > 0; }

    private void modify(Runnable func) {
        if(canModify()) {
            activeModifiersCount += 1;
            func.run();
            activeModifiersCount -= 1;
            doEndModifications();
            doEndModBlocker();
            return;
        }

        if (modOpsQueue == null) modOpsQueue = new LinkedList<>();
        modOpsQueue.add(func);
    }

    /// Checks if there are queued post-block operations and executes them if there ar no other blocks left
    private void doEndModBlocker() {
        if (this.modOpsQueue != null && canModify() && this.modOpsQueue.size() > 0) {
            // this.modify(() -> {
                while (true) {
                    Runnable r = this.modOpsQueue.poll();
                    if(r == null) return;
                    this.modify(r); //.run();
                }
            // });
        }
    }

    private void doEndModifications() {
        if (this.postModOpsQueue != null && !hasActiveModifiers() && this.postModOpsQueue.size() > 0) {
            while (true) {
                Consumer<Consumer<T>[]> r = this.postModOpsQueue.poll();
                if(r == null) return;
                this.freeze(r);
            }
        }
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
        this.modify(() -> {
            // lazy initializing
            if (this.listeners == null) this.listeners = new ArrayList<>();
            // List<Consumer<T>> list = this.listeners == null ? new ArrayList<>() : this.listeners;
            this.listeners.add(newListener);

            // synchronized(this.listenersLock) {
            //     this.listeners = list;
            // }

            // create owner collection if necessary
            if(owners == null)
                owners = new IdentityHashMap<>();

            owners.put(newListener, owner);
        });

        // // queue operation if locked
        // if(isTriggering()){
        //     Mod m = new Mod();
        //     m.addListener = newListener;
        //     m.addOwner = owner;
        //     queueMod(m);
        //     return;
        // }
        //
        //
        // // lazy initializing
        // if (this.listeners == null) this.listeners = new ArrayList<>();
        // // List<Consumer<T>> list = this.listeners == null ? new ArrayList<>() : this.listeners;
        // this.listeners.add(newListener);
        //
        // // synchronized(this.listenersLock) {
        // //     this.listeners = list;
        // // }
        //
        // // create owner collection if necessary
        // if(owners == null)
        //     owners = new IdentityHashMap<>();
        //
        // owners.put(newListener, owner);
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
        this.modify(() -> {
            // fetch local instances to avoid race-condition errors
            List<Consumer<T>> listeners = this.listeners;
            Map<Consumer<T>, Object> owners = this.owners;

            if(owners == null || listeners == null)
                return; // nothing to remove

            // synchronized(this.listenersLock) {
                if(listeners.remove(listener)){
                    // if(listeners.isEmpty())
                    //    this.listeners = null;

                    if(owners != null){
                        owners.remove(listener);
                        // if(owners.remove(listener) != null)
                        //     if(owners.isEmpty())
                        //         this.owners = null;
                    }
                }
            // }
        });
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
        this.modify(() -> {
            List<Consumer<T>> ls = getOwnerListeners(owner);
            for(Consumer<T> l : ls) {
                removeListener(l);
            }
        });
    }

    public final List<Consumer<T>> getAllListeners() {
        return this.listeners;
    }

    public List<Consumer<T>> getOwnerListeners(Object owner) {
        List<Consumer<T>> ls = new ArrayList<Consumer<T>>();

        // fetch local instance to avoid race-condition errors
        Map<Consumer<T>, Object> owners = this.owners;

        if(owners == null)
        return ls;

        // fetch local instance to avoid race-condition errors
        List<Consumer<T>> listeners = this.listeners;
        if(listeners == null) return ls;
        for(int idx=listeners.size()-1; idx>=0; idx--){
            Consumer<T> listener = listeners.get(idx);
            if(owners.get(listener) == owner)
                ls.add(listener);
        }

        return ls;
    }

    /**
     * Start notification of all registered listeners with the given payload
     *
     * @param arg the payload to give to all listeners
     */
    public void trigger(T arg) {
        this.freeze((frozenListeners) -> {
            // fetch local instance to avoid race-condition errors
            // List<Consumer<T>> listeners = this.listeners;

            if(listeners != null){
                for(int idx=0; idx<frozenListeners.length; idx++){
                    // synchronized(this.listenersLock) {
                    Consumer<T> func = frozenListeners[idx];
                    if (func != null) func.accept(arg);
                    // }
                }
            }
        });
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
      return this.listeners == null ? 0 : listeners.size();
    }


    private ForwardExt<T> getForwardExt(){
        // find existing
        if(extensions != null) {
            for(int i=0; i<extensions.size(); i++){
                EventExtension<T> ext = extensions.get(i);
                if(ForwardExt.class.isInstance(ext)) {
                    ForwardExt<T> lext = (ForwardExt<T>)ext;
                    return lext;
                }
            }
        }

        ForwardExt<T> lext = new ForwardExt<T>(this);
        this.enable(lext);
        return lext;
    }

    /**
     * Registers this event as a listener to the specified event to
     * forward the specified event's notifications to our own listeners
     *
     * @param other source event who's notifications to forward to our own listeners
     */
    public void forward(Event<T> other){
        this.getForwardExt().add(other);
    }

    /**
     * Stop forwarding all events that were being forwarded using .forward();
     */
    public void stopForwards(){
        this.getForwardExt().removeAll();
    }

    /**
     * Stop forwarding specific event that was being forwarded using .forward();
     * @param other source event to stop forwarding
     */
    public void stopForward(Event<T> other){
        this.getForwardExt().remove(other);
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



    private ListenerGroupExt<T> getArglessListenerGroupExtension(){
        String groupId = "ArglessListeners";

        // find existing
        if(extensions != null) {
            for(int i=0; i<extensions.size(); i++){
                EventExtension<T> ext = extensions.get(i);
                if(ListenerGroupExt.class.isInstance(ext)) {
                    ListenerGroupExt<T> lext = (ListenerGroupExt<T>)ext;
                    if (lext.getGroupId().equals(groupId));
                        return lext;
                }
            }
        }

        ListenerGroupExt<T> lext = new ListenerGroupExt<T>(this, groupId);
        this.enable(lext);
        return lext;
    }

    /**
     * Calls whenTriggered with the given runnable and the default null owner, see whenTriggered(Runnable, Object)
     * @param func The ownerless callback
     */
    public void whenTriggered(Runnable func){
      this.getArglessListenerGroupExtension().addListener(
        (T arg) -> func.run(),
        null);
    }

    /**
     * Register parameter-less callback (Runnable instance) that gets invoked
     * every time this event is triggered, just like "normal" listeners,
     * but without parameters
     * @param func The listener to be invoked every time this even is triggered
     * @param owner The owner by which this listener can be removed using stopWhenTriggeredCallbacks
     */
    public void whenTriggered(Runnable func, Object owner){
        this.getArglessListenerGroupExtension().addListener(
          (T arg) -> func.run(),
          owner);
    }

    /** Removes all callbacks registered using the whenTriggered methods */
    public void stopWhenTriggeredCallbacks(){
        this.getArglessListenerGroupExtension().stopListeners();
    }

    /** Removes all callbacks registered using the whenTriggered for this specific owner */
    public void stopWhenTriggeredCallbacks(Object owner){
        this.getArglessListenerGroupExtension().stopListeners(owner);
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
                ext.disable();
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

    public String debugInfo() {
      return "EVENT DEBUG INFO:\ntriggerCount: "+Integer.toString(this.triggerCount)+"\nModQueue count: "+Integer.toString(this.modOpsQueue.size());
    }
}
