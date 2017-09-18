package com.fuse.utils;

import java.util.function.Consumer;

public class State<T> {
  private boolean bInitialized = false;
  private T value = null;

  public Event<T> newValueEvent;
  public Event<State<T>> initializedEvent;

  public State(){
    newValueEvent = new Event<>();
    initializedEvent = new Event<>();
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

  public void push(Consumer<T> func){
    this.push(func, null);
  }

  public void push(Consumer<T> func, Object owner){
  }
}
