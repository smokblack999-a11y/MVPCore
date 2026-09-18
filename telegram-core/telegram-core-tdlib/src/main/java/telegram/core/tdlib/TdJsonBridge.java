package telegram.core.tdlib;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

/**
 * Minimal adapter over the official TDLib JSON Java binding.
 * Reflection keeps the public module free from generated TDLib classes.
 */
final class TdJsonBridge {
    private final Method createClientId;
    private final Method send;
    private final Method receive;
    private final Method execute;

    private TdJsonBridge(Method createClientId, Method send, Method receive, Method execute) {
        this.createClientId = createClientId;
        this.send = send;
        this.receive = receive;
        this.execute = execute;
    }

    static TdJsonBridge load() {
        try {
            Class<?> c = Class.forName("org.drinkless.tdlib.JsonClient");
            Method create = find(c, "createClientId");
            Method send = find(c, "send", int.class, String.class);
            Method receive = find(c, "receive", double.class);
            Method execute = find(c, "execute", String.class);
            return new TdJsonBridge(create, send, receive, execute);
        } catch (Throwable e) {
            throw new IllegalStateException(
                    "TDLib JSONJava bridge is unavailable or failed to initialize", e);
        }
    }

    int createClientId() {
        return (Integer) invoke(createClientId);
    }

    void send(int clientId, String request) {
        invoke(send, clientId, request);
    }

    String receive(double timeoutSeconds) {
        Object value = invoke(receive, timeoutSeconds);
        return value == null ? null : value.toString();
    }

    String execute(String request) {
        Object value = invoke(execute, request);
        return value == null ? null : value.toString();
    }

    private static Method find(Class<?> type, String name, Class<?>... parameterTypes) throws NoSuchMethodException {
        Method method = type.getMethod(name, parameterTypes);
        if (!Modifier.isStatic(method.getModifiers())) {
            throw new IllegalStateException(type.getName() + "." + name + " must be static");
        }
        return method;
    }

    private static Object invoke(Method method, Object... args) {
        try {
            return method.invoke(null, args);
        } catch (ReflectiveOperationException e) {
            Throwable cause = e.getCause() == null ? e : e.getCause();
            throw new IllegalStateException("TDLib call failed: " + method.getName(), cause);
        }
    }
}
