/*
 * Copyright (c) 1997, 2022 Oracle and/or its affiliates. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0, which is available at
 * http://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package com.sun.xml.ws.spi;


import com.sun.xml.ws.api.BindingID;
import com.sun.xml.ws.api.WSService;
import com.sun.xml.ws.api.ServiceSharedFeatureMarker;
import com.sun.xml.ws.api.addressing.AddressingVersion;
import com.sun.xml.ws.api.addressing.WSEndpointReference;
import com.sun.xml.ws.api.model.wsdl.WSDLModel;
import com.sun.xml.ws.api.model.wsdl.WSDLPort;
import com.sun.xml.ws.api.model.wsdl.WSDLService;
import com.sun.xml.ws.api.server.BoundEndpoint;
import com.sun.xml.ws.api.server.Container;
import com.sun.xml.ws.api.server.ContainerResolver;
import com.sun.xml.ws.api.server.Module;
import com.sun.xml.ws.api.server.WSEndpoint;
import com.sun.xml.ws.api.wsdl.parser.WSDLParserExtension;
import com.sun.xml.ws.client.WSServiceDelegate;
import com.sun.xml.ws.developer.MemberSubmissionEndpointReference;
import com.sun.xml.ws.resources.ProviderApiMessages;
import com.sun.xml.ws.transport.http.server.EndpointImpl;
import com.sun.xml.ws.util.ServiceFinder;
import com.sun.xml.ws.util.xml.XmlUtil;
import com.sun.xml.ws.wsdl.parser.RuntimeWSDLParser;

import org.w3c.dom.Element;
import org.xml.sax.EntityResolver;

import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBException;
import jakarta.xml.bind.Unmarshaller;
import javax.xml.namespace.QName;
import javax.xml.transform.Source;
import javax.xml.transform.stream.StreamSource;
import jakarta.xml.ws.Endpoint;
import jakarta.xml.ws.EndpointReference;
import jakarta.xml.ws.Service;
import jakarta.xml.ws.WebServiceException;
import jakarta.xml.ws.WebServiceFeature;
import jakarta.xml.ws.spi.Provider;
import jakarta.xml.ws.spi.ServiceDelegate;
import jakarta.xml.ws.spi.Invoker;
import jakarta.xml.ws.wsaddressing.W3CEndpointReference;

import java.net.URL;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.List;
import java.util.Map;

/**
 * The entry point to the JAX-WS RI from the JAX-WS API.
 *
 * @author WS Development Team
 */
public class ProviderImpl extends Provider {

    private final static ContextClassloaderLocal<JAXBContext> eprjc = new ContextClassloaderLocal<>() {
        @Override
        protected JAXBContext initialValue() throws Exception {
            return getEPRJaxbContext();
        }
    };

    /**
     * Convenient singleton instance.
     */
    public static final ProviderImpl INSTANCE = new ProviderImpl();

    @Override
    public Endpoint createEndpoint(String bindingId, Object implementor) {
        return new EndpointImpl(
            (bindingId != null) ? BindingID.parse(bindingId) : BindingID.parse(implementor.getClass()),
            implementor);
    }

    @Override
    public ServiceDelegate createServiceDelegate( URL wsdlDocumentLocation, QName serviceName, Class serviceClass) {
         return new WSServiceDelegate(wsdlDocumentLocation, serviceName, serviceClass);
    }

    @Override
    public ServiceDelegate createServiceDelegate(URL wsdlDocumentLocation, QName serviceName, Class serviceClass,
                                                 WebServiceFeature ... features) {
        for (WebServiceFeature feature : features) {
            if (!(feature instanceof ServiceSharedFeatureMarker))
            throw new WebServiceException("Doesn't support any Service specific features");
        }
        return new WSServiceDelegate(wsdlDocumentLocation, serviceName, serviceClass, features);
    }

    public ServiceDelegate createServiceDelegate( Source wsdlSource, QName serviceName, Class serviceClass) {
        return new WSServiceDelegate(wsdlSource, serviceName, serviceClass);
   }

    @Override
    public Endpoint createAndPublishEndpoint(String address,
                                             Object implementor) {
        Endpoint endpoint = new EndpointImpl(
            BindingID.parse(implementor.getClass()),
            implementor);
        endpoint.publish(address);
        return endpoint;
    }

    @Override
    public Endpoint createEndpoint(String bindingId, Object implementor, WebServiceFeature... features) {
        return new EndpointImpl(
            (bindingId != null) ? BindingID.parse(bindingId) : BindingID.parse(implementor.getClass()),
            implementor, features);
    }

    @Override
    public Endpoint createAndPublishEndpoint(String address, Object implementor, WebServiceFeature... features) {
        Endpoint endpoint = new EndpointImpl(
            BindingID.parse(implementor.getClass()), implementor, features);
        endpoint.publish(address);
        return endpoint;
    }

    @Override
    public Endpoint createEndpoint(String bindingId, Class implementorClass, Invoker invoker, WebServiceFeature... features) {
        return new EndpointImpl(
            (bindingId != null) ? BindingID.parse(bindingId) : BindingID.parse(implementorClass),
            implementorClass, invoker, features);
    }
    
    @Override
    public EndpointReference readEndpointReference(final Source eprInfoset) {

        try {
            Unmarshaller unmarshaller = eprjc.get().createUnmarshaller();
            return (EndpointReference) unmarshaller.unmarshal(eprInfoset);
        } catch (JAXBException e) {
            throw new WebServiceException("Error creating Marshaller or marshalling.", e);
        }

    }

    @Override
    public <T> T getPort(EndpointReference endpointReference, Class<T> clazz, WebServiceFeature... webServiceFeatures) {
        /*
        final @NotNull MemberSubmissionEndpointReference msepr =
                EndpointReferenceUtil.transform(MemberSubmissionEndpointReference.class, endpointReference);
                WSService service = new WSServiceDelegate(msepr.toWSDLSource(), msepr.serviceName.name, Service.class);
                */
        if(endpointReference == null)
            throw new WebServiceException(ProviderApiMessages.NULL_EPR());
        WSEndpointReference wsepr =  new WSEndpointReference(endpointReference);
        WSEndpointReference.Metadata metadata = wsepr.getMetaData();
        WSService service;
        if(metadata.getWsdlSource() != null)
            service = (WSService) createServiceDelegate(metadata.getWsdlSource(), metadata.getServiceName(), Service.class);
        else
            throw new WebServiceException("WSDL metadata is missing in EPR");
        return service.getPort(wsepr, clazz, webServiceFeatures);
    }

    @Override
    public W3CEndpointReference createW3CEndpointReference(String address, QName serviceName, QName portName, List<Element> metadata, String wsdlDocumentLocation, List<Element> referenceParameters) {
        return createW3CEndpointReference(address, null, serviceName, portName, metadata, wsdlDocumentLocation, referenceParameters, null, null);
    }

    @Override
    public W3CEndpointReference createW3CEndpointReference(String address, QName interfaceName, QName serviceName, QName portName,
                                                           List<Element> metadata, String wsdlDocumentLocation, List<Element> referenceParameters,
                                                           List<Element> elements, Map<QName, String> attributes) {
        Container container = ContainerResolver.getInstance().getContainer();
        if (address == null) {
            if (serviceName == null || portName == null) {
                throw new IllegalStateException(ProviderApiMessages.NULL_ADDRESS_SERVICE_ENDPOINT());
            } else {
                //check if it is run in a Java EE Container and if so, get address using serviceName and portName
                Module module = container.getSPI(Module.class);
                if (module != null) {
                    List<BoundEndpoint> beList = module.getBoundEndpoints();
                    for (BoundEndpoint be : beList) {
                        WSEndpoint wse = be.getEndpoint();
                        if (wse.getServiceName().equals(serviceName) && wse.getPortName().equals(portName)) {
                            try {
                                address = be.getAddress().toString();
                            } catch (WebServiceException e) {
                                // May be the container does n't support this
                                //just ignore the exception
                            }
                            break;
                        }
                    }
                }
                //address is still null? may be its not run in a JavaEE Container
                if (address == null)
                    throw new IllegalStateException(ProviderApiMessages.NULL_ADDRESS());
            }
        }
        if((serviceName==null) && (portName != null)) {
            throw new IllegalStateException(ProviderApiMessages.NULL_SERVICE());
        }
        //Validate Service and Port in WSDL
        String wsdlTargetNamespace = null;
        if (wsdlDocumentLocation != null) {
            try {
                EntityResolver er = XmlUtil.createDefaultCatalogResolver();

                URL wsdlLoc = new URL(wsdlDocumentLocation);
                WSDLModel wsdlDoc = RuntimeWSDLParser.parse(wsdlLoc, new StreamSource(wsdlLoc.toExternalForm()), er,
                        true, container, ServiceFinder.find(WSDLParserExtension.class).toArray());
                if (serviceName != null) {
                    WSDLService wsdlService = wsdlDoc.getService(serviceName);
                    if (wsdlService == null)
                        throw new IllegalStateException(ProviderApiMessages.NOTFOUND_SERVICE_IN_WSDL(
                                serviceName,wsdlDocumentLocation));
                    if (portName != null) {
                        WSDLPort wsdlPort = wsdlService.get(portName);
                        if (wsdlPort == null)
                            throw new IllegalStateException(ProviderApiMessages.NOTFOUND_PORT_IN_WSDL(
                                    portName,serviceName,wsdlDocumentLocation));
                    }
                    wsdlTargetNamespace = serviceName.getNamespaceURI();
                } else {
                    QName firstService = wsdlDoc.getFirstServiceName();
                    wsdlTargetNamespace = firstService.getNamespaceURI();
                }
            } catch (Exception e) {
                throw new IllegalStateException(ProviderApiMessages.ERROR_WSDL(wsdlDocumentLocation),e);
            }
        }
        //wcf3.0/3.5 rejected empty metadata element.
        if (metadata != null && metadata.size() == 0) {
           metadata = null;
        }
        return new WSEndpointReference(
            AddressingVersion.fromSpecClass(W3CEndpointReference.class),
            address, serviceName, portName, interfaceName, metadata, wsdlDocumentLocation, wsdlTargetNamespace,referenceParameters, elements, attributes).toSpec(W3CEndpointReference.class);

    }

    private static JAXBContext getEPRJaxbContext() {
        // EPRs have package and private fields, so we need privilege escalation.
        // this access only fixed, known set of classes, so doing that
        // shouldn't introduce security vulnerability.
        return AccessController.doPrivileged(new PrivilegedAction<>() {
            @Override
            public JAXBContext run() {
                try {
                    return JAXBContext.newInstance(MemberSubmissionEndpointReference.class, W3CEndpointReference.class);
                } catch (JAXBException e) {
                    throw new WebServiceException("Error creating JAXBContext for W3CEndpointReference. ", e);
                }
            }
        });
    }
}
