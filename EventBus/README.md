# Ytka's EventBus

An advanced(or not, idk) way to supply events to your listeners.


# Quick Start

Enable it in your build.gradle:
```
dependencies {
    // ... Other dependencies ...
    implementation project(":eventbus")
    implementation project(":eventbus_api")
    annotationProcessor project(":eventbus_processor")
}
```

Then use code like below to register implementations of eventbus
```
// Init with context
EventBus.registerImpl(MyApplication.INSTANCE);

// Or use application id instead of Context
EventBus.registerImpl(BuildConfig.APPLICATION_ID);

// Or init with library's package name
EventBus.registerImpl(BuildConfig.LIBRARY_PACKAGE_NAME);
```

To create new event bus(You can use more than one event bus) use
```
EventBus myBus = EventBus.new("some_bus");
```

Declare your events(Optional, with this you can declare if event can fire parent events) via
```
@Event(canFireParent = true/false)
public class MyEvent {
    public MyEvent(Object... objs) { ... }
}
```

Then declare your method listeners via
```
@EventHandler
public void onMyEvent(MyEvent e) { ... }
```

Register all your listeners via ```myBus.registerListener(...);```

Now you are ready to fire events with ```myBus.fireEvent(new MyEvent(...));```!

# Proguard Support
To ensure that your app is compatible with Proguard add ```-keep class ru.ytkab0bp.eventbus.impl.** { *; }``` to your proguard-rules.pro

# Limitations
* You can't use it within private classes or methods
* You can't use it within Anonymous classes