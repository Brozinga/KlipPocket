package ru.ytkab0bp.eventbus;

import android.content.Context;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.RestrictTo;

import java.util.ArrayList;
import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.List;

public class EventBus {
    private final static String TAG = "event_bus";
    private final static Handler MAIN_HANDLER = new Handler(Looper.getMainLooper());

    private static List<EventBusListenerImpl> mImpls = new ArrayList<>();

    private HandlerThread handlerThread;

    protected List<Object> handlers = new ArrayList<>();
    protected String mKey;
    protected Handler handler;

    private EventBus(String key) {
        mKey = key;
    }

    public static EventBus newBus(String key) {
        EventBus b = new EventBus(key);
        b.init();
        return b;
    }

    /**
     * Initializes components of the eventbus
     */
    private void init() {
        handlerThread = new HandlerThread("eventbus_" + mKey, HandlerThread.NORM_PRIORITY);
        handlerThread.start();
        handler = new Handler(handlerThread.getLooper());
    }

    public void removeCallbacksFromThread(Runnable r) {
        handler.removeCallbacks(r);
    }

    public void postOnThreadDelayed(Runnable r, long d) {
        handler.postDelayed(r, d);
    }

    public void postOnThread(Runnable r) {
        handler.post(r);
    }

    /**
     * @param clz Event to check
     * @return If we can fire parent event
     */
    public static boolean canFire(Class<?> clz, Object event) {
        boolean canFireParent = false;
        for (EventBusListenerImpl i : mImpls) {
            if (i.canFireParent(clz)) {
                canFireParent = true;
                break;
            }
        }
        return canFireParent ? clz.isInstance(event) : event.getClass().equals(clz);
    }

    public static void registerImpl(Context ctx) {
        registerImpl(ctx.getPackageName());
    }

    /**
     * Registers an implementation
     * @param implId Implementation id, should be BuildConfig.APPLICATION_ID or BuildConfig.LIBRARY_PACKAGE_NAME
     */
    public static void registerImpl(String implId) {
        try {
            EventBusListenerImpl impl = (EventBusListenerImpl) Class.forName("ru.ytkab0bp.eventbus.impl.ru_Z9_ytkab0bp_Z9_beamklipper").newInstance();
            mImpls.add(impl);
        } catch (ClassNotFoundException | IllegalAccessException | InstantiationException e) {
            throw new RuntimeException("Can't load implementation", e);
        }
    }

    /**
     * Registers new eventbus listener
     * @param listener Eventbus listener
     */
    public void registerListener(Object listener) {
        if (mImpls.isEmpty())
            throw new IllegalStateException("No implementations registered, please use registerImpl(...)!");

        handlers.add(listener);
    }

    /**
     * Unregisters eventbus listener
     * @param listener Eventbus listener
     */
    public void unregisterListener(Object listener) {
        handlers.remove(listener);
    }

    /**
     * Fires event to the listeners
     * @param event Event to fire
     */
    public void fireEvent(Object event) {
        if (Looper.myLooper() != handler.getLooper())
            handler.post(()-> fireEvent0(event, false, 0));
        else fireEvent0(event, false, 0);
    }

    /**
     * Stops this event bus
     */
    public void release() {
        handlerThread.quitSafely();
        handlerThread = null;
        handler = null;
    }

    private void fireEvent0(Object event, boolean useClone, int startIndex) {
        if (event == null)
            throw new NullPointerException("Event should not be null!");

        Iterator<Object> it = (useClone ? new ArrayList<>(handlers) : handlers).iterator();
        int index = startIndex;
        while (it.hasNext()) {
            Object obj;
            try {
                obj = it.next();
            } catch (ConcurrentModificationException e) {
                fireEvent0(event, true, index);
                return;
            }

            try {
                for (EventBusListenerImpl val : mImpls) val.onEvent(obj, event);
            } catch (Exception e) {
                Log.e(TAG, "Exception occurred while firing event", e);
            }

            index++;
        }
    }

    /**
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public static void postOnUiThread(Runnable r) {
        MAIN_HANDLER.post(r);
    }
}
