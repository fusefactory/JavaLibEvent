package com.fuse.utils;

import java.util.List;
import java.util.ArrayList;
import java.util.ListIterator;
import java.util.function.Consumer;
import com.fuse.utils.extensions.StateExt;
import com.fuse.utils.extensions.StatePusher;
import com.fuse.utils.extensions.StateValueRunner;

public class State<T> {
  private boolean bInitialized = false;
  private T value = null;
  private List<StateExt<T>> extensions = null;

  public Event<T> newValueEvent;
  public Event<State<T>> initializedEvent;

  public State(){
    this(null);
  }

  public State(T initialValue){
    newValueEvent = new Event<>();
    initializedEvent = new Event<>();
    this.set(initialValue); // will trigger some event, but those have not subscribers yet
  }

  public void destroy(){
    this.value = null;
    this.newValueEvent.destroy();
    this.initializedEvent.destroy();

    if(this.extensions != null){

      ListIterator it = this.extensions.listIterator();
      while(it.hasNext()){
        StateExt<T> ext = (StateExt<T>)it.next();
        ext.disable();
        it.remove();
      }

      this.extensions = null;
    }
  }

  public State<T> set(T value){
    boolean change = this.value == null || !this.value.equals(value);
    this.value = value;

    if(!bInitialized && value != null){
      bInitialized = true;
      initializedEvent.trigger(this);
    }

    if(change)
      this.newValueEvent.trigger(this.value);

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

  public void stopPushes(Object owner){
    if(this.extensions == null)
      return;

    ListIterator it = this.extensions.listIterator();
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
}
