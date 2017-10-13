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

  @Test public void when(){
    Event<String> history = new Event<>();
    history.enableHistory();

    State<Boolean> state = new State<>(true)
      .when(true, () -> history.trigger("true"))
      .when(false, () -> history.trigger("false"));

    assertEquals(history.getHistory().get(0), "true");
    assertEquals(history.getHistory().size(), 1);
  }

  @Test public void reset(){
    State<Integer> s = new State<Integer>();
    s.newValueEvent.enableHistory();
    s.initializedEvent.enableHistory();

    assertEquals(s.isInitialized(), false);
    assertEquals(s.newValueEvent.getHistory().size(), 0);
    assertEquals(s.initializedEvent.getHistory().size(), 0);

    s.set(23);
    assertEquals(s.isInitialized(), true);
    assertEquals(s.newValueEvent.getHistory().size(), 1);
    assertEquals(s.initializedEvent.getHistory().size(), 1);

    s.set(25);
    assertEquals(s.isInitialized(), true);
    assertEquals(s.newValueEvent.getHistory().size(), 2);
    assertEquals(s.initializedEvent.getHistory().size(), 1);

    s.reset();
    assertEquals(s.val(), null);
    assertEquals(s.isInitialized(), false);
    assertEquals(s.newValueEvent.getHistory().size(), 2);
    assertEquals(s.initializedEvent.getHistory().size(), 1);

    s.set(31);
    assertEquals(s.isInitialized(), true);
    assertEquals(s.newValueEvent.getHistory().size(), 3);
    assertEquals(s.initializedEvent.getHistory().size(), 2);
  }

  @Test public void push_to_other_state(){
    State<Integer> s1, s2;
    s1 = new State<>(1);
    s2 = new State<>(2);
    assertEquals((int)s2.get(), 2);
    s1.push(s2);
    assertEquals((int)s2.get(), 1);
    s1.set(3);
    assertEquals((int)s2.get(), 3);
    s2.set(4);
    assertEquals((int)s2.get(), 4);
    assertEquals((int)s1.get(), 3);
  }
}
