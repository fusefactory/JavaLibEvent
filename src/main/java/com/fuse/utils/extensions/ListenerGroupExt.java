package com.fuse.utils.extensions;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import com.fuse.utils.Event;

public class ListenerGroupExt<T> extends EventExtension<T> {

  private String groupId;
  private Event<T> groupEvent = new Event<T>();

  public ListenerGroupExt(Event<T> event, String groupId){
    super(event);
    this.groupId = groupId;
  }

  public String getGroupId() {
    return this.groupId;
  }

  public void addListener(Consumer<T> newListener, Object owner){
    this.groupEvent.addListener(newListener, owner);
    this.event.addListener(newListener, owner);
  }

  public void stopListeners(Object owner) {
    List<Consumer<T>> ls = this.groupEvent.getOwnerListeners(owner);
    for(Consumer<T> l : ls) {
      this.event.removeListener(l);
      this.groupEvent.removeListeners(l);
    }
  }

  public void stopListeners() {
    this.event.removeListeners(this.groupEvent.getAllListeners());
    this.groupEvent.destroy();
  }
}
