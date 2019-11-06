package com.fuse.utils.extensions;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import com.fuse.utils.Event;

public class ForwardExt<T> extends EventExtension<T> {

  Consumer<T> forwarder;
  private List<Event<T>> forwardSources = new ArrayList<>();

  public ForwardExt(Event<T> event){
    super(event);
    forwarder = (T t) -> this.event.trigger(t);
  }

  @Override
  protected void destroy(){
    this.removeAll();
  }

  public void add(Event<T> source) {
    forwardSources.add(source);
    source.addListener(this.forwarder);
  }

  public void removeAll(){
    while (forwardSources.size() > 0)
      this.remove(this.forwardSources.get(0));
  }

  public void remove(Event<T> source) {
    source.removeListener(this.forwarder);
    this.forwardSources.remove(source);
  }
}
