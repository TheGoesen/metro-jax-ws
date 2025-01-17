/*
 * Copyright (c) 1997, 2022 Oracle and/or its affiliates. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0, which is available at
 * http://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package com.sun.xml.ws.api.client;

import com.sun.istack.NotNull;
import com.sun.istack.Nullable;
import com.sun.xml.ws.api.WSService;
import com.sun.xml.ws.util.ServiceFinder;

import jakarta.xml.ws.Service;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Creates {@link ServiceInterceptor}.
 *
 * <p>
 * Code that wishes to inject {@link ServiceInterceptor} into {@link WSService}
 * must implement this class. There are two ways to have the JAX-WS RI
 * recognize your {@link ServiceInterceptor}s.
 *
 * <h2>Use {@link ServiceFinder}</h2>
 * <p>
 * {@link ServiceInterceptorFactory}s discovered via {@link ServiceFinder}
 * will be incorporated to all {@link WSService} instances.
 *
 * <h2>Register per-thread</h2>
 *
 *
 * @author Kohsuke Kawaguchi
 * @see ServiceInterceptor
 */
public abstract class ServiceInterceptorFactory {
    public abstract ServiceInterceptor create(@NotNull WSService service);

    /**
     * Loads all {@link ServiceInterceptor}s and return aggregated one.
     */
    public static @NotNull ServiceInterceptor load(@NotNull WSService service, @Nullable ClassLoader cl) {
        List<ServiceInterceptor> l = new ArrayList<>();

        // first service look-up
        for( ServiceInterceptorFactory f : ServiceFinder.find(ServiceInterceptorFactory.class))
            l.add(f.create(service));

        // then thread-local
        for( ServiceInterceptorFactory f : threadLocalFactories.get())
            l.add(f.create(service));

        return ServiceInterceptor.aggregate(l.toArray(new ServiceInterceptor[0]));
    }

    private static ThreadLocal<Set<ServiceInterceptorFactory>> threadLocalFactories = new ThreadLocal<>() {
        @Override
        protected Set<ServiceInterceptorFactory> initialValue() {
            return new HashSet<>();
        }
    };

    /**
     * Registers {@link ServiceInterceptorFactory} for this thread.
     *
     * <p>
     * Once registered, {@link ServiceInterceptorFactory}s are consulted for every
     * {@link Service} created in this thread, until it gets unregistered.
     */
    public static boolean registerForThread(ServiceInterceptorFactory factory) {
        return threadLocalFactories.get().add(factory);
    }

    /**
     * Removes previously registered {@link ServiceInterceptorFactory} for this thread.
     */
    public static boolean unregisterForThread(ServiceInterceptorFactory factory) {
        return threadLocalFactories.get().remove(factory);
    }
}
