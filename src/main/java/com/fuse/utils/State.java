package com.fuse.utils;

import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;
import java.util.function.Consumer;

import com.fuse.utils.extensions.StateExt;
import com.fuse.utils.extensions.StatePusher;
import com.fuse.utils.extensions.StateValueRunner;

public class State<T> {

  class ChangeArgs {
    public T previous;
    public T current;
    public ChangeArgs(T p, T c){ previous = p; current = c; }
  }

  private boolean bInitialized = false;
  private T value = null;
  private List<StateExt<T>> extensions = null;

  public Event<T> newValueEvent = new Event<>();
  public Event<State<T>> initializedEvent = new Event<>();
  public Event<ChangeArgs> changeEvent = new Event<>();

  public State(){
  }

  public State(T initialValue){
    this.set(initialValue); // will trigger some event, but those have not subscribers yet
  }

  public void destroy(){
    this.value = null;
    this.newValueEvent.destroy();
    this.initializedEvent.destroy();
    this.changeEvent.destroy();

    if(this.extensions != null){

      ListIterator<StateExt<T>> it = this.extensions.listIterator();
      while(it.hasNext()){
        StateExt<T> ext = (StateExt<T>)it.next();
        ext.disable();
        it.remove();
      }

      this.extensions = null;
    }
  }

  public State<T> set(T value){
    T prevValue = this.value;
    this.value = value;

    if(!bInitialized && value != null){
      bInitialized = true;
      initializedEvent.trigger(this);
    }

    boolean change =  (this.value != null && !this.value.equals(prevValue)) || this.value == null && prevValue != null;

    if(change && this.value != null)
      this.newValueEvent.trigger(this.value);

    if(change)
      this.changeEvent.trigger(new ChangeArgs(prevValue, this.value));

    return this;
  }

  public T get(){
    return this.value;
  }

  public T val(){
    return this.value;
  }

  public boolean isInitialized(){
    return bInitialized;
  }

  public void reset(){
    this.value = null;
    this.bInitialized = false;
  }

  // extensions // // // // //

  public State<T> addExtension(StateExt<T> ext){
    if(this.extensions == null)
      this.extensions = new ArrayList<>();

    this.extensions.add(ext);
    return this;
  }

  public State<T> removeExtension(StateExt<T> ext){
    if(this.extensions != null)
      this.extensions.remove(ext);
    return this;
  }

  public void push(Consumer<T> func){
    this.push(func, null);
  }

  public void push(Consumer<T> func, Object owner){
    StatePusher<T> ext = new StatePusher<>(this, owner, func);
    ext.enable();
    this.addExtension(ext);
  }

  /// convenience method for always pushing this state's values into another state
  public void push(State<T> target){
    this.push(target, null);
  }

  public void push(State<T> target, Object owner){
    this.push((T val) -> { target.set(val); }, owner);
  }

  public void stopPushes(Object owner){
    if(this.extensions == null)
      return;

    ListIterator<StateExt<T>> it = this.extensions.listIterator();
    while(it.hasNext()){
      StateExt<T> ext = (StateExt<T>)it.next();

      if(StatePusher.class.isInstance(ext) && ext.owner == owner){
        ext.disable();
        it.remove();
      }
    }
  }

  public State<T> when(T value, Runnable func){
    StateValueRunner<T> ext = new StateValueRunner<>(this, value, func);
    ext.enable();
    this.addExtension(ext);
    return this;
  }

  public State<T> whenNot(T value, Runnable func){
    StateValueRunner<T> ext = new StateValueRunner<>(this, value, func, true);
    ext.enable();
    this.addExtension(ext);
    return this;
  }

  public State<T> whenNot(T value, Consumer<T> func){
    Runnable wrapper = () -> func.accept(this.val());
    return this.whenNot(value, wrapper);
  }

  public State<T> whenOnce(T value, Runnable func){
    StateValueRunner<T> ext = new StateValueRunner<>(this, value, func);
    ext.setOnce();
    ext.enable();
    this.addExtension(ext);
    return this;
  }
}
