package com.fuse.utils.extensions;

import com.fuse.utils.State;

public class StateValueRunner<T> extends StateExt<T> {

  private T value;
  private Runnable func;
  private int count = 0;
  private Integer maxTimes = null;
  private boolean bNegative = false;

  public StateValueRunner(State<T> state, T value, Runnable func){
    super(state, null);
    this.value = value;
    this.func = func;
  }

  public StateValueRunner(State<T> state, T value, Runnable func, boolean negative){
    this(state, value, func);
    this.setIsNegative(negative);
  }

  public StateValueRunner<T> setMaxTimes(Integer times){ this.maxTimes = times; return this; }

  public StateValueRunner<T> setOnce(){ return this.setMaxTimes(1); }

  @Override protected void setup(){
    this.state.newValueEvent.addListener((T val) -> { this.check(val); }, this);
    this.check(this.state.get());
  }

  @Override protected void destroy(){
    this.state.newValueEvent.removeListeners(this);
  }

  public StateValueRunner<T> setIsNegative(boolean negative){ this.bNegative = negative; return this; }
  public boolean isNegative(){ return this.bNegative; }

  private void check(T val){
    boolean equal = ((val != null && val.equals(this.value)) || (val == null && this.value == null));

    if(equal ^ this.bNegative)
      this.run();
  }

  private void run(){
    this.func.run();
    count += 1;

    if(this.maxTimes != null && count >= this.maxTimes){
      this.destroy();
    }
  }
}
