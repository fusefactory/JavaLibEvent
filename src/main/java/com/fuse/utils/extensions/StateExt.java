package com.fuse.utils.extensions;

import com.fuse.utils.State;

public class StateExt<T> {
  protected State<T> state;
  private boolean bEnabled = false;

  public Object owner; // simply "payload" attribute, not to be user inside the extension

  public StateExt(State<T> state){
    this(state, null);
  }

  public StateExt(State<T> state, Object owner){
    this.state = state;
    this.owner = owner;
  }

  public State<T> getState(){
    return this.state;
  }

  public boolean isEnabled(){
    return this.bEnabled;
  }

  public void setEnabled(boolean enable){
    if(this.bEnabled && !enable){
      this.destroy();
    }

    if(enable && !this.bEnabled){
      this.setup();
    }

    this.bEnabled = enable;
  }

  public void enable(){
    this.setEnabled(true);
  }

  public void disable(){
    this.setEnabled(false);
  }

  protected void setup(){
    // virtual; override
  }

  protected void destroy(){
    // virtual; override
  }
}
