package com.fuse.utils.extensions;

import com.fuse.utils.State;

public class StateValueRunner<T> extends StateExt<T> {

  private T value;
  private Runnable func;
  private int count = 0;
  private Integer maxTimes = null;

  public StateValueRunner(State<T> state, T value, Runnable func){
    super(state, null);
    this.value = value;
    this.func = func;
  }

  public StateValueRunner setMaxTimes(Integer times){ this.maxTimes = times; return this; }

  public StateValueRunner setOnce(){ return this.setMaxTimes(1); }

  @Override protected void setup(){
    this.state.newValueEvent.addListener((T val) -> {
      if(val.equals(this.value))
        this.run();
    }, this);

    if(this.state.isInitialized() && this.state.val().equals(this.value))
      this.run();
  }

  @Override protected void destroy(){
    this.state.newValueEvent.removeListeners(this);
  }

  private void run(){
    this.func.run();
    count += 1;

    if(this.maxTimes != null && count >= this.maxTimes){
      this.destroy();
    }
  }
}
