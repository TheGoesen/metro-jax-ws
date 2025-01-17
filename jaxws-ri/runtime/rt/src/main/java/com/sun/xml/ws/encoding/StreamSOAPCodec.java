/*
 * Copyright (c) 1997, 2022 Oracle and/or its affiliates. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0, which is available at
 * http://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package com.sun.xml.ws.encoding;

import com.oracle.webservices.impl.encoding.StreamDecoderImpl;
import com.oracle.webservices.impl.internalspi.encoding.StreamDecoder;
import com.sun.istack.NotNull;
import com.sun.istack.Nullable;
import com.sun.xml.ws.api.SOAPVersion;
import com.sun.xml.ws.api.WSBinding;
import com.sun.xml.ws.api.WSFeatureList;
import com.sun.xml.ws.api.message.AttachmentSet;
import com.sun.xml.ws.api.message.Message;
import com.sun.xml.ws.api.message.Packet;
import com.sun.xml.ws.api.pipe.ContentType;
import com.sun.xml.ws.api.streaming.XMLStreamWriterFactory;
import com.sun.xml.ws.binding.WebServiceFeatureList;
import com.sun.xml.ws.developer.SerializationFeature;
import com.sun.xml.ws.message.AttachmentSetImpl;
import com.sun.xml.ws.message.stream.StreamMessage;
import com.sun.xml.ws.protocol.soap.VersionMismatchException;
import com.sun.xml.ws.server.UnsupportedMediaException;
import com.sun.xml.ws.streaming.XMLStreamReaderUtil;
import com.sun.xml.ws.util.ServiceFinder;

import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.XMLStreamWriter;
import jakarta.xml.ws.WebServiceException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;
import java.nio.charset.Charset;
import java.util.List;

/**
 * A stream SOAP codec.
 *
 * @author Paul Sandoz
 */
@SuppressWarnings({"StringEquality"})
public abstract class StreamSOAPCodec implements com.sun.xml.ws.api.pipe.StreamSOAPCodec, RootOnlyCodec {

    private static final String SOAP_ENVELOPE = "Envelope";
    private static final String SOAP_HEADER = "Header";
    private static final String SOAP_BODY = "Body";

    private final SOAPVersion soapVersion;
    protected final SerializationFeature serializationFeature;
    
    private final StreamDecoder streamDecoder;

    // charset of last decoded message. Will be used for encoding server's
    // response messages with the request message's encoding
    // it will stored in the packet.invocationProperties
    private final static String DECODED_MESSAGE_CHARSET = "decodedMessageCharset";

    /*package*/ StreamSOAPCodec(SOAPVersion soapVersion) {
        this(soapVersion, null);
    }

    /*package*/ StreamSOAPCodec(WSBinding binding) {
        this(binding.getSOAPVersion(), binding.getFeature(SerializationFeature.class));
    }
    
    StreamSOAPCodec(WSFeatureList features) {
        this(WebServiceFeatureList.getSoapVersion(features), features.get(SerializationFeature.class));
    }

    private StreamSOAPCodec(SOAPVersion soapVersion, @Nullable SerializationFeature sf) {
        this.soapVersion = soapVersion;
        this.serializationFeature = sf;
        this.streamDecoder = selectStreamDecoder();
    }
    
    private StreamDecoder selectStreamDecoder() {
        for (StreamDecoder sd : ServiceFinder.find(StreamDecoder.class)) {
            return sd;
        }
        
        return new StreamDecoderImpl();
    }

    @Override
    public ContentType getStaticContentType(Packet packet) {
        return getContentType(packet);
    }

    @Override
    public ContentType encode(Packet packet, OutputStream out) {
        if (packet.getMessage() != null) {
            String encoding = getPacketEncoding(packet);
            packet.invocationProperties.remove(DECODED_MESSAGE_CHARSET);
            XMLStreamWriter writer = XMLStreamWriterFactory.create(out, encoding);
            try {
                packet.getMessage().writeTo(writer);
                writer.flush();
            } catch (XMLStreamException e) {
                throw new WebServiceException(e);
            }
            XMLStreamWriterFactory.recycle(writer);
        }
        return getContentType(packet);
    }

    protected abstract ContentType getContentType(Packet packet);

    protected abstract String getDefaultContentType();

    @Override
    public ContentType encode(Packet packet, WritableByteChannel buffer) {
        //TODO: not yet implemented
        throw new UnsupportedOperationException();
    }

    protected abstract List<String> getExpectedContentTypes();

    @Override
    public void decode(InputStream in, String contentType, Packet packet) throws IOException {
        decode(in, contentType, packet, new AttachmentSetImpl());
    }

    /*
     * Checks against expected Content-Type headers that is handled by a codec
     *
     * @param ct the Content-Type of the request
     * @param expected expected Content-Types for a codec
     * @return true if the codec supports this Content-Type
     *         false otherwise
     */
    private static boolean isContentTypeSupported(String ct, List<String> expected) {
        for(String contentType : expected) {
            if (ct.contains(contentType)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Decodes a message from {@link XMLStreamReader} that points to
     * the beginning of a SOAP infoset.
     *
     * @param reader
     *      can point to the start document or the start element.
     */
    @Override
    public final @NotNull Message decode(@NotNull XMLStreamReader reader) {
        return decode(reader,new AttachmentSetImpl());
    }

    /**
     * Decodes a message from {@link XMLStreamReader} that points to
     * the beginning of a SOAP infoset.
     *
     * @param reader
     *      can point to the start document or the start element.
     * @param attachmentSet
     *      {@link StreamSOAPCodec} can take attachments parsed outside,
     *      so that this codec can be used as a part of a biggre codec
     *      (like MIME multipart codec.)
     */
    @Override
    public final Message decode(XMLStreamReader reader, @NotNull AttachmentSet attachmentSet) {
        return decode(soapVersion, reader, attachmentSet);
    }
    
    public static final Message decode(SOAPVersion soapVersion, XMLStreamReader reader, @NotNull AttachmentSet attachmentSet) {
        // Move to soap:Envelope and verify
        if(reader.getEventType()!=XMLStreamConstants.START_ELEMENT)
            XMLStreamReaderUtil.nextElementContent(reader);
        XMLStreamReaderUtil.verifyReaderState(reader,XMLStreamConstants.START_ELEMENT);
        if (SOAP_ENVELOPE.equals(reader.getLocalName()) && !soapVersion.nsUri.equals(reader.getNamespaceURI())) {
            throw new VersionMismatchException(soapVersion, soapVersion.nsUri, reader.getNamespaceURI());
        }
        XMLStreamReaderUtil.verifyTag(reader, soapVersion.nsUri, SOAP_ENVELOPE);
        return new StreamMessage(soapVersion, reader, attachmentSet);
    }

    @Override
    public void decode(ReadableByteChannel in, String contentType, Packet packet ) {
        throw new UnsupportedOperationException();
    }

    @Override
    public final StreamSOAPCodec copy() {
        return this;
    }
    
    @Override
    public void decode(InputStream in, String contentType, Packet packet, AttachmentSet att ) throws IOException {
        List<String> expectedContentTypes = getExpectedContentTypes();
        if (contentType != null && !isContentTypeSupported(contentType,expectedContentTypes)) {
            throw new UnsupportedMediaException(contentType, expectedContentTypes);
        }
        com.oracle.webservices.api.message.ContentType pct = packet.getInternalContentType();
        ContentTypeImpl cti = (pct instanceof ContentTypeImpl) ?
                (ContentTypeImpl)pct : new ContentTypeImpl(contentType);
        String charset = cti.getCharSet();
        if (charset != null && !Charset.isSupported(charset)) {
            throw new UnsupportedMediaException(charset);
        }
        if (charset != null) {
            packet.invocationProperties.put(DECODED_MESSAGE_CHARSET, charset);
        } else {
            packet.invocationProperties.remove(DECODED_MESSAGE_CHARSET);
        }
        packet.setMessage(streamDecoder.decode(in, charset, att, soapVersion));
    }

    @Override
    public void decode(ReadableByteChannel in, String contentType, Packet response, AttachmentSet att ) {
        throw new UnsupportedOperationException();
    }

    /*
     * Creates a new {@link StreamSOAPCodec} instance.
     */
    public static StreamSOAPCodec create(SOAPVersion version) {
        if(version==null)
            // this decoder is for SOAP, not for XML/HTTP
            throw new IllegalArgumentException();
        switch(version) {
            case SOAP_11:
                return new StreamSOAP11Codec();
            case SOAP_12:
                return new StreamSOAP12Codec();
            default:
                throw new AssertionError();
        }
    }

    /*
     * Creates a new {@link StreamSOAPCodec} instance using binding
     */
    public static StreamSOAPCodec create(WSFeatureList features) {
        SOAPVersion version = WebServiceFeatureList.getSoapVersion(features);
        if(version==null)
            // this decoder is for SOAP, not for XML/HTTP
            throw new IllegalArgumentException();
        switch(version) {
            case SOAP_11:
                return new StreamSOAP11Codec(features);
            case SOAP_12:
                return new StreamSOAP12Codec(features);
            default:
                throw new AssertionError();
        }
    }

    /**
     * Creates a new {@link StreamSOAPCodec} instance using binding
     * 
     * @deprecated use {@link #create(WSFeatureList)}
     */
    public static StreamSOAPCodec create(WSBinding binding) {
        SOAPVersion version = binding.getSOAPVersion();
        if(version==null)
            // this decoder is for SOAP, not for XML/HTTP
            throw new IllegalArgumentException();
        switch(version) {
            case SOAP_11:
                return new StreamSOAP11Codec(binding);
            case SOAP_12:
                return new StreamSOAP12Codec(binding);
            default:
                throw new AssertionError();
        }
    }

    private String getPacketEncoding(Packet packet) {
        // If SerializationFeature is set, just use that encoding
        if (serializationFeature != null && serializationFeature.getEncoding() != null) {
            return serializationFeature.getEncoding().equals("")
                    ? SOAPBindingCodec.DEFAULT_ENCODING : serializationFeature.getEncoding();
        }

        if (packet != null && packet.endpoint != null) {
            // Use request message's encoding for Server-side response messages
            String charset = (String)packet.invocationProperties.get(DECODED_MESSAGE_CHARSET);
            return charset == null
                    ? SOAPBindingCodec.DEFAULT_ENCODING : charset;
        } 
        
        // Use default encoding for client-side request messages
        return SOAPBindingCodec.DEFAULT_ENCODING;
    }

    protected ContentTypeImpl.Builder getContenTypeBuilder(Packet packet) {
        ContentTypeImpl.Builder b = new ContentTypeImpl.Builder();
        String encoding = getPacketEncoding(packet);
        if (SOAPBindingCodec.DEFAULT_ENCODING.equalsIgnoreCase(encoding)) {
            b.contentType = getDefaultContentType();
            b.charset = SOAPBindingCodec.DEFAULT_ENCODING;
            return b;
        }
        b.contentType = getMimeType()+" ;charset="+encoding;
        b.charset = encoding;
        return b;
    }

}
