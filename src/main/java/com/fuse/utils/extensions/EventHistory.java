package com.fuse.utils.extensions;

import java.util.ArrayList;
import java.util.List;

import com.fuse.utils.Event;

public class EventHistory<T> extends EventExtension<T> {
  private List<T> history = null;

  public EventHistory(Event<T> event){
    super(event);
  }

  @Override
  protected void setup(){
    if(history == null)
      history = new ArrayList<>();

    event.addListener((T value) -> { history.add(value); }, this);
  }

  @Override
  protected void destroy(){
    event.removeListeners(this);

    if(history != null){
      history.clear();
      history = null;
    }
  }

  public List<T> getValues(){
    return history == null ? new ArrayList<>() : history;
  }
}
