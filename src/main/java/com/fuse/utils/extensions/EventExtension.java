package com.fuse.utils.extensions;

import com.fuse.utils.Event;

public class EventExtension<T> {
  protected Event<T> event = null;
  private boolean bEnabled = false;
  protected boolean bDone = false;

  public EventExtension(Event<T> event){
    this.event = event;
  }

  protected void setup(){
    // override
  }

  protected void destroy(){
    // override
  }

  public void enable(){
    if(isEnabled()) return;
    setup();
    bEnabled = true;
  }

  public void disable(){
    if(!isEnabled()) return;
    destroy();
    bEnabled = false;
  }

  public boolean isEnabled(){
    return bEnabled;
  }

  /** This can be used to signal to the owning event that the extension can be removed */
  public boolean isDone(){
    return bDone;
  }

  public Event<T> getEvent(){
    return event;
  }
}
