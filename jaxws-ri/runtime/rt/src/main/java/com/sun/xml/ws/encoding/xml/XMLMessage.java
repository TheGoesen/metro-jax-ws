/*
 * Copyright (c) 1997, 2022 Oracle and/or its affiliates. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0, which is available at
 * http://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package com.sun.xml.ws.encoding.xml;

import com.sun.istack.NotNull;
import org.glassfish.jaxb.runtime.api.Bridge;
import com.sun.xml.ws.api.SOAPVersion;
import com.sun.xml.ws.api.WSFeatureList;
import com.sun.xml.ws.api.message.*;
import com.sun.xml.ws.api.model.wsdl.WSDLPort;
import com.sun.xml.ws.api.pipe.Codec;
import com.sun.xml.ws.api.streaming.XMLStreamWriterFactory;
import com.sun.xml.ws.developer.StreamingAttachmentFeature;
import com.sun.xml.ws.encoding.ContentType;
import com.sun.xml.ws.encoding.MimeMultipartParser;
import com.sun.xml.ws.encoding.XMLHTTPBindingCodec;
import com.sun.xml.ws.message.AbstractMessageImpl;
import com.sun.xml.ws.message.EmptyMessageImpl;
import com.sun.xml.ws.message.MimeAttachmentSet;
import com.sun.xml.ws.message.source.PayloadSourceMessage;
import com.sun.xml.ws.util.ByteArrayBuffer;
import com.sun.xml.ws.util.StreamUtils;
import org.xml.sax.ContentHandler;
import org.xml.sax.ErrorHandler;
import org.xml.sax.SAXException;

import jakarta.activation.DataSource;
import jakarta.xml.bind.JAXBException;
import jakarta.xml.bind.Unmarshaller;
import jakarta.xml.soap.SOAPException;
import jakarta.xml.soap.SOAPMessage;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.XMLStreamWriter;
import javax.xml.transform.Source;
import javax.xml.transform.stream.StreamSource;
import jakarta.xml.ws.WebServiceException;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 *
 * @author Jitendra Kotamraju
 */
public final class XMLMessage {

    private static final int PLAIN_XML_FLAG      = 1;       // 00001
    private static final int MIME_MULTIPART_FLAG = 2;       // 00010
    private static final int FI_ENCODED_FLAG     = 16;      // 10000

    /*
     * Construct a message given a content type and an input stream.
     */
    public static Message create(final String ct, InputStream in, WSFeatureList f) {
        Message data;
        try {
            in = StreamUtils.hasSomeData(in);
            if (in == null) {
                return Messages.createEmpty(SOAPVersion.SOAP_11);
            }

            if (ct != null) {
                final ContentType contentType = new ContentType(ct);
                final int contentTypeId = identifyContentType(contentType);
                if ((contentTypeId & MIME_MULTIPART_FLAG) != 0) {
                    data = new XMLMultiPart(ct, in, f);
                } else if ((contentTypeId & PLAIN_XML_FLAG) != 0) {
                    data = new XmlContent(ct, in, f);
                } else {
                    data = new UnknownContent(ct, in);
                }
            } else {
                // According to HTTP spec 7.2.1, if the media type remain
                // unknown, treat as application/octet-stream
                data = new UnknownContent("application/octet-stream", in);
            }
        } catch(Exception ex) {
            throw new WebServiceException(ex);
        }
        return data;
    }


    public static Message create(Source source) {
        return (source == null) ? 
            Messages.createEmpty(SOAPVersion.SOAP_11) : 
            Messages.createUsingPayload(source, SOAPVersion.SOAP_11);
    }

    public static Message create(DataSource ds, WSFeatureList f) {
        try {
            return (ds == null) ? 
                Messages.createEmpty(SOAPVersion.SOAP_11) : 
                create(ds.getContentType(), ds.getInputStream(), f);
        } catch(IOException ioe) {
            throw new WebServiceException(ioe);
        }
    }

    public static Message create(Exception e) {
        return new FaultMessage(SOAPVersion.SOAP_11);
    }

    /*
     * Get the content type ID from the content type.
     */
    private static int getContentId(String ct) {    
        try {
            final ContentType contentType = new ContentType(ct);
            return identifyContentType(contentType);
        } catch(Exception ex) {
            throw new WebServiceException(ex);
        }
    }
    
    /**
     * Return true if the content uses fast infoset.
     */
    public static boolean isFastInfoset(String ct) {    
        return (getContentId(ct) & FI_ENCODED_FLAG) != 0;
    }
    
    /*
     * Verify a contentType.
     *
     * @return
     * MIME_MULTIPART_FLAG | PLAIN_XML_FLAG
     * MIME_MULTIPART_FLAG | FI_ENCODED_FLAG;
     * PLAIN_XML_FLAG
     * FI_ENCODED_FLAG
     *
     */
    public static int identifyContentType(ContentType contentType) {
        String primary = contentType.getPrimaryType();
        String sub = contentType.getSubType();

        if (primary.equalsIgnoreCase("multipart") && sub.equalsIgnoreCase("related")) {
            String type = contentType.getParameter("type");
            if (type != null) {
                if (isXMLType(type)) {
                    return MIME_MULTIPART_FLAG | PLAIN_XML_FLAG;
                } else if (isFastInfosetType(type)) {
                    return MIME_MULTIPART_FLAG | FI_ENCODED_FLAG;
                }
            }
            return 0;
        } else if (isXMLType(primary, sub)) {
            return PLAIN_XML_FLAG;
        } else if (isFastInfosetType(primary, sub)) {
            return FI_ENCODED_FLAG;
        }
        return 0;
    }

    protected static boolean isXMLType(@NotNull String primary, @NotNull String sub) {
        return (primary.equalsIgnoreCase("text") && sub.equalsIgnoreCase("xml"))
                || (primary.equalsIgnoreCase("application") && sub.equalsIgnoreCase("xml"))
                || (primary.equalsIgnoreCase("application") && sub.toLowerCase().endsWith("+xml"));
    }

    protected static boolean isXMLType(String type) {
        String lowerType = type.toLowerCase();
        return lowerType.startsWith("text/xml")
                || lowerType.startsWith("application/xml")
                || (lowerType.startsWith("application/") && (lowerType.contains("+xml")));
    }

    protected static boolean isFastInfosetType(String primary, String sub) {
        return primary.equalsIgnoreCase("application") && sub.equalsIgnoreCase("fastinfoset");
    }

    protected static boolean isFastInfosetType(String type) {
        return type.toLowerCase().startsWith("application/fastinfoset");
    }
    
    
    /**
     * Access a {@link Message} as a {@link DataSource}.
     * <p>
     * A {@link Message} implementation will implement this if the 
     * messages is to be access as data source.
     * <p>
     * TODO: consider putting as part of the API.
     */
    public interface MessageDataSource {
        /**
         * Check if the data source has been consumed.
         * @return true of the data source has been consumed, otherwise false.
         */
        boolean hasUnconsumedDataSource();
        
        /**
         * Get the data source.
         * @return the data source.
         */
        DataSource getDataSource();
    }

    /**
     * It's conent-type is some XML type
     *
     */
    private static class XmlContent extends AbstractMessageImpl implements MessageDataSource {
        private final XmlDataSource dataSource;
        private boolean consumed;
        private Message delegate;
        private final HeaderList headerList;
//      private final WSBinding binding;
        private WSFeatureList features;
        
        public XmlContent(String ct, InputStream in, WSFeatureList f) {
            super(SOAPVersion.SOAP_11);
            dataSource = new XmlDataSource(ct, in);
            this.headerList = new HeaderList(SOAPVersion.SOAP_11);
//            this.binding = binding;
            features = f;
        }

        private Message getMessage() {
            if (delegate == null) {
                InputStream in = dataSource.getInputStream();
                assert in != null;
                delegate = Messages.createUsingPayload(new StreamSource(in), SOAPVersion.SOAP_11);
                consumed = true;
            }
            return delegate;
        }

        @Override
        public boolean hasUnconsumedDataSource() {
            return !dataSource.consumed()&&!consumed;
        }

        @Override
        public DataSource getDataSource() {
            return hasUnconsumedDataSource() ? dataSource :
                XMLMessage.getDataSource(getMessage(), features);
        }

        @Override
        public boolean hasHeaders() {
            return false;
        }

        @Override
        public @NotNull MessageHeaders getHeaders() {
            return headerList;
        }

        @Override
        public String getPayloadLocalPart() {
            return getMessage().getPayloadLocalPart();
        }

        @Override
        public String getPayloadNamespaceURI() {
            return getMessage().getPayloadNamespaceURI();
        }

        @Override
        public boolean hasPayload() {
            return true;
        }

        @Override
        public boolean isFault() {
            return false;
        }

        @Override
        public Source readEnvelopeAsSource() {
            return getMessage().readEnvelopeAsSource();
        }

        @Override
        public Source readPayloadAsSource() {
            return getMessage().readPayloadAsSource();
        }

        @Override
        public SOAPMessage readAsSOAPMessage() throws SOAPException {
            return getMessage().readAsSOAPMessage();
        }

        @Override
        public SOAPMessage readAsSOAPMessage(Packet packet, boolean inbound) throws SOAPException {
            return getMessage().readAsSOAPMessage(packet, inbound);
        }

        @Override
        public <T> T readPayloadAsJAXB(Unmarshaller unmarshaller) throws JAXBException {
            return getMessage().readPayloadAsJAXB(unmarshaller);
        }
        /** @deprecated */
        @Override
        public <T> T readPayloadAsJAXB(Bridge<T> bridge) throws JAXBException {
            return getMessage().readPayloadAsJAXB(bridge);
        }

        @Override
        public XMLStreamReader readPayload() throws XMLStreamException {
            return getMessage().readPayload();
        }


        @Override
        public void writePayloadTo(XMLStreamWriter sw) throws XMLStreamException {
            getMessage().writePayloadTo(sw);
        }

        @Override
        public void writeTo(XMLStreamWriter sw) throws XMLStreamException {
            getMessage().writeTo(sw);
        }

        @Override
        public void writeTo(ContentHandler contentHandler, ErrorHandler errorHandler) throws SAXException {
            getMessage().writeTo(contentHandler, errorHandler);
        }

        @Override
        public Message copy() {
            return getMessage().copy().copyFrom(getMessage());
        }

        @Override
        protected void writePayloadTo(ContentHandler contentHandler, ErrorHandler errorHandler, boolean fragment) throws SAXException {
            throw new UnsupportedOperationException();
        }

    }



    /**
     * Data represented as a multi-part MIME message. 
     * <p>
     * The root part may be an XML or an FI document. This class
     * parses MIME message lazily.
     */
    public static final class XMLMultiPart extends AbstractMessageImpl implements MessageDataSource {
        private final DataSource dataSource;
        private final StreamingAttachmentFeature feature;
        private Message delegate;
        private HeaderList headerList;// = new HeaderList();
//      private final WSBinding binding;
        private final WSFeatureList features;

        public XMLMultiPart(final String contentType, final InputStream is, WSFeatureList f) {
            super(SOAPVersion.SOAP_11);
            headerList = new HeaderList(SOAPVersion.SOAP_11);
            dataSource = createDataSource(contentType, is);
            this.feature = f.get(StreamingAttachmentFeature.class);
            this.features = f;
        }

        private Message getMessage() {
            if (delegate == null) {
                MimeMultipartParser mpp;
                try {
                    mpp = new MimeMultipartParser(dataSource.getInputStream(),
                            dataSource.getContentType(), feature);
                } catch(IOException ioe) {
                    throw new WebServiceException(ioe);
                }
                InputStream in = mpp.getRootPart().asInputStream();
                assert in != null;
                delegate = new PayloadSourceMessage(headerList, new StreamSource(in), new MimeAttachmentSet(mpp), SOAPVersion.SOAP_11);
            }
            return delegate;
        }

        @Override
        public boolean hasUnconsumedDataSource() {
            return delegate == null;
        }

        @Override
        public DataSource getDataSource() {
            return hasUnconsumedDataSource() ? dataSource :
                XMLMessage.getDataSource(getMessage(), features);
        }

        @Override
        public boolean hasHeaders() {
            return false;
        }

        @Override
        public @NotNull MessageHeaders getHeaders() {
            return headerList;
        }

        @Override
        public String getPayloadLocalPart() {
            return getMessage().getPayloadLocalPart();
        }

        @Override
        public String getPayloadNamespaceURI() {
            return getMessage().getPayloadNamespaceURI();
        }

        @Override
        public boolean hasPayload() {
            return true;
        }

        @Override
        public boolean isFault() {
            return false;
        }

        @Override
        public Source readEnvelopeAsSource() {
            return getMessage().readEnvelopeAsSource();
        }

        @Override
        public Source readPayloadAsSource() {
            return getMessage().readPayloadAsSource();
        }

        @Override
        public SOAPMessage readAsSOAPMessage() throws SOAPException {
            return getMessage().readAsSOAPMessage();
        }

        @Override
        public SOAPMessage readAsSOAPMessage(Packet packet, boolean inbound) throws SOAPException {
            return getMessage().readAsSOAPMessage(packet, inbound);
        }

        @Override
        public <T> T readPayloadAsJAXB(Unmarshaller unmarshaller) throws JAXBException {
            return getMessage().readPayloadAsJAXB(unmarshaller);
        }

        @Override
        public <T> T readPayloadAsJAXB(Bridge<T> bridge) throws JAXBException {
            return getMessage().readPayloadAsJAXB(bridge);
        }

        @Override
        public XMLStreamReader readPayload() throws XMLStreamException {
            return getMessage().readPayload();
        }

        @Override
        public void writePayloadTo(XMLStreamWriter sw) throws XMLStreamException {
            getMessage().writePayloadTo(sw);
        }

        @Override
        public void writeTo(XMLStreamWriter sw) throws XMLStreamException {
            getMessage().writeTo(sw);
        }

        @Override
        public void writeTo(ContentHandler contentHandler, ErrorHandler errorHandler) throws SAXException {
            getMessage().writeTo(contentHandler, errorHandler);
        }

        @Override
        public Message copy() {
            return getMessage().copy().copyFrom(getMessage());
        }

        @Override
        protected void writePayloadTo(ContentHandler contentHandler, ErrorHandler errorHandler, boolean fragment) throws SAXException {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean isOneWay(@NotNull WSDLPort port) {
            return false;
        }

        @Override
        public @NotNull AttachmentSet getAttachments() {
            return getMessage().getAttachments();
        }

    }

    private static class FaultMessage extends EmptyMessageImpl {

        public FaultMessage(SOAPVersion version) {
            super(version);
        }

        @Override
        public boolean isFault() {
            return true;
        }
    }

    
    /**
     * Don't know about this content. It's conent-type is NOT the XML types
     * we recognize(text/xml, application/xml, multipart/related;text/xml etc).
     *
     * This could be used to represent image/jpeg etc
     */
    public static class UnknownContent extends AbstractMessageImpl implements MessageDataSource {
        private final DataSource ds;
        private final HeaderList headerList;
        
        public UnknownContent(final String ct, final InputStream in) {
            this(createDataSource(ct,in));
        }
        
        public UnknownContent(DataSource ds) {
            super(SOAPVersion.SOAP_11);
            this.ds = ds;
            this.headerList = new HeaderList(SOAPVersion.SOAP_11);
        }

        /*
         * Copy constructor.
         */
        private UnknownContent(UnknownContent that) {
            super(that.soapVersion);
            this.ds = that.ds;
            this.headerList = HeaderList.copy(that.headerList);
            this.copyFrom(that);
        }

        @Override
        public boolean hasUnconsumedDataSource() {
            return true;
        }

        @Override
        public DataSource getDataSource() {
            assert ds != null;
            return ds;
        }

        @Override
        protected void writePayloadTo(ContentHandler contentHandler,
                                      ErrorHandler errorHandler, boolean fragment) throws SAXException {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean hasHeaders() {
            return false;
        }
        
        @Override
        public boolean isFault() {
            return false;
        }

        @Override
        public MessageHeaders getHeaders() {
            return headerList;
        }

        @Override
        public String getPayloadLocalPart() {
            throw new UnsupportedOperationException();
        }

        @Override
        public String getPayloadNamespaceURI() {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean hasPayload() {
            return false;
        }

        @Override
        public Source readPayloadAsSource() {
            return null;
        }

        @Override
        public XMLStreamReader readPayload() throws XMLStreamException {
            throw new WebServiceException("There isn't XML payload. Shouldn't come here.");
        }

        @Override
        public void writePayloadTo(XMLStreamWriter sw) throws XMLStreamException {
            // No XML. Nothing to do
        }

        @Override
        public Message copy() {
            return new UnknownContent(this).copyFrom(this);
        }

    }

    public static DataSource getDataSource(Message msg, WSFeatureList f) {
        if (msg == null)
            return null;
        if (msg instanceof MessageDataSource) {
            return ((MessageDataSource)msg).getDataSource();
        } else {
            AttachmentSet atts = msg.getAttachments();
            if (atts != null && !atts.isEmpty()) {
                final ByteArrayBuffer bos = new ByteArrayBuffer();
                try {
                    Codec codec = new XMLHTTPBindingCodec(f);
                    Packet packet = new Packet(msg);
                    com.sun.xml.ws.api.pipe.ContentType ct = codec.getStaticContentType(packet);
                    codec.encode(packet, bos);
                    return createDataSource(ct.getContentType(), bos.newInputStream());
                } catch(IOException ioe) {
                    throw new WebServiceException(ioe);
                }
                
            } else {
                final ByteArrayBuffer bos = new ByteArrayBuffer();
                XMLStreamWriter writer = XMLStreamWriterFactory.create(bos);
                try {
                    msg.writePayloadTo(writer);
                    writer.flush();
                } catch (XMLStreamException e) {
                    throw new WebServiceException(e);
                }
                return XMLMessage.createDataSource("text/xml", bos.newInputStream());
            }       
        }
    }
    
    public static DataSource createDataSource(final String contentType, final InputStream is) {
        return new XmlDataSource(contentType, is);
    }

    private static class XmlDataSource implements DataSource {
        private final String contentType;
        private final InputStream is;
        private boolean consumed;

        XmlDataSource(String contentType, final InputStream is) {
            this.contentType = contentType;
            this.is = is;
        }

        public boolean consumed() {
            return consumed;
        }

        @Override
        public InputStream getInputStream() {
            consumed = !consumed;
            return is;
        }

        @Override
        public OutputStream getOutputStream() {
            return null;
        }

        @Override
        public String getContentType() {
            return contentType;
        }

        @Override
        public String getName() {
            return "";
        }
    }
}
