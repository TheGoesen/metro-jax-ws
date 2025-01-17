/*
 * Copyright (c) 1997, 2022 Oracle and/or its affiliates. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0, which is available at
 * http://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package com.sun.xml.ws.client;

import com.sun.istack.NotNull;
import com.sun.istack.Nullable;
import com.sun.xml.ws.api.BindingID;
import com.sun.xml.ws.api.EndpointAddress;
import com.sun.xml.ws.api.WSService;
import com.sun.xml.ws.api.policy.PolicyResolverFactory;
import com.sun.xml.ws.api.policy.PolicyResolver;
import com.sun.xml.ws.api.client.WSPortInfo;
import com.sun.xml.ws.api.model.wsdl.WSDLPort;
import com.sun.xml.ws.binding.BindingImpl;
import com.sun.xml.ws.binding.WebServiceFeatureList;
import com.sun.xml.ws.policy.PolicyMap;
import com.sun.xml.ws.policy.jaxws.PolicyUtil;

import javax.xml.namespace.QName;
import jakarta.xml.ws.WebServiceFeature;

/**
 * Information about a port.
 * <br>
 * This object is owned by {@link WSServiceDelegate} to keep track of a port,
 * since a port maybe added dynamically.
 *
 * @author JAXWS Development Team
 */
public class PortInfo implements WSPortInfo {
    private final @NotNull WSServiceDelegate owner;

    public final @NotNull QName portName;
    public final @NotNull EndpointAddress targetEndpoint;
    public final @NotNull BindingID bindingId;

    public final @NotNull PolicyMap policyMap;
    /**
     * If a port is known statically to a WSDL, {@link PortInfo} may
     * have the corresponding WSDL model. This would occur when the
     * service was created with the WSDL location and the port is defined
     * in the WSDL.
     * <br>
     * If this is a {@link SEIPortInfo}, then this is always non-null.
     */
    public final @Nullable WSDLPort portModel;

    public PortInfo(WSServiceDelegate owner, EndpointAddress targetEndpoint, QName name, BindingID bindingId) {
        this.owner = owner;
        this.targetEndpoint = targetEndpoint;
        this.portName = name;
        this.bindingId = bindingId;
        this.portModel = getPortModel(owner, name);
        this.policyMap = createPolicyMap();

    }

    public PortInfo(@NotNull WSServiceDelegate owner, @NotNull WSDLPort port) {
        this.owner = owner;
        this.targetEndpoint = port.getAddress();
        this.portName = port.getName();
        this.bindingId = port.getBinding().getBindingId();
        this.portModel = port;
        this.policyMap = createPolicyMap();
    }

    @Override
    public PolicyMap getPolicyMap() {
        return policyMap;
    }

    public PolicyMap createPolicyMap() {
       PolicyMap map;
       if(portModel != null) {
            map = portModel.getOwner().getParent().getPolicyMap();
       } else {
           map = PolicyResolverFactory.create().resolve(new PolicyResolver.ClientContext(null,owner.getContainer()));
       }
       //still map is null, create a empty map
       if(map == null)
           map = PolicyMap.createPolicyMap(null);
       return map;
    }
    /**
     * Creates {@link BindingImpl} for this {@link PortInfo}.
     *
     * @param webServiceFeatures
     *      User-specified features.
     * @param portInterface
     *      Null if this is for dispatch. Otherwise the interface the proxy is going to implement
     * @return
     *      The initialized BindingImpl
     */
    public BindingImpl createBinding(WebServiceFeature[] webServiceFeatures, Class<?> portInterface) {
        return createBinding(new WebServiceFeatureList(webServiceFeatures), portInterface, null);
    }

    public BindingImpl createBinding(WebServiceFeatureList webServiceFeatures, Class<?> portInterface,
    		BindingImpl existingBinding) {
		if (existingBinding != null) {
			webServiceFeatures.addAll(existingBinding.getFeatures());
		}

        Iterable<WebServiceFeature> configFeatures;
        //TODO incase of Dispatch, provide a way to User for complete control of the message processing by giving
        // ability to turn off the WSDL/Policy based features and its associated tubes.

        //Even in case of Dispatch, merge all features configured via WSDL/Policy or deployment configuration
        if (portModel != null) {
            // could have merged features from this.policyMap, but some features are set in WSDLModel which are not there in PolicyMap
            // for ex: <wsaw:UsingAddressing> wsdl extn., and since the policyMap features are merged into WSDLModel anyway during postFinished(),
            // So, using here WsdlModel for merging is right.

            // merge features from WSDL
            configFeatures = portModel.getFeatures();
        } else {
            configFeatures = PolicyUtil.getPortScopedFeatures(policyMap, owner.getServiceName(),portName);
        }
        webServiceFeatures.mergeFeatures(configFeatures, false);

        // merge features from interceptor
        webServiceFeatures.mergeFeatures(owner.serviceInterceptor.preCreateBinding(this, portInterface, webServiceFeatures), false);

        BindingImpl bindingImpl = BindingImpl.create(bindingId, webServiceFeatures.toArray());
        owner.getHandlerConfigurator().configureHandlers(this,bindingImpl);
        return bindingImpl;
    }

    //This method is used for Dispatch client only
    private WSDLPort getPortModel(WSServiceDelegate owner, QName portName) {

        if (owner.getWsdlService() != null){
            Iterable<? extends WSDLPort> ports = owner.getWsdlService().getPorts();
            for (WSDLPort port : ports){
                if (port.getName().equals(portName))
                    return port;                
            }
        }
        return null;
    }

//
// implementation of API PortInfo interface
//

    @Override
    @Nullable
    public WSDLPort getPort() {
        return portModel;
    }

    @Override
    @NotNull
    public WSService getOwner() {
        return owner;
    }

    @Override
    @NotNull
    public BindingID getBindingId() {
        return bindingId;
    }

    @Override
    @NotNull
    public EndpointAddress getEndpointAddress() {
        return targetEndpoint;
    }

    /**
     * @deprecated
     *      Only meant to be used via {@link jakarta.xml.ws.handler.PortInfo}.
     *      Use {@link WSServiceDelegate#getServiceName()}.
     */
    @Override
    public QName getServiceName() {
        return owner.getServiceName();
    }

    /**
     *      Only meant to be used via {@link jakarta.xml.ws.handler.PortInfo}.
     *      Use {@link #portName}.
     */
    @Override
    public QName getPortName() {
        return portName;
    }

    /**
     * @deprecated
     *      Only meant to be used via {@link jakarta.xml.ws.handler.PortInfo}.
     *      Use {@link #bindingId}.
     */
    @Override
    public String getBindingID() {
        return bindingId.toString();
    }
}

