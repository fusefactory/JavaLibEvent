# JavaLibEvent
[![Build Status](https://travis-ci.org/fusefactory/JavaLibEvent.svg?branch=master)](https://travis-ci.org/fusefactory/JavaLibEvent)

_Java Package that provides the following high-level classes for implementing Events that invoke lambda-based listeners:_

* com.fuse.utils.Event
* com.fuse.utils.Test
* com.fuse.utils.State

## Installation

Use as maven/gradle/sbt/leiningen dependency with [JitPack](https://github.com/jitpack/maven-modular)
* https://jitpack.io/#fusefactory/JavaLibEvent

For more info on jitpack see;
* https://github.com/jitpack/maven-simple
* https://jitpack.io/docs/?#building-with-jitpack

## Documentation

* Javadocs: https://fusefactory.github.io/JavaLibEvent/site/apidocs/index.html
* To run unit tests: ``` mvn test ```

## Usage: Event class

#### Register a listener

```java
// Create event instance
Event<CustomObject> operationExecutedEvent = new Event<CustomObject>();

// Add listener
operationExecutedEvent.addListener((CustomObject obj) -> {
    // add custom behaviour here
}, this);
```

* the second parameter ('this') is optional (defaults to null)
* the parameter specifies 'owner' of the listener
* Owners can be used to unregister listeners (see following example)

#### Remove previously registered listeners
```java
// Create event instance
Event<CustomObject> operationExecutedEvent = new Event<CustomObject>();

// remove all listeners owned by ('this')
operationExecutedEvent.removeListeners(this);
```

* the 'owner' can be specified when the listener is registered (see previous example)

#### Notify all registered listeners
```java
void setup(){
    // Create event instance
    Event<CustomObject> operationExecutedEvent = new Event<CustomObject>();
}

void someOperation(){
    // Create payload instance
    CustomObject someObject = new CustomObject();

    // notify listeners
    operationExecutedEvent.trigger(someObject);
}
```

#### Safe-to-register/unregister-listeners-from-within-callback
It is safe to register/unregister listeners to/from an Event from within
a callback registered to that same Event. Note that and listener add/remove
operations will be queued if the Event if being triggered, not actually actuated until the Event has finished invoking all its current listeners.

```java
    someEvent.addListener((CustomObject cobj) -> {
        if(cobj.some_attribute == true){
            // these listeners (including this one being executed right now)
            // won't be removed until after the Event finished its current round
            // of listener invocations
            someEvent.removeListeners(this);
            // likewise, this new listeners won't be added until after the
            // current 'trigger', so it won't be invoked this round
            someEvent.addListener((CustomObject cobj) -> {
                // ...
            }, this)
        }
    }, this);
```

#### Register a listener that gets invoked only once
Using the _addOnceListener_ method, you can register listeners that are only
invoked once. These listeners are automatically removed immediately
after the next trigger invocation.

Note that these listeners are removed just like other 'normal' listeners using the
removeListeners method.

```java
    someEvent.addOnceListener((CustomObject cobj) -> {
        // ...
    }, this);
```

## Usage: Test class

The com.fuse.utils.Test class work exactly like the com.fuse.utils.Event class (except the 'trigger' method is called 'test' and the listeners are expected to return a boolean value. When a single listener returns false, the notifications immediately stop (listener that have not yet been invoked will not be invoked) and the test method returns a boolean value indicating if all listeners returned true. This way listener can be used the determine if a certain operation should be executed/continued or aborted. (See examples below).

#### Register listener
```java
Test<CustomObject> beforeOperationTest = new Test<CustomObject>();

beforeOperationTest.addListener((CustomObject obj) -> boolean {
    return obj.value > 10;
}, this);
```

#### Use Test instance to determine if operation should be executed
```java
void setup(){
    Test<CustomObject> beforeOperationTest = new Test<CustomObject>();
}

void operation(CustomObject obj){
    if(beforeOperationTest.test(obj) == false)
        return;

    // perform operation
}
```

## Usage State classes

The state class is basically a 'smart' variable which triggers events when its value changes.

You can use its public ```newValueEvent``` and ```initializedEvent``` attributes directly (see usage of the Event class in the documentation above), but it's often more convenient to use the higher level ```push method``` because it both executes the lambda for the current value (if any) and for all future values (until it is stopped), or the ```when method``` in case something needs to be executed when the state gets a specific value

#### 'push' method

The push method executes for every (current and future) value of the state. This doesn't only save you some coding, but is designed to make it easier to reason about certain code. By registering a state-listener using the push method you basically create a connection between the state instance and whatever needs to happens next, while hiding all the logic that is needed to establish that connection (registering event listeners and checking if the value is initialized).

```java
    // create an instance; note you have to use objects, primitives like int, string and float (lower-case) are not allowed
    State<Float> levelState = new State<>();

    // register a listener to be executed for every (future) value of the state instance;
    levelState.push((Float value) -> {

        // ... do stuff with the value here ...
        System.out.println("Got value: "+value.toString());

    }, this); // this second param (this) is optional and can be used later to remove the callback


    levelState.set(10.0f); // This will print "Got value: 10.0"
    levelState.set(20.0f); // This will print "Got value: 10.0"

    // register another listener, note that this listener
    // will also be immediately executed with the current value
    levelState.push((Float value) -> { this.setSuperImportantAttributeValue(value * 2.0f); }, this);

    // our super important attribute should now have the value 40.0f

    // this will remove both the above listeners
    levelState.stopPushes(this);
```

#### 'when' method

To register _value specific listeners_ use the ```when``` method. Just like the push method, this can make the code more readable by hiding all the if/then/else control-flow logic.

```java
    // initialize a boolean state instance and initialize with the value 'false'
    State<Boolean> onOffState = new State<>(false);
    // register separate listeners for the true and for the false values
    onOffState.when(false, (Boolean value) -> machine.turnOFF());
    onOffState.when(true, (Boolean value) -> {
        this.sendMachineStartNotification();
        machine.turnON()
    });

    // note that the when method returns the state instance itself, so you can link them
    State<Integer> score = new State<>(0);
    score
        .when(1, (Integer value) -> System.out.println("Your first point!"))
        .when(7, (Integer value) -> System.out.println("Getting lucky"))
        .when(21, (Integer value) -> System.out.println("You just won a pingpong match"));
```
