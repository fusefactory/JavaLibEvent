# JavaLibEvent
* com.fuse.utils.Event;
* com.fuse.utils.Test;

Java package for use with [JitPack](https://github.com/jitpack/maven-modular)

For more info on jitpack see;
* https://github.com/jitpack/maven-simple
* https://jitpack.io/#jitpack/maven-simple
* https://jitpack.io/docs/?#building-with-jitpack

## Usage: Event class

#### Register a listener
```java
// Create event instance
Event<CustomObject> operationExecutedEvent = new Event<CustomObject>();
// Add listener
operationExecutedEvent.addListener((CustomObject obj) -> {

}, this);

// the second parameter ('this') is optional (defaults to null)
// the parameter specifies 'owner' of the listener
// Owners can be used to unregister listeners (see following example)
```

#### Unregister listeners
```java
// Create event instance
Event<CustomObject> operationExecutedEvent = new Event<CustomObject>();
// remove all listener owned by ('this')
operationExecutedEvent.addListeners(this);
// the 'owner' can be specified when the listener is registered (see previous example)
```

#### Notify listeners
```java
void setup(){
    Event<CustomObject> operationExecutedEvent = new Event<CustomObject>();
}

void someOperation(){
    CustomObject someObject = new CustomerObject();
    // ...

    // notify listeners that this operation was executed with this object
    operationExecutedEvent.trigger(someObject);
    // ...
}
```

Usage: Test class

The test class work exactly like the Event class (except the 'trigger' method is called 'test' and the listeners are expected to return a boolean value. When a listener returns false, the notifications immediately stop (listener that have not yet been invoked will not be invoked) and the test method returns a boolean value indicating if all listeners returned true. This way listener can be used the determine if a certain operation should be executed/continued or aborted. (See examples below).

#### Register listener
```java
Test<CustomObject> beforeOperationTest = new Test<CustomObject>();

beforeOperationTest.addListener((CustomOjbect obj) -> boolean {
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

    // performa operation
}
```
