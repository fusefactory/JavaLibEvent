package com.fuse.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import org.junit.Test;
import org.junit.Ignore;

public class StateTest {

  @Test public void set_value_push(){
    State<Float> state = new State<>();
    assertEquals(state.isInitialized(), false);
    assertEquals(state.get(), null);
    assertEquals(state.val(), null);

    // initialize
    state.set(3.5f);
    assertEquals(state.isInitialized(), true);
    assertEquals((float)state.get(), 3.5f, 0.00001f);
    assertEquals((float)state.val(), 3.5f, 0.00001f);

    // value pusher
    Event<Float> log = new Event<>();
    log.enableHistory();
    assertEquals(log.getHistory().size(), 0);

    // register (active) value pusher
    state.push((Float value) -> { log.trigger(value); }, this);

    // verify initial value pushed
    assertEquals((float)log.getHistory().get(0), 3.5f, 0.00001f);
    assertEquals(log.getHistory().size(), 1);

    // value change
    state.set(4.0f);
    assertEquals((float)log.getHistory().get(1), 4.0f, 0.00001f);
    assertEquals(log.getHistory().size(), 2);

    // another value change
    state.set(5.0f);
    assertEquals((float)log.getHistory().get(2), 5.0f, 0.00001f);
    assertEquals(log.getHistory().size(), 3);

    // value unchanged
    state.set(5.0f);
    assertEquals(log.getHistory().size(), 3);

    // stop pusher
    state.stopPushes((Object)this);
    state.set(6.0f);
    assertEquals(log.getHistory().size(), 3);
  }
}
