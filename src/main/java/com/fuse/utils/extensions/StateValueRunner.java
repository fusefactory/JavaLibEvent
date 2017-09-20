package com.fuse.utils.extensions;

import com.fuse.utils.State;

public class StateValueRunner<T> extends StateExt<T> {

  private T value;
  private Runnable func;

  public StateValueRunner(State<T> state, T value, Runnable func){
    super(state, null);
    this.value = value;
    this.func = func;
  }

  @Override protected void setup(){
    this.state.newValueEvent.addListener((T val) -> {
      if(val.equals(this.value))
        this.func.run();
    }, this);

    if(this.state.isInitialized() && this.state.val().equals(this.value))
      this.func.run();
  }

  @Override protected void destroy(){
    this.state.newValueEvent.removeListeners(this);
  }
}
