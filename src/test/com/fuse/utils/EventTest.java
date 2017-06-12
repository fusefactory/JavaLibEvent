package com.fuse.utils;

import java.util.function.Consumer;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

import com.fuse.utils.Event;

/**
 * Unit test for com.fuse.utils.Event.
 */
public class EventTest extends TestCase
{
    /**
     * Create the test case
     *
     * @param testName name of the test case
     */
    public EventTest( String testName )
    {
        super( testName );
    }

    /**
     * @return the suite of tests being tested
     */
    public static Test suite()
    {
        return new TestSuite( EventTest.class );
    }

    /**
     * Test Logic
     */
    private String result;
    private Event<String> event;
    private Object owner;

    public void testApp()
    {
      {
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
      {
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
    }
}
