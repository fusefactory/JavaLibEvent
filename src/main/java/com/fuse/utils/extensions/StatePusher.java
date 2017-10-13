package com.fuse.utils.extensions;

import java.util.function.Consumer;
import com.fuse.utils.State;

public class StatePusher<T> extends StateExt<T> {

  private Consumer<T> func;

  public StatePusher(State<T> state, Object owner, Consumer<T> func){
    super(state, owner);
    this.func = func;
  }

  @Override protected void setup(){
    this.state.newValueEvent.addListener(func, this);
    if(this.state.isInitialized())
      this.func.accept(this.state.val());
  }

  @Override protected void destroy(){
    this.state.newValueEvent.removeListeners(this);
  }
}
