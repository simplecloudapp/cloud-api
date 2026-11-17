package app.simplecloud.api.internal.networkstate.read;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Reads what a plugin can read from a cloud-api object: every public getter, and the getters of the objects
 * they return, down to plain values. Two objects that a plugin cannot tell apart give equal results. A getter
 * that throws gives its exception, and a map keeps its order, since a plugin sees both.
 */
final class PublicGetters {

    private static final String API_PACKAGE = "app.simplecloud.api.";
    private static final String GENERATED_PACKAGE = "app.simplecloud.api.web.";

    private PublicGetters() {
    }

    static Object read(Object value) {
        if (value == null || value instanceof String || value instanceof Number || value instanceof Boolean
                || value instanceof Enum<?>) {
            return value;
        }
        if (value instanceof Map<?, ?> map) {
            return readEntries(map);
        }
        if (value instanceof List<?> list) {
            return readElements(list);
        }
        if (value instanceof Object[] array) {
            return readElements(List.of(array));
        }
        String className = value.getClass().getName();
        boolean apiObject = className.startsWith(API_PACKAGE) && !className.startsWith(GENERATED_PACKAGE);
        if (!apiObject) {
            throw new IllegalArgumentException("A getter returned a " + className + ", which this reader does not know");
        }
        return readGetters(value);
    }

    private static List<Object> readEntries(Map<?, ?> map) {
        List<Object> entries = new ArrayList<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            entries.add(new AbstractMap.SimpleImmutableEntry<>(entry.getKey(), read(entry.getValue())));
        }
        return entries;
    }

    private static List<Object> readElements(List<?> list) {
        List<Object> elements = new ArrayList<>();
        for (Object element : list) {
            elements.add(read(element));
        }
        return elements;
    }

    private static Map<String, Object> readGetters(Object object) {
        Map<String, Object> getters = new TreeMap<>();
        for (Method method : object.getClass().getMethods()) {
            if (isGetter(method)) {
                getters.put(method.getName(), invoke(method, object));
            }
        }
        return getters;
    }

    private static boolean isGetter(Method method) {
        boolean named = method.getName().startsWith("get") || method.getName().startsWith("is");
        boolean instanceMethod = !Modifier.isStatic(method.getModifiers());
        boolean returnsValue = method.getReturnType() != void.class;
        return named && instanceMethod && returnsValue && method.getParameterCount() == 0
                && !method.getName().equals("getClass");
    }

    private static Object invoke(Method method, Object object) {
        try {
            return read(method.invoke(object));
        } catch (InvocationTargetException thrown) {
            Throwable cause = thrown.getCause();
            return "throws " + cause.getClass().getSimpleName() + ": " + cause.getMessage();
        } catch (IllegalAccessException inaccessible) {
            throw new IllegalStateException(inaccessible);
        }
    }
}
