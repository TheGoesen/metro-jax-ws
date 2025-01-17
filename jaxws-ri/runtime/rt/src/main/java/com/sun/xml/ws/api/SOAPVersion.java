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

import org.glassfish.jaxb.core.util.Which;
import com.sun.xml.ws.api.message.saaj.SAAJFactory;
import com.sun.xml.ws.encoding.soap.SOAP12Constants;

import javax.xml.namespace.QName;
import jakarta.xml.soap.MessageFactory;
import jakarta.xml.soap.SOAPConstants;
import jakarta.xml.soap.SOAPException;
import jakarta.xml.soap.SOAPFactory;
import jakarta.xml.ws.soap.SOAPBinding;

import com.oracle.webservices.api.EnvelopeStyle;
import com.oracle.webservices.api.EnvelopeStyleFeature;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Version of SOAP (1.1 and 1.2).
 *
 * <p>
 * This class defines various constants for SOAP 1.1 and SOAP 1.2,
 * and also defines convenience methods to simplify the processing
 * of multiple SOAP versions.
 *
 * <p>
 * This constant alows you to do:
 *
 * <pre>
 * SOAPVersion version = ...;
 * version.someOp(...);
 * </pre>
 *
 * As opposed to:
 *
 * <pre>
 * if(binding is SOAP11) {
 *   doSomeOp11(...);
 * } else {
 *   doSomeOp12(...);
 * }
 * </pre>
 *
 * @author Kohsuke Kawaguchi
 */
public enum SOAPVersion {
    SOAP_11(SOAPBinding.SOAP11HTTP_BINDING,
            com.sun.xml.ws.encoding.soap.SOAPConstants.URI_ENVELOPE,
            "text/xml",
            SOAPConstants.URI_SOAP_ACTOR_NEXT, "actor",
            jakarta.xml.soap.SOAPConstants.SOAP_1_1_PROTOCOL,
            new QName(com.sun.xml.ws.encoding.soap.SOAPConstants.URI_ENVELOPE, "MustUnderstand"),
            "Client",
            "Server",
            Collections.singleton(SOAPConstants.URI_SOAP_ACTOR_NEXT)),

    SOAP_12(SOAPBinding.SOAP12HTTP_BINDING,
            SOAP12Constants.URI_ENVELOPE,
            "application/soap+xml",
            SOAPConstants.URI_SOAP_1_2_ROLE_ULTIMATE_RECEIVER, "role",
            jakarta.xml.soap.SOAPConstants.SOAP_1_2_PROTOCOL,
            new QName(com.sun.xml.ws.encoding.soap.SOAP12Constants.URI_ENVELOPE, "MustUnderstand"),
            "Sender",
            "Receiver",
            new HashSet<>(Arrays.asList(SOAPConstants.URI_SOAP_1_2_ROLE_NEXT, SOAPConstants.URI_SOAP_1_2_ROLE_ULTIMATE_RECEIVER)));

    /**
     * Binding ID for SOAP/HTTP binding of this SOAP version.
     *
     * <p>
     * Either {@link SOAPBinding#SOAP11HTTP_BINDING} or
     *  {@link SOAPBinding#SOAP12HTTP_BINDING}
     */
    public final String httpBindingId;

    /**
     * SOAP envelope namespace URI.
     */
    public final String nsUri;

    /**
     * Content-type. Either "text/xml" or "application/soap+xml".
     */
    public final String contentType;

    /**
     * SOAP MustUnderstand FaultCode for this SOAP version
     */
    public final QName faultCodeMustUnderstand;

    private final String saajFactoryString;
    
    /**
     * If the actor/role attribute is absent, this SOAP version assumes this value.
     */
    public final String implicitRole;

    /**
     * Singleton set that contains {@link #implicitRole}.
     */
    public final Set<String> implicitRoleSet;

    /**
     * This represents the roles required to be assumed by SOAP binding implementation.
     */
    public final Set<String> requiredRoles;

    /**
     * "role" (SOAP 1.2) or "actor" (SOAP 1.1)
     */
    public final String roleAttributeName;

    /**
     * "{nsUri}Client" or "{nsUri}Sender"
     */
    public final QName faultCodeClient;

    /**
     * "{nsUri}Server" or "{nsUri}Receiver"
     */
    public final QName faultCodeServer;

    private SOAPVersion(String httpBindingId, String nsUri, String contentType, String implicitRole, String roleAttributeName,
                        String saajFactoryString, QName faultCodeMustUnderstand, String faultCodeClientLocalName,
                        String faultCodeServerLocalName,Set<String> requiredRoles) {
        this.httpBindingId = httpBindingId;
        this.nsUri = nsUri;
        this.contentType = contentType;
        this.implicitRole = implicitRole;
        this.implicitRoleSet = Collections.singleton(implicitRole);
        this.roleAttributeName = roleAttributeName;
        this.saajFactoryString = saajFactoryString;
        this.faultCodeMustUnderstand = faultCodeMustUnderstand;
        this.requiredRoles = requiredRoles;
        this.faultCodeClient = new QName(nsUri,faultCodeClientLocalName);
        this.faultCodeServer = new QName(nsUri,faultCodeServerLocalName);
    }

    public SOAPFactory getSOAPFactory() {
    	try {
	    	return SAAJFactory.getSOAPFactory(saajFactoryString);
        } catch (SOAPException e) {
            throw new Error(e);
        } catch (NoSuchMethodError e) {
            // SAAJ 1.3 is not in the classpath
            LinkageError x = new LinkageError("You are loading old SAAJ from "+ Which.which(MessageFactory.class));
            x.initCause(e);
            throw x;
        }
    }

    public MessageFactory getMessageFactory() {
    	try {
	    	return SAAJFactory.getMessageFactory(saajFactoryString); 
        } catch (SOAPException e) {
            throw new Error(e);
        } catch (NoSuchMethodError e) {
            // SAAJ 1.3 is not in the classpath
            LinkageError x = new LinkageError("You are loading old SAAJ from "+ Which.which(MessageFactory.class));
            x.initCause(e);
            throw x;
        }
    }

    public String toString() {
        return httpBindingId;
    }

    /**
     * Returns {@link SOAPVersion} whose {@link #httpBindingId} equals to
     * the given string.
     *
     * This method does not perform input string validation.
     *
     * @param binding
     *      for historical reason, we treat null as {@link #SOAP_11},
     *      but you really shouldn't be passing null.
     * @return always non-null.
     */
    public static SOAPVersion fromHttpBinding(String binding) {
        if(binding==null)
            return SOAP_11;

        if(binding.equals(SOAP_12.httpBindingId))
            return SOAP_12;
        else
            return SOAP_11;
    }

    /**
     * Returns {@link SOAPVersion} whose {@link #nsUri} equals to
     * the given string.
     *
     * This method does not perform input string validation.
     *
     * @param nsUri
     *      must not be null.
     * @return always non-null.
     */
    public static SOAPVersion fromNsUri(String nsUri) {
        if(nsUri.equals(SOAP_12.nsUri))
            return SOAP_12;
        else
            return SOAP_11;
    }

    public static SOAPVersion from(EnvelopeStyleFeature f) {
        EnvelopeStyle.Style[] style = f.getStyles();
        if (style.length != 1) throw new IllegalArgumentException ("The EnvelopingFeature must has exactly one Enveloping.Style");
        return from(style[0]);
    }

    public static SOAPVersion from(EnvelopeStyle.Style style) {
        switch (style) {
        case SOAP11: return SOAP_11;
        case SOAP12: return SOAP_12;
        case XML: //ERROR??
        default: return SOAP_11;
        }
    }

    public EnvelopeStyleFeature toFeature() {
        return SOAP_11.equals(this) ?
            new EnvelopeStyleFeature(EnvelopeStyle.Style.SOAP11) :
            new EnvelopeStyleFeature(EnvelopeStyle.Style.SOAP12);
    }
}
