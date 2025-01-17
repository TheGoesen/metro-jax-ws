/*
 * Copyright (c) 1997, 2022 Oracle and/or its affiliates. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0, which is available at
 * http://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package com.oracle.webservices.api.message;

import com.sun.istack.NotNull;
import com.sun.istack.Nullable;
import java.lang.invoke.MethodHandles;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.security.AccessController;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;


/**
 * A set of "properties" that can be accessed via strongly-typed fields
 * as well as reflexibly through the property name.
 *
 * @author Kohsuke Kawaguchi
 */
@SuppressWarnings("SuspiciousMethodCalls")
public abstract class BasePropertySet implements PropertySet {

    /**
     * Creates a new instance of TypedMap.
     */
    protected BasePropertySet() {
    }

    private Map<String,Object> mapView;

    /**
     * Represents the list of strongly-typed known properties
     * (keyed by property names.)
     *
     * <p>
     * Just giving it an alias to make the use of this class more fool-proof.
     */
    protected static class PropertyMap extends HashMap<String,Accessor> {

        // the entries are often being iterated through so performance can be improved
        // by their caching instead of iterating through the original (immutable) map each time
        transient PropertyMapEntry[] cachedEntries = null;

        PropertyMapEntry[] getPropertyMapEntries() {
            if (cachedEntries == null) {
                cachedEntries = createPropertyMapEntries();
            }
            return cachedEntries;
        }

        private PropertyMapEntry[] createPropertyMapEntries() {
            final PropertyMapEntry[] modelEntries = new PropertyMapEntry[size()];
            int i = 0;
            for (final Entry<String, Accessor> e : entrySet()) {
                modelEntries[i++] = new PropertyMapEntry(e.getKey(), e.getValue());
            }
            return modelEntries;
        }

    }

    /**
     * PropertyMapEntry represents a Map.Entry in the PropertyMap with more efficient access.
     */
    static public class PropertyMapEntry {
        public PropertyMapEntry(String k, Accessor v) {
            key = k; value = v;
        }
        String key;
        Accessor value;
    }
    
    /**
     * Map representing the Fields and Methods annotated with {@link PropertySet.Property}.
     * Model of {@link PropertySet} class.
     *
     * <p>
     * At the end of the derivation chain this method just needs to be implemented
     * as:
     *
     * <pre>
     * private static final PropertyMap model;
     * static {
     *   model = parse(MyDerivedClass.class);
     * }
     * protected PropertyMap getPropertyMap() {
     *   return model;
     * }
     * </pre>
     * or if the implementation is in different Java module.
     * <pre>
     * private static final PropertyMap model;
     * static {
     *   model = parse(MyDerivedClass.class, MethodHandles.lookup());
     * }
     * protected PropertyMap getPropertyMap() {
     *   return model;
     * }
     * </pre>
     * @return the map of strongly-typed known properties keyed by property names
     */
    protected abstract PropertyMap getPropertyMap();

    /**
     * This method parses a class for fields and methods with {@link PropertySet.Property}.
     *
     * @param clazz Class to be parsed
     * @return the map of strongly-typed known properties keyed by property names
     * @see #parse(java.lang.Class, java.lang.invoke.MethodHandles.Lookup)
     */
    protected static PropertyMap parse(final Class<?> clazz) {
        return parse(clazz, MethodHandles.lookup());
    }

    /**
     * This method parses a class for fields and methods with {@link PropertySet.Property}.
     *
     * @param clazz Class to be parsed
     * @param caller the caller lookup object
     * @return the map of strongly-typed known properties keyed by property names
     * @throws NullPointerException if {@code clazz} or {@code caller} is {@code null}
     * @throws SecurityException if denied by the security manager
     * @throws RuntimeException if any of the other access checks specified above fails
     * @since 3.0.1
     */
    protected static PropertyMap parse(final Class<?> clazz, final MethodHandles.Lookup caller) {
        Class<?> cl = Objects.requireNonNull(clazz, "clazz must not be null");
        MethodHandles.Lookup lookup = Objects.requireNonNull(caller, "caller must not be null");
        try {
            return AccessController.doPrivileged((PrivilegedExceptionAction<PropertyMap>) () -> {
                PropertyMap props = new PropertyMap();
                for (Class<?> c = cl; c != Object.class; c = c.getSuperclass()) {
                    MethodHandles.Lookup privateLookup = AccessorFactory.createPrivateLookup(c, lookup);
                    for (Field f : c.getDeclaredFields()) {
                        Property cp = f.getAnnotation(Property.class);
                        if (cp != null) {
                            for (String value : cp.value()) {
                                props.put(value, AccessorFactory.createAccessor(f, value, privateLookup));
                            }
                        }
                    }
                    for (Method m : c.getDeclaredMethods()) {
                        Property cp = m.getAnnotation(Property.class);
                        if (cp != null) {
                            String name = m.getName();
                            assert name.startsWith("get") || name.startsWith("is");

                            String setName = name.startsWith("is")
                                    ? "set" + name.substring(2) // isFoo -> setFoo
                                    : 's' + name.substring(1);  // getFoo -> setFoo
                            Method setter;
                            try {
                                setter = cl.getMethod(setName, m.getReturnType());
                            } catch (NoSuchMethodException e) {
                                setter = null; // no setter
                            }
                            for (String value : cp.value()) {
                                props.put(value, AccessorFactory.createAccessor(m, setter, value, privateLookup));
                            }
                        }
                    }
                }

                return props;
            });
        } catch (PrivilegedActionException ex) {
            Throwable t = ex.getCause();
            // TODO9: use InaccessibleObjectException on JDK 9+ instead
            throw new RuntimeException(t);
        }
    }

    /**
     * Represents a typed property defined on a {@link PropertySet}.
     */
    protected interface Accessor {
        String getName();
        boolean hasValue(PropertySet props);
        Object get(PropertySet props);
        void set(PropertySet props, Object value);
    }

    static final class FieldAccessor implements Accessor {
        /**
         * Field with the annotation.
         */
        private final Field f;

        /**
         * One of the values in {@link Property} annotation on {@link #f}.
         */
        private final String name;

        protected FieldAccessor(Field f, String name) {
            this.f = f;
            f.setAccessible(true);
            this.name = name;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public boolean hasValue(PropertySet props) {
            return get(props)!=null;
        }

        @Override
        public Object get(PropertySet props) {
            try {
                return f.get(props);
            } catch (IllegalAccessException e) {
                throw new AssertionError();
            }
        }

        @Override
        public void set(PropertySet props, Object value) {
            try {
                f.set(props,value);
            } catch (IllegalAccessException e) {
                throw new AssertionError();
            }
        }
    }

    static final class MethodAccessor implements Accessor {
        /**
         * Getter method.
         */
        private final @NotNull Method getter;
        /**
         * Setter method.
         * Some property is read-only.
         */
        private final @Nullable Method setter;

        /**
         * One of the values in {@link Property} annotation on {@link #getter}.
         */
        private final String name;

        protected MethodAccessor(Method getter, Method setter, String value) {
            this.getter = getter;
            this.setter = setter;
            this.name = value;
            getter.setAccessible(true);
            if (setter!=null) {
                setter.setAccessible(true);
            }
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public boolean hasValue(PropertySet props) {
            return get(props)!=null;
        }

        @Override
        public Object get(PropertySet props) {
            try {
                return getter.invoke(props);
            } catch (IllegalAccessException e) {
                throw new AssertionError();
            } catch (InvocationTargetException e) {
                handle(e);
                return 0;   // never reach here
            }
        }

        @Override
        public void set(PropertySet props, Object value) {
            if(setter==null) {
                throw new ReadOnlyPropertyException(getName());
            }
            try {
                setter.invoke(props,value);
            } catch (IllegalAccessException e) {
                throw new AssertionError();
            } catch (InvocationTargetException e) {
                handle(e);
            }
        }

        /**
         * Since we don't expect the getter/setter to throw a checked exception,
         * it should be possible to make the exception propagation transparent.
         * That's what we are trying to do here.
         */
        private Exception handle(InvocationTargetException e) {
            Throwable t = e.getTargetException();
            if (t instanceof Error) {
                throw (Error)t;
            }
            if (t instanceof RuntimeException) {
                throw (RuntimeException)t;
            }
            throw new Error(e);
        }
    }


    /**
     * Class allowing to work with PropertySet object as with a Map; it doesn't only allow to read properties from
     * the map but also to modify the map in a way it is in sync with original strongly typed fields. It also allows
     * (if necessary) to store additional properties those can't be found in strongly typed fields.
     *
     * @see PropertySet#asMap() method
     */
    final class MapView extends HashMap<String, Object> {

        // flag if it should allow store also different properties
        // than the from strongly typed fields
        boolean extensible;

        MapView(boolean extensible) {
        	super(getPropertyMap().getPropertyMapEntries().length);
            this.extensible = extensible;
            initialize();
        }

        public void initialize() {
            // iterate (cached) array instead of map to speed things up ...
            PropertyMapEntry[] entries = getPropertyMap().getPropertyMapEntries();
            for (PropertyMapEntry entry : entries) {
                super.put(entry.key, entry.value);
            }
        }

        @Override
        public Object get(Object key) {
            Object o = super.get(key);
            if (o instanceof Accessor) {
                return ((Accessor) o).get(BasePropertySet.this);
            } else {
                return o;
            }
        }

        @Override
        public Set<Entry<String, Object>> entrySet() {
            Set<Entry<String, Object>> entries = new HashSet<>();
            for (String key : keySet()) {
                entries.add(new SimpleImmutableEntry<>(key, get(key)));
            }
            return entries;
        }

        @Override
        public Object put(String key, Object value) {

            Object o = super.get(key);
            if (o instanceof Accessor) {

                Object oldValue = ((Accessor) o).get(BasePropertySet.this);
                ((Accessor) o).set(BasePropertySet.this, value);
                return oldValue;

            } else {

                if (extensible) {
                    return super.put(key, value);
                } else {
                    throw new IllegalStateException("Unknown property [" + key + "] for PropertySet [" +
                            BasePropertySet.this.getClass().getName() + "]");
                }
            }
        }

        @Override
        public void clear() {
            for (String key : keySet()) {
                remove(key);
            }
        }

        @Override
        public Object remove(Object key) {
            Object o;
            o = super.get(key);
            if (o instanceof Accessor) {
                ((Accessor)o).set(BasePropertySet.this, null);
            }
            return super.remove(key);
        }
    }

    @Override
    public boolean containsKey(Object key) {
        Accessor sp = getPropertyMap().get(key);
        if (sp != null) {
            return sp.get(this) != null;
        }
        return false;
    }

    /**
     * Gets the name of the property.
     *
     * @param key
     *      This field is typed as {@link Object} to follow the {@link Map#get(Object)}
     *      convention, but if anything but {@link String} is passed, this method
     *      just returns null.
     */
    @Override
    public Object get(Object key) {
        Accessor sp = getPropertyMap().get(key);
        if (sp != null) {
            return sp.get(this);
        }
        throw new IllegalArgumentException("Undefined property "+key);
    }

    /**
     * Sets a property.
     *
     * <p>
     * <strong>Implementation Note</strong>
     * <p>
     * This method is slow. Code inside JAX-WS should define strongly-typed
     * fields in this class and access them directly, instead of using this.
     *
     * @throws ReadOnlyPropertyException
     *      if the given key is an alias of a strongly-typed field,
     *      and if the name object given is not assignable to the field.
     *
     * @see Property
     */
    @Override
    public Object put(String key, Object value) {
        Accessor sp = getPropertyMap().get(key);
        if(sp!=null) {
            Object old = sp.get(this);
            sp.set(this,value);
            return old;
        } else {
            throw new IllegalArgumentException("Undefined property "+key);
        }
    }

    /**
     * Checks if this {@link PropertySet} supports a property of the given name.
     */
    @Override
    public boolean supports(Object key) {
        return getPropertyMap().containsKey(key);
    }

    @Override
    public Object remove(Object key) {
        Accessor sp = getPropertyMap().get(key);
        if(sp!=null) {
            Object old = sp.get(this);
            sp.set(this,null);
            return old;
        } else {
            throw new IllegalArgumentException("Undefined property "+key);
        }
    }

    /**
     * Creates a modifiable {@link Map} view of this {@link PropertySet}.
     * <br>
     * Changes done on this {@link Map} or on {@link PropertySet} object work in both directions - values made to
     * {@link Map} are reflected to {@link PropertySet} and changes done using getters/setters on {@link PropertySet}
     * object are automatically reflected in this {@link Map}.
     * <br>
     * If necessary, it also can hold other values (not present on {@link PropertySet}) -
     * see {@link BasePropertySet#mapAllowsAdditionalProperties()}
     *
     * @return always non-null valid instance.
     */
    @Override
    public Map<String, Object> asMap() {
        if (mapView == null) {
            mapView = createView();
        }
        return mapView;
    }
    
    protected Map<String, Object> createView() {
        return new MapView(mapAllowsAdditionalProperties());
    }

    /**
     * Used when constructing the {@link MapView} for this object - it controls if the {@link MapView} servers only to
     * access strongly typed values or allows also different values
     *
     * @return true if {@link Map} should allow also properties not defined as strongly typed fields
     */
    protected boolean mapAllowsAdditionalProperties() {
        return false;
    }

    protected void createEntrySet(Set<Entry<String,Object>> core) {
        for (final Entry<String, Accessor> e : getPropertyMap().entrySet()) {
            core.add(new Entry<>() {
                @Override
                public String getKey() {
                    return e.getKey();
                }

                @Override
                public Object getValue() {
                    return e.getValue().get(BasePropertySet.this);
                }

                @Override
                public Object setValue(Object value) {
                    Accessor acc = e.getValue();
                    Object old = acc.get(BasePropertySet.this);
                    acc.set(BasePropertySet.this, value);
                    return old;
                }
            });
        }
    }
}
