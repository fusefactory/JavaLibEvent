package com.fuse.utils;

import java.util.List;
import java.util.ArrayList;
import java.util.function.Consumer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class EventTest {

  private String result;
  private Event<String> event;
  private Object owner;

  @Test public void addListener_addOnceListener_trigger_removeListeners(){
    event = new Event<String>();
    result = "";

    // trigger without listener(s)
    event.trigger("trigger1");

    // add listener
    Consumer<String> consumer = (String value) -> {
    	result = result + value;
    };

    event.addListener(consumer);

    // trigger listener
    event.trigger("trigger2");
    assertEquals(result, "trigger2");

    // remove listener
    event.removeListener(consumer);

    // trigger without listener(s)
    event.trigger("trigger3");
    assertEquals(result, "trigger2");

    // add new listener
    event.addListener((String val) -> {
    	result += " -> " + val;
    }, this);

    // trigger with new listener
    event.trigger("trigger4");
    assertEquals(result, "trigger2 -> trigger4");

    // remove new listener by owner
    event.removeListeners(this);

    // trigger without listener(s)
    event.trigger("trigger5");
    assertEquals(result, "trigger2 -> trigger4");

    // add "once listener" (listener will auto-remove after first invocation)
    event.addOnceListener((String value) -> {
    	result += " # " + value;
    });

    // trigger twice, only first one the listener is called
    event.trigger("trigger6");
    event.trigger("trigger7");
    assertEquals(result, "trigger2 -> trigger4 # trigger6");

    Object owner = this;
    // register listener that tries to modify to event while it's triggering
    event.addListener((String val) -> {
    	event.addListener((String v) -> {}, owner);
    	result += " _ " + val + " (after add: "+ Integer.toString(event.size());
    	event.removeListeners(owner);
    	result += ", after remove: "+ Integer.toString(event.size()) + ")";
    }, this);

    assertEquals(event.size(), 1);
    event.trigger("trigger8");
    assertEquals(event.size(), 0);
    assertEquals(result, "trigger2 -> trigger4 # trigger6 _ trigger8 (after add: 1, after remove: 1)");
  }

  @Test public void forward_stopForward(){
      Event<String> e1 = new Event<String>();
      Event<String> e2 = new Event<String>();

      result = "";

      e2.addListener((String val) -> {
        result += val;
      });

      e1.trigger("trigger1");
      assertEquals(result, "");

      e2.forward(e1);
      assertEquals(result, "");

      e1.trigger("trigger2");
      e1.trigger("trigger3");
      assertEquals(result, "trigger2trigger3");

      e2.stopForward(e1);
      e1.trigger("trigger4");
      assertEquals(result, "trigger2trigger3");
  }

  @Test public void hasOwner(){
      Event<Float> evt = new Event<>();
      assertEquals(evt.hasOwner(this), false);
      evt.addListener((Float val) -> {}, this);
      assertEquals(evt.hasOwner(this), true);
  }

  @Test public void hasListener(){
    Consumer<Float> listner = (Float val) -> {};
    Event<Float> evt = new Event<>();
    assertEquals(evt.hasListener(listner), false);
    evt.addListener(listner);
    assertEquals(evt.hasListener(listner), true);
  }

	@Test public void history(){
		List<Integer> numbers = new ArrayList<>();

		// create event
		Event<Integer> e = new Event<>();
		assertEquals(e.isHistoryEnabled(), false);
		assertEquals(e.getHistory(), null);
		// trigger event; by default it doesn't record a history
		e.trigger(6);
		assertEquals(e.getHistory(), null);

		// enable history
		e.enableHistory();
		assertEquals(e.isHistoryEnabled(), true);
		assertEquals((int)e.getHistory().size(), 0);

		// trigger twice, verify both values get recorded into the history of the event
		e.trigger(7);
		assertEquals((int)e.getHistory().get(0), 7);
		assertEquals((int)e.getHistory().size(), 1);
		e.trigger(99);
		assertEquals((int)e.getHistory().get(1), 99);
		assertEquals((int)e.getHistory().size(), 2);

		// disable history, verify cleanup
		e.enableHistory(false);
		assertEquals(e.isHistoryEnabled(), false);
		assertEquals(e.getHistory(), null);
	}

	@Test public void withAllValues(){
		List<Integer> numbers = new ArrayList<>();

		// create event
		Event<Integer> e = new Event<>();
		assertEquals(e.isHistoryEnabled(), false);

		// register logic to be ran for each triggered value, this will also enable history recording
		e.withAllValues((Integer number) -> numbers.add(number));
		assertEquals(e.isHistoryEnabled(), true);

		e.trigger(101);
		assertEquals((int)numbers.get(0), 101);
		assertEquals(numbers.size(), 1);

		// register another one of those, verify it ran with the value in history
		e.withAllValues((Integer number) -> numbers.add(number*2));
		assertEquals((int)numbers.get(1), 202);
		assertEquals(numbers.size(), 2);
	}

	@Test public void whenTriggered(){
		Event<String> evt = new Event<>();
		List<String> strings = new ArrayList<>();

		evt.whenTriggered(() -> strings.add("callback without arguments!"), this);
		assertEquals(strings.size(), 0);
		evt.trigger("with some argument");
		assertEquals(strings.size(), 1);
		assertEquals(strings.get(0), "callback without arguments!");
		evt.trigger("with some other argument");
		assertEquals(strings.size(), 2);
		assertEquals(strings.get(1), "callback without arguments!");
		evt.stopWhenTriggeredCallbacks(this);
		evt.trigger("with some other argument");
		assertEquals(strings.size(), 2);
	}

	@Test public void destroy(){
		Event<String> forwardSource = new Event<>();
		Event<String> event = new Event<>();
		assertEquals(event.size(), 0);
		event.addListener((String val) -> {});
		event.whenTriggered(() -> {});
		event.forward(forwardSource);
		assertEquals(event.size(), 1);
		assertEquals(forwardSource.size(), 1);
		event.destroy();
		assertEquals(event.size(), 0);
		assertEquals(forwardSource.size(), 0);
	}

	@Test public void destroy_while_iterating(){
		Event<String> forwardSource = new Event<>();
		Event<String> event = new Event<>();
		assertEquals(event.size(), 0);
		event.addListener((String val) -> {});
		event.forward(forwardSource);
		assertEquals(event.size(), 1);
		assertEquals(forwardSource.size(), 1);

		event.whenTriggered(() -> {
			event.destroy();
		});

		event.trigger("foo");

		assertEquals(event.size(), 0);
		assertEquals(forwardSource.size(), 0);
	}

	// @Test public void benchmark(){
	// 	Event<String> root = new Event<>();
	// 	Event<String> nest1 = new Event<>();
	// 	Event<String> nest2 = new Event<>();
	//
	// 	nest2.addListener((String val) -> {});
	//
	// 	nest1.addListener((String val) -> {
	// 		for(int i=0; i<100; i++)
	// 			nest2.trigger("foo");
	// 	});
	//
	// 	root.addListener((String val) -> {
	// 		for(int i=0; i<100; i++)
	// 			nest1.trigger("bar");
	// 	});
	//
	// 	long t1 = System.currentTimeMillis();
	//
	// 	for(int i=0; i<100; i++)
	// 		root.trigger("foobar");
	//
	// 	long t2 = System.currentTimeMillis();
	//
	// 	assertTrue((t2-t1) < 50); // averages around 20 on macbook
	// 	assertTrue((t2-t1) > 10);
	// }
}
