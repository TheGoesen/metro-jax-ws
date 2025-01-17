/*
 * Copyright (c) 1997, 2022 Oracle and/or its affiliates. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0, which is available at
 * http://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package com.sun.xml.ws.util;

import com.sun.xml.ws.api.WSBinding;
import com.sun.xml.ws.api.databinding.MetadataReader;
import com.sun.xml.ws.api.server.AsyncProvider;
import com.sun.xml.ws.api.streaming.XMLStreamReaderFactory;
import com.sun.xml.ws.handler.HandlerChainsModel;
import com.sun.xml.ws.model.ReflectAnnotationReader;
import com.sun.xml.ws.server.EndpointFactory;
import com.sun.xml.ws.streaming.XMLStreamReaderUtil;
import com.sun.istack.NotNull;

import jakarta.jws.HandlerChain;
import jakarta.jws.WebService;
import jakarta.jws.soap.SOAPMessageHandlers;
import javax.xml.namespace.QName;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import jakarta.xml.ws.Provider;
import jakarta.xml.ws.Service;
import java.io.IOException;
import java.io.InputStream;
import java.util.logging.Logger;

/**
 * <p>Used by client and server side to create handler information
 * from annotated class. The public methods all return a
 * HandlerChainInfo that contains the handlers and role information
 * needed at runtime.
 *
 * <p>All of the handler chain descriptors follow the same schema,
 * whether they are wsdl customizations, handler files specified
 * by an annotation, or are included in the sun-jaxws.xml file.
 * So this class is used for all handler xml information. The
 * two public entry points are
 * {@link HandlerAnnotationProcessor#buildHandlerInfo}, called
 * when you have an annotated class that points to a file.
 *
 * <p>The methods in the class are static so that it may called
 * from the runtime statically.
 *
 * @see com.sun.xml.ws.util.HandlerAnnotationInfo
 *
 * @author JAX-WS Development Team
 */
public class HandlerAnnotationProcessor {

    private static final Logger logger = Logger.getLogger(
        com.sun.xml.ws.util.Constants.LoggingDomain + ".util");

    /**
     * <p>This method is called by
     * {@link EndpointFactory} when
     * they have an annotated class.
     *
     * <p>If there is no handler chain annotation on the class,
     * this method will return null. Otherwise it will load the
     * class and call the parseHandlerFile method to read the
     * information.
     *
     * @return A HandlerAnnotationInfo object that stores the
     * handlers and roles. Will return null if the class passed
     * in has no handler chain annotation.
     */
    public static HandlerAnnotationInfo buildHandlerInfo(@NotNull
        Class<?> clazz, QName serviceName, QName portName, WSBinding binding) {

        MetadataReader metadataReader = EndpointFactory.getExternalMetadatReader(clazz, binding);
        if (metadataReader == null) {
            metadataReader = new ReflectAnnotationReader();
        }

//        clazz = checkClass(clazz);
        HandlerChain handlerChain = metadataReader.getAnnotation(HandlerChain.class, clazz);
        if (handlerChain == null) {
            clazz = getSEI(clazz, metadataReader);
            if (clazz != null)
            handlerChain = metadataReader.getAnnotation(HandlerChain.class, clazz);
            if (handlerChain == null)
                return null;
        }

        if (clazz.getAnnotation(SOAPMessageHandlers.class) != null) {
            throw new UtilException(
                "util.handler.cannot.combine.soapmessagehandlers");
        }
        InputStream iStream = getFileAsStream(clazz, handlerChain);
        XMLStreamReader reader =
            XMLStreamReaderFactory.create(null,iStream, true);
        XMLStreamReaderUtil.nextElementContent(reader);
        HandlerAnnotationInfo handlerAnnInfo = HandlerChainsModel.parseHandlerFile(reader, clazz.getClassLoader(),
            serviceName, portName, binding);
        try {
            reader.close();
            iStream.close();
        } catch (XMLStreamException | IOException e) {
            e.printStackTrace();
            throw new UtilException(e.getMessage());
        }
        return handlerAnnInfo;
    }

    public static HandlerChainsModel buildHandlerChainsModel(final Class<?> clazz) {
        if(clazz == null) {
            return null;
        }
        HandlerChain handlerChain =
            clazz.getAnnotation(HandlerChain.class);
        if(handlerChain == null)
            return null;
        InputStream iStream = getFileAsStream(clazz, handlerChain);
        XMLStreamReader reader =
            XMLStreamReaderFactory.create(null,iStream, true);
        XMLStreamReaderUtil.nextElementContent(reader);
        HandlerChainsModel handlerChainsModel = HandlerChainsModel.parseHandlerConfigFile(clazz, reader);
        try {
            reader.close();
            iStream.close();
        } catch (XMLStreamException | IOException e) {
            e.printStackTrace();
            throw new UtilException(e.getMessage());
        }
        return handlerChainsModel;
    }

    static Class getClass(String className) {
        try {
            return Thread.currentThread().getContextClassLoader().loadClass(
                className);
        } catch (ClassNotFoundException e) {
            throw new UtilException("util.handler.class.not.found",
                className);
        }
    }

    static Class getSEI(Class<?> clazz, MetadataReader metadataReader) {
        if (metadataReader == null) {
            metadataReader = new ReflectAnnotationReader();
        }

        if (Provider.class.isAssignableFrom(clazz) || AsyncProvider.class.isAssignableFrom(clazz)) {
            //No SEI for Provider Implementation
            return null;
        }
        if (Service.class.isAssignableFrom(clazz)) {
            //No SEI for Service class
            return null;
        }

        WebService webService = metadataReader.getAnnotation(WebService.class, clazz);
        if (webService == null) {
            throw new UtilException("util.handler.no.webservice.annotation", clazz.getCanonicalName());
        }

        String ei = webService.endpointInterface();
        if (ei.length() > 0) {
            clazz = getClass(webService.endpointInterface());
            WebService ws = metadataReader.getAnnotation(WebService.class, clazz);
            if (ws == null) {
                throw new UtilException("util.handler.endpoint.interface.no.webservice",
                    webService.endpointInterface());
            }
            return clazz;
        }
        return null;
    }

   static InputStream getFileAsStream(Class clazz, HandlerChain chain) {
        return MrJarUtil.getResourceAsStream(clazz, chain.file());
    }
}
