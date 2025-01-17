/*
 * Copyright (c) 1997, 2022 Oracle and/or its affiliates. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0, which is available at
 * http://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package com.sun.xml.ws.server.provider;

import com.sun.istack.NotNull;
import com.sun.istack.Nullable;
import com.sun.xml.ws.api.message.Packet;
import com.sun.xml.ws.api.pipe.Fiber;
import com.sun.xml.ws.api.pipe.NextAction;
import com.sun.xml.ws.api.pipe.ThrowableContainerPropertySet;
import com.sun.xml.ws.api.pipe.Tube;
import com.sun.xml.ws.api.server.AsyncProvider;
import com.sun.xml.ws.api.server.AsyncProviderCallback;
import com.sun.xml.ws.api.server.Invoker;
import com.sun.xml.ws.api.server.WSEndpoint;
import com.sun.xml.ws.server.AbstractWebServiceContext;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * This {@link Tube} is used to invoke the {@link AsyncProvider} endpoints.
 *
 * @author Jitendra Kotamraju
 */
public // TODO needed by factory
class AsyncProviderInvokerTube<T> extends ProviderInvokerTube<T> {

    private static final Logger LOGGER = Logger.getLogger(
        com.sun.xml.ws.util.Constants.LoggingDomain + ".server.AsyncProviderInvokerTube");

    public AsyncProviderInvokerTube(Invoker invoker, ProviderArgumentsBuilder<T> argsBuilder) {
        super(invoker, argsBuilder);
    }

   /*
    * This binds the parameter for Provider endpoints and invokes the
    * invoke() method of {@linke Provider} endpoint. The return value from
    * invoke() is used to create a new {@link Message} that traverses
    * through the Pipeline to transport.
    */
    @Override
    public @NotNull NextAction processRequest(@NotNull Packet request) {
        T param = argsBuilder.getParameter(request);
        NoSuspendResumer resumer = new NoSuspendResumer();
        @SuppressWarnings({ "rawtypes", "unchecked" })
		AsyncProviderCallbackImpl callback = new AsyncProviderInvokerTube.AsyncProviderCallbackImpl(request, resumer);
        AsyncWebServiceContext ctxt = new AsyncWebServiceContext(getEndpoint(),request);

        AsyncProviderInvokerTube.LOGGER.fine("Invoking AsyncProvider Endpoint");
        try {
            getInvoker(request).invokeAsyncProvider(request, param, callback, ctxt);
        } catch(Throwable e) {
            LOGGER.log(Level.SEVERE, e.getMessage(), e);
            return doThrow(e);
        }
        
        synchronized(callback) {
        	if (resumer.response != null) {
                // Only used by AsyncProvider<Packet>
                // Implementation may pass Packet containing throwable; use both
        	    ThrowableContainerPropertySet tc = resumer.response.getSatellite(ThrowableContainerPropertySet.class);
        	    Throwable t = (tc != null) ? tc.getThrowable() : null;
        	    
        		return t != null ? doThrow(resumer.response, t) : doReturnWith(resumer.response);
        	}
        
	        // Suspend the Fiber. AsyncProviderCallback will resume the Fiber after
	        // it receives response.
        	callback.resumer = new FiberResumer();
	        return doSuspend();
        }
    }
    
    private interface Resumer {
    	void onResume(Packet response);
    }
    
    /*private*/ public class FiberResumer implements Resumer { // TODO public for DISI
    	private final Fiber fiber;
    	
    	public FiberResumer() {
            this.fiber = Fiber.current();
    	}
    	
    	@Override
        public void onResume(Packet response) {
            // Only used by AsyncProvider<Packet>
            // Implementation may pass Packet containing throwable; use both
    	    ThrowableContainerPropertySet tc = response.getSatellite(ThrowableContainerPropertySet.class);
    	    Throwable t = (tc != null) ? tc.getThrowable() : null;
    		fiber.resume(t, response);
    	}
    }
    
    private class NoSuspendResumer implements Resumer {
    	protected Packet response = null;

		@Override
        public void onResume(Packet response) {
			this.response = response;
		}
    }

    /*private*/ public class AsyncProviderCallbackImpl implements AsyncProviderCallback<T> { // TODO public for DISI
        private final Packet request;
        private Resumer resumer;

        public AsyncProviderCallbackImpl(Packet request, Resumer resumer) {
            this.request = request;
            this.resumer = resumer;
        }

        @Override
        public void send(@Nullable T param) {
            if (param == null) {
                if (request.transportBackChannel != null) {
                    request.transportBackChannel.close();
                }
            }
            Packet packet = argsBuilder.getResponse(request, param, getEndpoint().getPort(), getEndpoint().getBinding());
            synchronized(this) {
            	resumer.onResume(packet);
            }
        }

        @Override
        public void sendError(@NotNull Throwable t) {
            Exception e;
            if (t instanceof Exception) {
                e = (Exception) t;
            } else {
                e = new RuntimeException(t);
            }
            Packet packet = argsBuilder.getResponse(request, e, getEndpoint().getPort(), getEndpoint().getBinding());
            synchronized(this) {
            	resumer.onResume(packet);
            }
        }
    }

    /**
     * The single {@link jakarta.xml.ws.WebServiceContext} instance injected into application.
     */
    /*private static final*/ public class AsyncWebServiceContext extends AbstractWebServiceContext { // TODO public for DISI
        final Packet packet;

        public AsyncWebServiceContext(WSEndpoint endpoint, Packet packet) { // TODO public for DISI
            super(endpoint);
            this.packet = packet;
        }

        @Override
        public @NotNull Packet getRequestPacket() {
            return packet;
        }
    }

    @Override
    public @NotNull NextAction processResponse(@NotNull Packet response) {
        return doReturnWith(response);
    }

    @Override
    public @NotNull NextAction processException(@NotNull Throwable t) {
        return doThrow(t);
    }

}
