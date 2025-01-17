/*
 * Copyright (c) 1997, 2022 Oracle and/or its affiliates. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0, which is available at
 * http://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package com.sun.xml.ws.message;

import com.sun.istack.NotNull;
import org.glassfish.jaxb.runtime.api.Bridge;
import com.sun.xml.ws.api.SOAPVersion;
import com.sun.xml.ws.api.message.Header;
import com.sun.xml.ws.api.message.Message;
import com.sun.xml.ws.api.message.MessageHeaders;
import com.sun.xml.ws.api.message.MessageWritable;
import com.sun.xml.ws.api.message.Packet;
import com.sun.xml.ws.api.message.saaj.SAAJFactory;
import com.sun.xml.ws.encoding.TagInfoset;
import com.sun.xml.ws.message.saaj.SAAJMessage;
import com.sun.xml.ws.spi.db.XMLBridge;
import java.util.ArrayList;
import java.util.Collections;
import org.xml.sax.ContentHandler;
import org.xml.sax.ErrorHandler;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;
import org.xml.sax.helpers.LocatorImpl;

import jakarta.xml.bind.JAXBException;
import jakarta.xml.bind.Unmarshaller;
import jakarta.xml.soap.SOAPException;
import jakarta.xml.soap.SOAPMessage;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import javax.xml.transform.Source;
import javax.xml.transform.sax.SAXSource;

import java.util.List;
import java.util.Map;

/**
 * Partial {@link Message} implementation.
 *
 * <p>
 * This class implements some of the {@link Message} methods.
 * The idea is that those implementations may be non-optimal but
 * it may save effort in implementing {@link Message} and reduce
 * the code size.
 *
 * <p>
 * {@link Message} classes that are used more commonly should
 * examine carefully which method can be implemented faster,
 * and override them accordingly.
 *
 * @author Kohsuke Kawaguchi
 */
public abstract class AbstractMessageImpl extends Message {
    /**
     * SOAP version of this message.
     * Used to implement some of the methods, but nothing more than that.
     *
     * <p>
     * So if you aren't using those methods that use this field,
     * this can be null.
     */
    protected final SOAPVersion soapVersion;

    protected @NotNull TagInfoset envelopeTag;
    protected @NotNull TagInfoset headerTag;
    protected @NotNull TagInfoset bodyTag;

    protected static final AttributesImpl EMPTY_ATTS;
    protected static final LocatorImpl NULL_LOCATOR = new LocatorImpl();
    protected static final List<TagInfoset> DEFAULT_TAGS;

    static void create(SOAPVersion v, List c) {
        int base = v.ordinal()*3;
        c.add(base, new TagInfoset(v.nsUri, "Envelope", "S", EMPTY_ATTS,"S", v.nsUri));
        c.add(base+1, new TagInfoset(v.nsUri, "Header", "S", EMPTY_ATTS));
        c.add(base+2, new TagInfoset(v.nsUri, "Body", "S", EMPTY_ATTS));
    }

    static {
        EMPTY_ATTS = new AttributesImpl();
        List<TagInfoset> tagList = new ArrayList<>();
        create(SOAPVersion.SOAP_11, tagList);
        create(SOAPVersion.SOAP_12, tagList);
        DEFAULT_TAGS = Collections.unmodifiableList(tagList);
    }
    
    protected AbstractMessageImpl(SOAPVersion soapVersion) {
        this.soapVersion = soapVersion;
    }

    @Override
    public SOAPVersion getSOAPVersion() {
        return soapVersion;
    }
    /**
     * Copy constructor.
     */
    protected AbstractMessageImpl(AbstractMessageImpl that) {
        this.soapVersion = that.soapVersion;
        this.copyFrom(that);
    }

    @Override
    public Source readEnvelopeAsSource() {
        return new SAXSource(new XMLReaderImpl(this), XMLReaderImpl.THE_SOURCE);
    }

    @Override
    public <T> T readPayloadAsJAXB(Unmarshaller unmarshaller) throws JAXBException {
        if(hasAttachments())
            unmarshaller.setAttachmentUnmarshaller(new AttachmentUnmarshallerImpl(getAttachments()));
        try {
            return (T) unmarshaller.unmarshal(readPayloadAsSource());
        } finally{
            unmarshaller.setAttachmentUnmarshaller(null);
        }
    }
    /** @deprecated */
    @Override
    public <T> T readPayloadAsJAXB(Bridge<T> bridge) throws JAXBException {
        return bridge.unmarshal(readPayloadAsSource(),
            hasAttachments()? new AttachmentUnmarshallerImpl(getAttachments()) : null );
    }
    
    @Override
    public <T> T readPayloadAsJAXB(XMLBridge<T> bridge) throws JAXBException {
        return bridge.unmarshal(readPayloadAsSource(),
            hasAttachments()? new AttachmentUnmarshallerImpl(getAttachments()) : null );
    }

    public void writeToBodyStart(XMLStreamWriter w) throws XMLStreamException {
        String soapNsUri = soapVersion.nsUri;
        w.writeStartDocument();
        w.writeStartElement("S","Envelope",soapNsUri);
        w.writeNamespace("S",soapNsUri);
        if(hasHeaders()) {
            w.writeStartElement("S","Header",soapNsUri);
            MessageHeaders headers = getHeaders();
            for (Header h : headers.asList()) {
                h.writeTo(w);
            }
            w.writeEndElement();
        }
        // write the body
        w.writeStartElement("S","Body",soapNsUri);        
    }
    
    /**
     * Default implementation that relies on {@link #writePayloadTo(XMLStreamWriter)}
     */
    @Override
    public void writeTo(XMLStreamWriter w) throws XMLStreamException {
        writeToBodyStart(w);
        writePayloadTo(w);

        w.writeEndElement();
        w.writeEndElement();
        w.writeEndDocument();
    }

    /**
     * Writes the whole envelope as SAX events.
     */
    @Override
    public void writeTo( ContentHandler contentHandler, ErrorHandler errorHandler ) throws SAXException {
        String soapNsUri = soapVersion.nsUri;

        contentHandler.setDocumentLocator(NULL_LOCATOR);
        contentHandler.startDocument();
        contentHandler.startPrefixMapping("S",soapNsUri);
        contentHandler.startElement(soapNsUri,"Envelope","S:Envelope",EMPTY_ATTS);
        if(hasHeaders()) {
            contentHandler.startElement(soapNsUri,"Header","S:Header",EMPTY_ATTS);
            MessageHeaders headers = getHeaders();
            for (Header h : headers.asList()) {
                h.writeTo(contentHandler,errorHandler);
            }
            contentHandler.endElement(soapNsUri,"Header","S:Header");
        }
        // write the body
        contentHandler.startElement(soapNsUri,"Body","S:Body",EMPTY_ATTS);
        writePayloadTo(contentHandler,errorHandler, true);
        contentHandler.endElement(soapNsUri,"Body","S:Body");
        contentHandler.endElement(soapNsUri,"Envelope","S:Envelope");
    }

    /**
     * Writes the payload to SAX events.
     *
     * @param fragment
     *      if true, this method will fire SAX events without start/endDocument events,
     *      suitable for embedding this into a bigger SAX event sequence.
     *      if false, this method generaets a completely SAX event sequence on its own.
     */
    protected abstract void writePayloadTo(ContentHandler contentHandler, ErrorHandler errorHandler, boolean fragment) throws SAXException;

    public Message toSAAJ(Packet p, Boolean inbound) throws SOAPException {
        SAAJMessage message = SAAJFactory.read(p);
        if (message instanceof MessageWritable)
            ((MessageWritable) message)
                    .setMTOMConfiguration(p.getMtomFeature());   
        if (inbound != null) transportHeaders(p, inbound, message.readAsSOAPMessage());
        return message;
    }
    
    /**
     * Default implementation that uses {@link #writeTo(ContentHandler, ErrorHandler)}
     */
    @Override
    public SOAPMessage readAsSOAPMessage() throws SOAPException {
        return SAAJFactory.read(soapVersion, this);
    }

    @Override
    public SOAPMessage readAsSOAPMessage(Packet packet, boolean inbound) throws SOAPException {
        SOAPMessage msg = SAAJFactory.read(soapVersion, this, packet);
        transportHeaders(packet, inbound, msg);
        return msg;
    }

    private void transportHeaders(Packet packet, boolean inbound, SOAPMessage msg) throws SOAPException {
        Map<String, List<String>> headers = getTransportHeaders(packet, inbound);        
        if (headers != null) {
            addSOAPMimeHeaders(msg.getMimeHeaders(), headers);
        }        
        if (msg.saveRequired()) msg.saveChanges();
    }
}
