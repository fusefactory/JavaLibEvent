# JavaLibEvent

_Java Package that provides the following classes:_
* com.fuse.utils.Event;
* com.fuse.utils.Test;

## Installation

Use as maven/gradle/sbt/leiningen dependency with [JitPack](https://github.com/jitpack/maven-modular)
* https://jitpack.io/#fusefactory/JavaLibEvent

For more info on jitpack see;
* https://github.com/jitpack/maven-simple
* https://jitpack.io/docs/?#building-with-jitpack

## JavaDocs
* https://fusefactory.github.io/JavaLibEvent/site/apidocs/index.html

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
    CustomObject someObject = new CustomerObject();

    // notify listeners
    operationExecutedEvent.trigger(someObject);
}
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
