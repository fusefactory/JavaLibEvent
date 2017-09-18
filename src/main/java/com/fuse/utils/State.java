package com.fuse.utils;

import java.util.List;
import java.util.ArrayList;
import java.util.ListIterator;
import java.util.function.Consumer;
import com.fuse.utils.extensions.StateExt;
import com.fuse.utils.extensions.StatePusher;

public class State<T> {
  private boolean bInitialized = false;
  private T value = null;
  private List<StateExt<T>> extensions = null;

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
}
