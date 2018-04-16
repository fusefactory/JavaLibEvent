package com.fuse.utils.extensions;

import java.util.function.Consumer;

import com.fuse.utils.Event;

public class OnceListener<T> extends EventExtension<T> {
  private Consumer<T> listener;
  private Object owner;
  private Consumer<T> wrappedListener;

  public OnceListener(Event<T> event, Consumer<T> listener, Object owner){
    super(event);
    this.listener = listener;
    this.owner = owner;
  }

  @Override
  protected void setup(){
    // wrap original listener in a self-removing wrapper
    wrappedListener = (T payload) -> {
      // call original listener
      this.listener.accept(payload);
      // remove registered (wrapped) listener
      this.event.removeListener(this.wrappedListener);
      // flag for cleanup
      this.bDone = true;
    };

    event.addListener(wrappedListener, owner); // register wrapped listener
  }

  @Override
  protected void destroy(){
    if(wrappedListener != null){
      event.removeListener(wrappedListener);
      wrappedListener = null;
    }
  }
}
