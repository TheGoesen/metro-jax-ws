/*
 * Copyright (c) 1997, 2022 Oracle and/or its affiliates. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0, which is available at
 * http://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package com.sun.xml.ws.api.message;

import com.sun.istack.NotNull;
import com.sun.istack.Nullable;
import org.glassfish.jaxb.runtime.api.Bridge;
import com.sun.xml.ws.api.SOAPVersion;
import com.sun.xml.ws.api.addressing.AddressingVersion;
import com.sun.xml.ws.api.addressing.WSEndpointReference;
import com.sun.xml.ws.spi.db.XMLBridge;

import org.xml.sax.ContentHandler;
import org.xml.sax.ErrorHandler;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

import jakarta.xml.bind.JAXBException;
import jakarta.xml.bind.Unmarshaller;
import javax.xml.namespace.QName;
import jakarta.xml.soap.SOAPException;
import jakarta.xml.soap.SOAPMessage;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.XMLStreamWriter;
import jakarta.xml.ws.WebServiceException;
import java.util.Set;


/**
 * A SOAP header.
 *
 * <p>
 * A header is immutable, but unlike body it can be read
 * multiple times.
 * The {@link Header} abstraction hides how the header
 * data is represented in memory; instead, it commits to
 * the ability to write itself to XML infoset.
 *
 * <p>
 * When a message is received from the transport and
 * being processed, the processor needs to "peek"
 * some information of a header, such as the tag name,
 * the mustUnderstand attribute, and so on. Therefore,
 * the {@link Header} interface exposes those information
 * as properties, so that they can be checked without
 * replaying the infoset, which is efficiently but still
 * costly.
 *
 * <p>
 * A {@link Header} may belong to more than one {@link HeaderList}
 * due to wrapping of {@link Message}.
 *
 * @see HeaderList
 * @see Headers
 */
public interface Header {
    // TODO: Vivek pointed out that the only time we are looking at
    // mustUnderstand and role are when we do the mustUnderstand error check
    // (that is, to find out if there's any header with @mustUnderstand that
    // has appropriate role for us.)
    // if that's the case, it might be better if we define this whole operation
    // as one method, instead of exposing two properties.

    /**
     * Checks if this header is ignorable for us (IOW, make sure
     * that this header has a problematic "mustUnderstand" header value
     * that we have to reject.)
     *
     * <p>
     * This method is used as a part of the
     * <a href="HeaderList.html#MU">mustUnderstanx processing</a>.
     * At the end of the processing, the JAX-WS identifies a list of {@link Header}s
     * that were not understood. This method is invoked on those {@link Header}s,
     * to verify that we don't need to report an error for it.
     *
     * <p>
     * specifically, this method has to perform the following tasks:
     *
     * <ul>
     *  <li>If this header does not have {@code mustUnderstand} as "1" nor "true",
     *      then this method must return true.
     *  <li>Otherwise, check the role attribute (for SOAP 1.2) or the actor attribute (for SOAP 1.1).
     *      When those attributes are absent, the default values have to be assumed.
     *      See {@link #getRole(SOAPVersion)} for how the values are defaulted.
     *      Now, see if the {@code roles} set contains the value.
     *      If so, this method must return false (indicating that an error is in order.)
     *  <li>Otherwise return true (since we don't play the role this header is intended for.)
     * </ul>
     *
     * @param soapVersion
     *      The caller specifies the SOAP version that the pipeline is working against.
     *      Often each {@link Header} implementation already knows the SOAP version
     *      anyway, but this allows some {@link Header}s to avoid keeping it.
     *      That's why this redundant parameter is passed in.
     * @param roles
     *      The set of role values that the current JAX-WS pipeline is assuming.
     *      Note that SOAP 1.1 and SOAP 1.2 use different strings for the same role,
     *      and the caller is responsible for supplying a proper value depending on the
     *      active SOAP version in use.
     *
     * @return
     *      true if no error needs to be reported. False if an error needs to be raised.
     *      See the method javadoc for more discussion.
     */
    boolean isIgnorable(@NotNull SOAPVersion soapVersion, @NotNull Set<String> roles);

    /**
     * Gets the value of the soap:role attribute (or soap:actor for SOAP 1.1).
     *
     * <p>
     * If the attribute is omitted, the value defaults to {@link SOAPVersion#implicitRole}.
     *
     * @param soapVersion
     *      The caller specifies the SOAP version that the pipeline is working against.
     *      Often each {@link Header} implementation already knows the SOAP version
     *      anyway, but this allows some {@link Header}s to avoid keeping it.
     *      That's why this redundant parameter is passed in.
     * @return
     *      never null. This string need not be interned.
     */
    @NotNull String getRole(@NotNull SOAPVersion soapVersion);

    /**
     * True if this header is to be relayed if not processed.
     * For SOAP 1.1 messages, this method always return false.
     *
     * <p>
     * IOW, this method returns true if there's @soap:relay='true'
     * is present.
     *
     * <p>
     * <strong>Implementation Note</strong>
     * <p>
     * The implementation needs to check for both "true" and "1",
     * but because attribute values are normalized, it doesn't have
     * to consider " true", " 1 ", and so on.
     *
     * @return
     *      false.
     */
    boolean isRelay();

    /**
     * Gets the namespace URI of this header element.
     *
     * @return
     *      this string must be interned.
     */
    @NotNull String getNamespaceURI();

    /**
     * Gets the local name of this header element.
     *
     * @return
     *      this string must be interned.
     */
    @NotNull String getLocalPart();

    /**
     * Gets the attribute value on the header element.
     *
     * @param nsUri
     *      The namespace URI of the attribute. Can be empty.
     * @param localName
     *      The local name of the attribute.
     *
     * @return
     *      if the attribute is found, return the whitespace normalized value.
     *      (meaning no leading/trailing space, no consequtive whitespaces in-between.)
     *      Otherwise null. Note that the XML parsers are responsible for
     *      whitespace-normalizing attributes, so {@link Header} implementation
     *      doesn't have to do anything.
     */
    @Nullable String getAttribute(@NotNull String nsUri, @NotNull String localName);

    /**
     * Gets the attribute value on the header element.
     *
     * <p>
     * This is a convenience method that calls into {@link #getAttribute(String, String)}
     *
     * @param name
     *      Never null.
     *
     * @see #getAttribute(String, String)
     */
    @Nullable String getAttribute(@NotNull QName name);

    /**
     * Reads the header as a {@link XMLStreamReader}.
     *
     * <p>
     * The returned parser points at the start element of this header.
     * (IOW, {@link XMLStreamReader#getEventType()} would return
     * {@link XMLStreamReader#START_ELEMENT}.
     *
     * <p>
     * <strong>Performance Expectation</strong>
     * <p>
     * For some {@link Header} implementations, this operation
     * is a non-trivial operation. Therefore, use of this method
     * is discouraged unless the caller is interested in reading
     * the whole header.
     *
     * <p>
     * Similarly, if the caller wants to use this method only to do
     * the API conversion (such as simply firing SAX events from
     * {@link XMLStreamReader}), then the JAX-WS team requests
     * that you talk to us.
     *
     * <p>
     * {@link Message}s that come from tranport usually provides
     * a reasonably efficient implementation of this method.
     *
     * @return
     *      must not null.
     */
    XMLStreamReader readHeader() throws XMLStreamException;

    /**
     * Reads the header as a JAXB object by using the given unmarshaller.
     */
    <T> T readAsJAXB(Unmarshaller unmarshaller) throws JAXBException;

    /**
     * Reads the header as a JAXB object by using the given unmarshaller.
     * @deprecated
     */
    <T> T readAsJAXB(Bridge<T> bridge) throws JAXBException;
    
    /**
     * Reads the header as a data-bond object
     */
    <T> T readAsJAXB(XMLBridge<T> bridge) throws JAXBException;
    
    /**
     * Reads this header as an {@link WSEndpointReference}.
     *
     * @param expected
     *      The version of the addressing used to parse the EPR.
     *      If the actual infoset and this doesn't agree, then
     *      you'll get an {@link WebServiceException} stating that fact.
     *
     * @return
     *      On a successful return, this method never returns null.
     */
    @NotNull WSEndpointReference readAsEPR(AddressingVersion expected) throws XMLStreamException;

    /**
     * Writes out the header as a fragment.
     *
     * @throws XMLStreamException
     *      if the operation fails for some reason. This leaves the
     *      writer to an undefined state.
     */
    void writeTo(XMLStreamWriter w) throws XMLStreamException;

    /**
     * Writes out the header to the given SOAPMessage.
     *
     * <p>
     * Sometimes a {@link Message} needs to produce itself
     * as {@link SOAPMessage}, in which case each header needs
     * to turn itself into a header.
     *
     * @throws SOAPException
     *      if the operation fails for some reason. This leaves the
     *      writer to an undefined state.
     */
    void writeTo(SOAPMessage saaj) throws SOAPException;

    /**
     * Writes out the header as SAX events.
     *
     * <p>
     * Sometimes a {@link Message} needs to produce SAX events,
     * and this method is necessary for headers to participate to it.
     *
     * <p>
     * A header is responsible for producing the SAX events for its part,
     * including {@code startPrefixMapping} and {@code endPrefixMapping},
     * but not startDocument/endDocument.
     *
     * <p>
     * Note that SAX contract requires that any error that does NOT originate
     * from {@link ContentHandler} (meaning any parsing error and etc) must
     * be first reported to {@link ErrorHandler}. If the SAX event production
     * cannot be continued and the processing needs to abort, the code may
     * then throw the same {@link SAXParseException} reported to {@link ErrorHandler}.
     *
     * @param contentHandler
     *      The {@link ContentHandler} that receives SAX events.
     *
     * @param errorHandler
     *      The {@link ErrorHandler} that receives parsing errors.
     */
    void writeTo(ContentHandler contentHandler, ErrorHandler errorHandler) throws SAXException;

    /**
     * Used to obtain value XYZ from a header that looks like
     * "&lt;header&gt;XYZ&lt;/header&gt;". The primary use of this header
     * for now is to access certain Addressing headers quickly.
     *
     * @throws WebServiceException
     *      If the structure of the header is more complicated than
     *      a simple string header.
     *
     * @return
     *      Can be empty but always non-null.
     */
    @NotNull String getStringContent();
}
