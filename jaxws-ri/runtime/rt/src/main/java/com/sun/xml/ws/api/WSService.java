/*
 * Copyright (c) 1997, 2022 Oracle and/or its affiliates. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0, which is available at
 * http://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package com.sun.xml.ws.api;

import com.sun.istack.NotNull;
import com.sun.istack.Nullable;
import com.sun.xml.ws.api.addressing.WSEndpointReference;
import com.sun.xml.ws.api.server.Container;
import com.sun.xml.ws.api.server.ContainerResolver;
import com.sun.xml.ws.api.server.WSEndpoint;
import com.sun.xml.ws.client.WSServiceDelegate;

import jakarta.xml.bind.JAXBContext;
import javax.xml.namespace.QName;
import jakarta.xml.ws.Dispatch;
import jakarta.xml.ws.EndpointReference;
import jakarta.xml.ws.Service;
import jakarta.xml.ws.WebServiceException;
import jakarta.xml.ws.WebServiceFeature;
import jakarta.xml.ws.spi.ServiceDelegate;
import java.lang.reflect.Field;
import java.net.URL;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * JAX-WS implementation of {@link ServiceDelegate}.
 *
 * <p>
 * This abstract class is used only to improve the static type safety
 * of the JAX-WS internal API.
 *
 * <p>
 * The class name intentionally doesn't include "Delegate",
 * because the fact that it's a delegate is a detail of
 * the JSR-224 API, and for the layers above us this object
 * nevertheless represents {@link Service}. We want them
 * to think of this as an internal representation of a service.
 *
 * <p>
 * Only JAX-WS internal code may downcast this to {@link WSServiceDelegate}.
 *
 * @author Kohsuke Kawaguchi
 */
public abstract class WSService extends ServiceDelegate implements ComponentRegistry {
	private final Set<Component> components = new CopyOnWriteArraySet<>();
	
	protected WSService() {
    }

    /**
     * Works like {@link #getPort(EndpointReference, Class, WebServiceFeature...)}
     * but takes {@link WSEndpointReference}. 
     */
    public abstract <T> T getPort(WSEndpointReference epr, Class<T> portInterface, WebServiceFeature... features);

    /**
     * Works like {@link #createDispatch(jakarta.xml.ws.EndpointReference, java.lang.Class, jakarta.xml.ws.Service.Mode, jakarta.xml.ws.WebServiceFeature[])}
     * but it takes the port name separately, so that EPR without embedded metadata can be used.
     */
    public abstract <T> Dispatch<T> createDispatch(QName portName, WSEndpointReference wsepr, Class<T> aClass, Service.Mode mode, WebServiceFeature... features);

    /**
     * Works like {@link #createDispatch(jakarta.xml.ws.EndpointReference, jakarta.xml.bind.JAXBContext, jakarta.xml.ws.Service.Mode, jakarta.xml.ws.WebServiceFeature[])}
     * but it takes the port name separately, so that EPR without embedded metadata can be used.
     */
    public abstract Dispatch<Object> createDispatch(QName portName, WSEndpointReference wsepr, JAXBContext jaxbContext, Service.Mode mode, WebServiceFeature... features);

    /**
     * Gets the {@link Container} object.
     *
     * <p>
     * The components inside {@link WSEndpoint} uses this reference
     * to communicate with the hosting environment.
     *
     * @return
     *      always same object. If no "real" {@link Container} instance
     *      is given, {@link Container#NONE} will be returned.
     */
    public abstract @NotNull Container getContainer();

    @Override
    public @Nullable <S> S getSPI(@NotNull Class<S> spiType) {
    	for (Component c : components) {
    		S s = c.getSPI(spiType);
    		if (s != null)
    			return s;
    	}
    	
    	return getContainer().getSPI(spiType);
    }
    
    @Override
    public @NotNull Set<Component> getComponents() {
    	return components;
    }
    
    /**
     * Create a <code>Service</code> instance.
     *
     * The specified WSDL document location and service qualified name MUST
     * uniquely identify a <code>wsdl:service</code> element.
     *
     * @param wsdlDocumentLocation URL for the WSDL document location
     *                             for the service
     * @param serviceName QName for the service
     * @throws WebServiceException If any error in creation of the
     *                    specified service.
     **/
    public static WSService create( URL wsdlDocumentLocation, QName serviceName) {
        return new WSServiceDelegate(wsdlDocumentLocation,serviceName,Service.class);
    }

    /**
     * Create a <code>Service</code> instance.
     *
     * @param serviceName QName for the service
     * @throws WebServiceException If any error in creation of the
     *                    specified service
     */
    public static WSService create(QName serviceName) {
        return create(null,serviceName);
    }

    /**
     * Creates a service with a dummy service name.
     */
    public static WSService create() {
        return create(null,new QName(WSService.class.getName(),"dummy"));
    }

    /**
     * Typed parameter bag used by {@link WSService#create(URL, QName, InitParams)}
     *
     * @since 2.1.3
     */
    public static final class InitParams {
        private Container container;
        /**
         * Sets the {@link Container} object used by the created service.
         * This allows the client to use a specific {@link Container} instance
         * as opposed to the one obtained by {@link ContainerResolver}.
         */
        public void setContainer(Container c) {
            this.container = c;
        }
        public Container getContainer() {
            return container;
        }
    }

    /**
     * To create a {@link Service}, we need to go through the API that doesn't let us
     * pass parameters, so as a hack we use thread local.
     */
    protected static final ThreadLocal<InitParams> INIT_PARAMS = new ThreadLocal<>();

    /**
     * Used as a immutable constant so that we can avoid null check. 
     */
    protected static final InitParams EMPTY_PARAMS = new InitParams();

    /**
     * Creates a {@link Service} instance.
     *
     * <p>
     * This method works really like {@link Service#create(URL, QName)}
     * except it takes one more RI specific parameter.
     *
     * @param wsdlDocumentLocation
     *          {@code URL} for the WSDL document location for the service.
     *          Can be null, in which case WSDL is not loaded.
     * @param serviceName
     *          {@code QName} for the service.
     * @param properties
     *          Additional RI specific initialization parameters. Can be null.
     * @throws WebServiceException
     *          If any error in creation of the specified service.
     **/
    public static Service create( URL wsdlDocumentLocation, QName serviceName, InitParams properties) {
        if(INIT_PARAMS.get()!=null)
            throw new IllegalStateException("someone left non-null InitParams");
        INIT_PARAMS.set(properties);
        try {
            Service svc = Service.create(wsdlDocumentLocation, serviceName);
            if(INIT_PARAMS.get()!=null)
                throw new IllegalStateException("Service "+svc+" didn't recognize InitParams");
            return svc;
        } finally {
            // even in case of an exception still reset INIT_PARAMS
            INIT_PARAMS.set(null);
        }
    }

    /**
     * Obtains the {@link WSService} that's encapsulated inside a {@link Service}.
     *
     * @throws IllegalArgumentException
     *      if the given service object is not from the JAX-WS RI.
     */
    public static WSService unwrap(final Service svc) {
        return AccessController.doPrivileged(new PrivilegedAction<>() {
            @Override
            public WSService run() {
                try {
                    Field f = svc.getClass().getField("delegate");
                    f.setAccessible(true);
                    Object delegate = f.get(svc);
                    if (!(delegate instanceof WSService))
                        throw new IllegalArgumentException();
                    return (WSService) delegate;
                } catch (NoSuchFieldException e) {
                    AssertionError x = new AssertionError("Unexpected service API implementation");
                    x.initCause(e);
                    throw x;
                } catch (IllegalAccessException e) {
                    IllegalAccessError x = new IllegalAccessError(e.getMessage());
                    x.initCause(e);
                    throw x;
                }
            }
        });
    }
}
