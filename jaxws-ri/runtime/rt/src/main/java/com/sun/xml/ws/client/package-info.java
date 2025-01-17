/*
 * Copyright (c) 1997, 2022 Oracle and/or its affiliates. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0, which is available at
 * http://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

/**
 * <P>This document describes the architecture of client side 
 * JAX-WS 2.0.1 runtime. 
 *
 * <h2>JAX-WS 2.0.1 Client Sequence Diagram</h2>
 * <img alt="Client Sequence Diagram" src='../../../../../jaxws/basic-client.seq.png'>
  * <h2>JAX-WS 2.0.1 Asynchronous Invocation Sequence Diagram</h2>
 * <img alt="Asynchronous Invocation Sequence Diagram" src='../../../../../jaxws/client-async.seq.png'>
  * <h2>JAX-WS 2.0.1 Dispatch Invocation Sequence Diagram</h2>
 * <img alt="Dispatch Invocation Sequence Diagram" src='../../../../../jaxws/dispatch.seq.png'>

 * <H3>Message Flow</H3>
 * {@link WSServiceDelegate} provides client view of a Web service.
 * WSServiceDelegate.getPort returns an instance of {@code com.sun.xml.ws.client.EndpointIFInvocationHandler}
 * with {@code com.sun.pept.ept.ContactInfoList} and {@code com.sun.pept.Delegate} 
 * initialized. A method invocation on the port, obtained from WebService, invokes
 * {@code com.sun.xml.ws.client.EndpointIFInvocationHandler#invoke}. This method 
 * then creates a {@code com.sun.pept.ept.MessageInfo} and populates the data 
 * (parameters specified by the user) and metadata such as RuntimeContext, RequestContext, 
 * Message Exchange Pattern into this MessageInfo. This method then invokes 
 * {@code com.sun.pept.Delegate#send} and returns the response.
 * <p>
 * The Delegate.send method iterates through the ContactInfoList and picks up the 
 * correct {@code com.sun.pept.ept.ContactInfo} based upon the binding id of 
 * {@link jakarta.xml.ws.BindingProvider} and sets it on the MessageInfo. After the
 * Delegate obtains a specific ContactInfo it uses that ContactInfo to obtain a 
 * protocol-specific {@code com.sun.pept.protocol.MessageDispatcher}. There will be 
 * two types of client-side MessageDispatchers for JAX-WS 2.0.1, 
 * {@code com.sun.xml.ws.protocol.soap.client.SOAPMessageDispatcher} and 
 * {@code com.sun.xml.ws.protocol.xml.client.XMLMessageDispatcher}. The Delegate 
 * then invokes {@code com.sun.pept.protocol.MessageDispatcher#send}. The 
 * MessageDispatcher.send method makes a decision about the synchronous and 
 * asynchronous nature of the message exchange pattern and invokes separate methods
 * accordingly.
 * <p>
 * The MessageDispatcher uses ContactInfo to obtain
 * a {@code com.sun.xml.ws.encoding.soap.client.SOAPXMLEncoder} which converts 
 * the MessageInfo to {@code com.sun.xml.ws.encoding.soap.internal.InternalMessage}. 
 * There will be two types of client-side SOAPXMLEncoder for JAX-WS 2.0.1, 
 * SOAPXMEncoder for SOAP 1.1 and {@code com.sun.xml.ws.encoding.soap.client.SOAP12XMLEncoder}
 * for SOAP 1.2. The MessageDispatcher invokes configured handlers and use the 
 * codec to convert the InternalMessage to a {@link jakarta.xml.soap.SOAPMessage}.
 * The metadata from the MessageInfo is classified into {@link jakarta.xml.soap.MimeHeaders}
 * of this SOAPMessage and context information for {@code com.sun.xml.ws.api.server.WSConnection}.
 * The SOAPMessge is then written to the output stream of the WSConnection
 * obtained from MessageInfo.
 * <p>
 * The MessageDispatcher.receive method handles the response. The 
 * SOAPMessageDispatcher extracts the SOAPMessage from the input stream of 
 * WSConnection and performs the mustUnderstand processing followed by invocation 
 * of any handlers. The MessageDispatcher uses ContactInfo to obtain a 
 * {@code com.sun.xml.ws.encoding.soap.client.SOAPXMLDecoder} which converts the SOAPMessage 
 * to InternalMessage and then InternalMessage to MessageInfo. There will be two types of 
 * client-side SOAPXMLDecoder for JAX-WS 2.0.1, SOAPXMLDencoder for SOAP 1.1 and 
 * {@code com.sun.xml.ws.encoding.soap.client.SOAP12XMLDecoder} for SOAP 1.2. The 
 * response is returned back to the client code via Delegate.
 *
 * <H3>External Interactions</H3>
 * <H4>SAAJ API</H4>
 * <UL>
 * 	<LI><P>JAX-WS creates SAAJ SOAPMessage from the HttpServletRequest.
 * 	At present, JAX-WS reads all the bytes from the request stream and
 * 	then creates SOAPMessage along with the HTTP headers.</P>
 * </UL>
 * <P>MessageFactory(binding).createMessage(MimeHeaders, InputStream)</P>
 * <UL>
 * 	<LI><P>SOAPMessage parses the content from the stream including MIME
 * 	data</P>
 * 	<LI><P>com.sun.xml.ws.server.SOAPMessageDispatcher::checkHeadersPeekBody()</P>
 * 	<P>SOAPMessage.getSOAPHeader() is used for mustUnderstand processing
 * 	of headers. It further uses
 * 	SOAPHeader.examineMustUnderstandHeaderElements(role)</P>
 * 	<P>SOAPMessage.getSOAPBody().getFistChild() is used for guessing the
 * 	MEP of the request</P>
 * 	<LI><P>com.sun.xml.ws.handler.HandlerChainCaller:insertFaultMessage()</P>
 * 	<P>SOAPMessage.getSOAPPart().getEnvelope() and some other SAAJ calls
 * 	are made to create a fault in the SOAPMessage</P>
 * 	<LI><P>com.sun.xml.ws.handler.LogicalMessageImpl::getPayload()
 * 	interacts with SAAJ to get body from SOAPMessage</P>
 * 	<LI><P>com.sun.xml.ws.encoding.soap.SOAPEncoder.toSOAPMessage(com.sun.xml.ws.encoding.soap.internal.InternalMessage,
 * 	SOAPMessage). There is a scenario where there is SOAPMessage and a
 * 	logical handler sets payload as Source. To write to the stream,
 * 	SOAPMessage.writeTo() is used but before that the body needs to be
 * 	updated with logical handler' Source. Need to verify if this
 * 	scenario is still happening since Handler.close() is changed to take
 * 	MessageContext.</P>
 * 	<LI><P>com.sun.xml.ws.handlerSOAPMessageContextImpl.getHeaders()
 * 	uses SAAJ API to get headers.</P>
 * 	<LI><P>SOAPMessage.writeTo() is used to write response. At present,
 * 	it writes into byte[] and this byte[] is written to
 * 	HttpServletResponse.</P>
 * </UL>
 * <H4>JAXB API</H4>
 * <P>JAX-WS RI uses the JAXB API to marshall/unmarshall user created
 * JAXB objects with user created {@link jakarta.xml.bind.JAXBContext JAXBContext}.
 * Handler, Dispatch in JAX-WS API provide ways for the user to specify his/her own
 * JAXBContext. {@code com.sun.xml.ws.encoding.jaxb.JAXBTypeSerializer JAXBTypeSerializer} class uses all these methods.</P>
 * <UL>
 * 	<LI><p>{@link jakarta.xml.bind.Marshaller#marshal(Object,XMLStreamWriter) Marshaller.marshal(Object,XMLStreamWriter)}</p>
 * 	<LI><P>{@link jakarta.xml.bind.Marshaller#marshal(Object,Result) Marshaller.marshal(Object, DomResult)}</P>
 * 	<LI><P>{@link jakarta.xml.bind.Unmarshaller#unmarshal(XMLStreamReader) Object Unmarshaller.unmarshal(XMLStreamReader)}</P>
 * 	<LI><P>{@link jakarta.xml.bind.Unmarshaller#unmarshal(Source) Object Unmarshaller.unmarshal(Source)}</P>
 * </UL>
 * The following two JAXB classes are implemented by JAX-WS to enable/implement MTOM and XOP
 * <UL>
 *      <LI><P>{@link jakarta.xml.bind.attachment.AttachmentMarshaller AttachmentMarshaller}</P>
 *      <LI><P>{@link jakarta.xml.bind.attachment.AttachmentUnmarshaller AttachmentUnmarshaller}</P>
 * </UL>
 * <H4>JAXB Runtime-API (private contract)</H4>
 * <P>JAX-WS RI uses these private API for serialization/deserialization
 * purposes. This private API is used to serialize/deserialize method
 * parameters at the time of JAXBTypeSerializer class uses all
 * these methods.</P>
 * <UL>
 * 	<LI><P>{@link org.glassfish.jaxb.runtime.api.Bridge#marshal(BridgeContext, Object, XMLStreamWriter) Bridge.marshal(BridgeContext, Object, XMLStreamWriter)}</P>
 * 	<LI><P>{@link org.glassfish.jaxb.runtime.api.Bridge#marshal(BridgeContext, Object, Node) Bridge.marshal(BridgeContext, Object, Node)}</P>
 * 	<LI><P>{@link org.glassfish.jaxb.runtime.api.Bridge#unmarshal(BridgeContext, XMLStreamReader) Object Bridge.unmarshal(BridgeContext, XMLStreamReader)}</P>
 * </UL>
 * 
 **/
package com.sun.xml.ws.client;

import org.glassfish.jaxb.runtime.api.BridgeContext;

import javax.xml.transform.Result;
import javax.xml.transform.Source;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.XMLStreamWriter;

import org.w3c.dom.Node;
