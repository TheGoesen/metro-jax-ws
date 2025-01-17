/*
 * Copyright (c) 1997, 2022 Oracle and/or its affiliates. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0, which is available at
 * http://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package com.sun.xml.ws.db.toplink;

import java.awt.Image;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.ParameterizedType;
import java.util.Map;

import jakarta.activation.DataHandler;
import jakarta.mail.internet.MimeMultipart;
import jakarta.xml.bind.JAXBElement;
import jakarta.xml.bind.JAXBException;
import jakarta.xml.bind.attachment.AttachmentMarshaller;
import jakarta.xml.bind.attachment.AttachmentUnmarshaller;
import javax.xml.namespace.NamespaceContext;
import javax.xml.namespace.QName;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.XMLStreamWriter;
import javax.xml.transform.Result;
import javax.xml.transform.Source;
import javax.xml.transform.dom.DOMResult;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.sax.SAXResult;
import javax.xml.transform.sax.SAXSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;
import jakarta.xml.ws.WebServiceException;

import org.eclipse.persistence.jaxb.JAXBContext;
import org.eclipse.persistence.jaxb.JAXBMarshaller;
import org.eclipse.persistence.jaxb.JAXBTypeElement;
import org.eclipse.persistence.jaxb.JAXBUnmarshaller;
import org.eclipse.persistence.jaxb.TypeMappingInfo;
import org.eclipse.persistence.oxm.record.XMLStreamWriterRecord;

import org.eclipse.persistence.internal.oxm.record.XMLStreamReaderInputSource;
import org.eclipse.persistence.internal.oxm.record.XMLStreamReaderReader;
import org.jvnet.staxex.Base64Data;
import org.jvnet.staxex.XMLStreamReaderEx;
import org.jvnet.staxex.XMLStreamWriterEx;

import org.w3c.dom.Node;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;


import com.sun.xml.ws.spi.db.BindingContext;
import com.sun.xml.ws.spi.db.XMLBridge;
import com.sun.xml.ws.spi.db.TypeInfo;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

public class JAXBBond<T> implements XMLBridge<T> {

    JAXBContextWrapper parent;
    TypeInfo typeInfo;
    TypeMappingInfo mappingInfo;
    boolean isParameterizedType = false;

    public JAXBBond(JAXBContextWrapper p, TypeInfo ti) {
        this.parent = p;
        this.typeInfo = ti;
        if (parent.infoMap != null) {
            mappingInfo = parent.infoMap.get(ti);
        }
        if (mappingInfo != null) {
            isParameterizedType = (mappingInfo.getType() instanceof ParameterizedType);
        }
    }

    @Override
    public BindingContext context() {
        return parent;
    }

    @Override
    public TypeInfo getTypeInfo() {
        return typeInfo;
    }

    @Override
    public void marshal(T object, XMLStreamWriter output,
            AttachmentMarshaller am) throws JAXBException {
        JAXBMarshaller marshaller = null;
        try {
            marshaller = parent.mpool.allocate();
            marshaller.setAttachmentMarshaller(am);
            marshaller.setProperty(jakarta.xml.bind.Marshaller.JAXB_FRAGMENT,
                    true);
            boolean isEx = (output instanceof XMLStreamWriterEx);
            if (mappingInfo != null) {
                if (isParameterizedType) {
                    JAXBTypeElement jte = new JAXBTypeElement(
                            mappingInfo.getXmlTagName(), object,
                            (ParameterizedType) mappingInfo.getType());
                    if (isEx) {
                        marshaller.marshal(jte, new NewStreamWriterRecord(
                                (XMLStreamWriterEx) output), mappingInfo);
                    } else {
                        marshaller.marshal(jte, output, mappingInfo);
                    }
                } else {
                    JAXBElement<T> elt = new JAXBElement<>(
                            mappingInfo.getXmlTagName(),
                            (Class<T>) typeInfo.type, object);
                    if (isEx) {
                        marshaller.marshal(elt, new NewStreamWriterRecord(
                                (XMLStreamWriterEx) output), mappingInfo);
                    } else {
                        marshaller.marshal(elt, output, mappingInfo);
                    }
                }
            } else {
                if (isEx) {
                    marshaller.marshal(object, new NewStreamWriterRecord(
                            (XMLStreamWriterEx) output));
                } else {
                    marshaller.marshal(object, output);
                }
            }
        } finally {
            if (marshaller != null) {
                marshaller.setAttachmentMarshaller(null);
                parent.mpool.replace(marshaller);
            }
        }
    }

    // TODO NamespaceContext nsContext
    @Override
    public void marshal(T object, OutputStream output,
            NamespaceContext nsContext, AttachmentMarshaller am)
            throws JAXBException {
        JAXBMarshaller marshaller = null;

        try {
            marshaller = parent.mpool.allocate();
            marshaller.setAttachmentMarshaller(am);
            marshaller.setProperty(jakarta.xml.bind.Marshaller.JAXB_FRAGMENT,
                    true);
            if (mappingInfo != null) {
                if (isParameterizedType) {
                    JAXBTypeElement jte = new JAXBTypeElement(
                            mappingInfo.getXmlTagName(), object,
                            (ParameterizedType) mappingInfo.getType());
                    marshaller.marshal(jte, new StreamResult(output),
                            mappingInfo);
                } else {
                    JAXBElement<T> elt = new JAXBElement<>(
                            mappingInfo.getXmlTagName(),
                            (Class<T>) mappingInfo.getType(), object);
                    // marshaller.marshal(elt, output);
                    // GAG missing
                    marshaller.marshal(elt, new StreamResult(output),
                            mappingInfo);
                }
            } else {
                marshaller.marshal(object, output);
            }
        } finally {
            if (marshaller != null) {
                marshaller.setAttachmentMarshaller(null);
                parent.mpool.replace(marshaller);
            }
        }
    }

    @Override
    public void marshal(T object, Node output) throws JAXBException {
        JAXBMarshaller marshaller = null;
        try {
            marshaller = parent.mpool.allocate();
            // marshaller.setAttachmentMarshaller(am);
            marshaller.setProperty(jakarta.xml.bind.Marshaller.JAXB_FRAGMENT,
                    true);
            if (mappingInfo != null) {
                if (isParameterizedType) {
                    JAXBTypeElement jte = new JAXBTypeElement(
                            mappingInfo.getXmlTagName(), object,
                            (ParameterizedType) mappingInfo.getType());
                    marshaller.marshal(jte, new DOMResult(output), mappingInfo);
                } else {
                    JAXBElement<T> elt = new JAXBElement<>(
                            mappingInfo.getXmlTagName(),
                            (Class<T>) mappingInfo.getType(), object);
                    // marshaller.marshal(elt, output);
                    marshaller.marshal(elt, new DOMResult(output), mappingInfo);
                }
            } else {
                marshaller.marshal(object, output);
            }
        } finally {
            if (marshaller != null) {
                marshaller.setAttachmentMarshaller(null);
                parent.mpool.replace(marshaller);
            }
        }
    }

    @Override
    public void marshal(T object, ContentHandler contentHandler,
            AttachmentMarshaller am) throws JAXBException {
        JAXBMarshaller marshaller = null;
        try {
            marshaller = parent.mpool.allocate();
            marshaller.setAttachmentMarshaller(am);
            marshaller.setProperty(jakarta.xml.bind.Marshaller.JAXB_FRAGMENT,
                    true);
            if (mappingInfo != null) {
                if (isParameterizedType) {
                    JAXBTypeElement jte = new JAXBTypeElement(
                            mappingInfo.getXmlTagName(), object,
                            (ParameterizedType) mappingInfo.getType());
                    marshaller.marshal(jte, new SAXResult(contentHandler),
                            mappingInfo);
                } else {
                    JAXBElement<T> elt = new JAXBElement<>(
                            mappingInfo.getXmlTagName(),
                            (Class<T>) mappingInfo.getType(), object);
                    // marshaller.marshal(elt, contentHandler);

                    // GAG missing
                    marshaller.marshal(elt, new SAXResult(contentHandler),
                            mappingInfo);

                    // marshaller.marshal(elt, contentHandler, mappingInfo);
                }
            } else {
                marshaller.marshal(object, contentHandler);
            }
        } finally {
            if (marshaller != null) {
                marshaller.setAttachmentMarshaller(null);
                parent.mpool.replace(marshaller);
            }
        }
    }

    @Override
    public void marshal(T object, Result result) throws JAXBException {
        JAXBMarshaller marshaller = null;
        try {
            marshaller = parent.mpool.allocate();
            marshaller.setAttachmentMarshaller(null);
            marshaller.setProperty(jakarta.xml.bind.Marshaller.JAXB_FRAGMENT,
                    true);
            if (mappingInfo != null) {
                if (isParameterizedType) {
                    JAXBTypeElement jte = new JAXBTypeElement(
                            mappingInfo.getXmlTagName(), object,
                            (ParameterizedType) mappingInfo.getType());
                    marshaller.marshal(jte, result, mappingInfo);
                } else {
                    JAXBElement<T> elt = new JAXBElement<>(
                            mappingInfo.getXmlTagName(),
                            (Class<T>) mappingInfo.getType(), object);
                    // marshaller.marshal(elt, result);
                    marshaller.marshal(elt, result, mappingInfo);
                }
            } else {
                TypeMappingInfo tmi = null;
                if (object instanceof JAXBElement) {
                    QName q = ((JAXBElement) object).getName();
                    JAXBContext ctx = (JAXBContext) parent.getJAXBContext();

                    Map<TypeMappingInfo, QName> mtq = ctx
                            .getTypeMappingInfoToSchemaType();
                    for (Map.Entry<TypeMappingInfo, QName> es : mtq.entrySet()) {
                        if (q.equals(es.getValue())) {
                            tmi = es.getKey();
                            break;
                        }

                    }
                }
                if (tmi != null) {
                    marshaller.marshal(object, result, tmi);
                } else {
                    marshaller.marshal(object, result);
                }
            }
        } finally {
            if (marshaller != null) {
                marshaller.setAttachmentMarshaller(null);
                parent.mpool.replace(marshaller);
            }
        }
    }

    // This is used in RPC
    @Override
    public T unmarshal(XMLStreamReader in, AttachmentUnmarshaller au)
            throws JAXBException {
        JAXBUnmarshaller unmarshaller = null;
        try {
            QName tagName = null;
            if (in.getEventType() == XMLStreamConstants.START_ELEMENT) {
                tagName = in.getName();
            }

            unmarshaller = parent.upool.allocate();
            unmarshaller.setAttachmentUnmarshaller(au);

            Object o;
            if (in instanceof XMLStreamReaderEx) {
                CustomXMLStreamReaderReader cr = new CustomXMLStreamReaderReader();
                XMLStreamReaderInputSource is = new XMLStreamReaderInputSource(
                        in);
                SAXSource saxSource = new SAXSource(cr, is);
                o = ((mappingInfo != null) ? unmarshaller.unmarshal(saxSource,
                        mappingInfo) : unmarshaller.unmarshal(saxSource));
            } else {
                o = ((mappingInfo != null) ? unmarshaller.unmarshal(in,
                        mappingInfo) : unmarshaller.unmarshal(in));
            }

            if (o instanceof JAXBElement) {
                o = ((JAXBElement) o).getValue();
            }
            // TODO recycle to pool

            // Workaround for Eclipselink JAXB not consuming END_ELEMENT.
            try {
                if (in.getEventType() == XMLStreamConstants.END_ELEMENT && in.getName().equals(tagName)) {
                    in.next();
                }
            } catch (XMLStreamException e) {
                throw new WebServiceException(e);
            }
            return (T) o;
        } finally {
            if (unmarshaller != null) {
                unmarshaller.setAttachmentUnmarshaller(null);
                parent.upool.replace(unmarshaller);
            }
        }
    }

    @Override
    public T unmarshal(Source in, AttachmentUnmarshaller au)
            throws JAXBException {
        JAXBUnmarshaller unmarshaller = null;
        try {
            unmarshaller = parent.upool.allocate();
            unmarshaller.setAttachmentUnmarshaller(au);
            Object o;
            if (mappingInfo != null) {
                o = unmarshaller.unmarshal(in, mappingInfo);
            } else {
                o = unmarshaller.unmarshal(in);
            }
            if (o instanceof JAXBElement) {
                o = ((JAXBElement) o).getValue();
            }
            // TODO recycle to pool
            return (T) o;
        } finally {
            if (unmarshaller != null) {
                unmarshaller.setAttachmentUnmarshaller(null);
                parent.upool.replace(unmarshaller);
            }
        }
    }

    @Override
    public T unmarshal(InputStream in) throws JAXBException {
        JAXBUnmarshaller unmarshaller = null;
        try {
            unmarshaller = parent.upool.allocate();
            // GAG missing
            Object o = ((mappingInfo != null) ? unmarshaller.unmarshal(
                    new StreamSource(in), mappingInfo) : unmarshaller
                    .unmarshal(in));
            // Object o = unmarshaller.unmarshal(in);
            if (o instanceof JAXBElement) {
                o = ((JAXBElement) o).getValue();
            }
            // TODO recycle to pool
            return (T) o;
        } finally {
            if (unmarshaller != null) {
                unmarshaller.setAttachmentUnmarshaller(null);
                parent.upool.replace(unmarshaller);
            }
        }
    }

    @Override
    public T unmarshal(Node in, AttachmentUnmarshaller au) throws JAXBException {
        JAXBUnmarshaller unmarshaller = null;
        try {
            unmarshaller = parent.upool.allocate();
            unmarshaller.setAttachmentUnmarshaller(au);
            Object o = ((mappingInfo != null) ? unmarshaller.unmarshal(
                    new DOMSource(in), mappingInfo) : unmarshaller
                    .unmarshal(in));
            // Object o = unmarshaller.unmarshal(in);
            if (o instanceof JAXBElement) {
                o = ((JAXBElement) o).getValue();
            }
            // TODO recycle to pool
            return (T) o;
        } finally {
            if (unmarshaller != null) {
                unmarshaller.setAttachmentUnmarshaller(null);
                parent.upool.replace(unmarshaller);
            }
        }
    }

    /**
     * The combination of jaxws-ri and eclipselink-jaxb may perform better with
     * XMLStreamWriter
     */
    @Override
    public boolean supportOutputStream() {
        return false;
    }

    public static class CustomXMLStreamReaderReader extends XMLStreamReaderReader {

        @Override
        protected void parseCharactersEvent(XMLStreamReader xmlStreamReader)
                throws SAXException {
            // super.parseCharactersEvent(xmlStreamReader);
            XMLStreamReaderEx xrex = (XMLStreamReaderEx) xmlStreamReader;
            try {
                CharSequence characters = xrex.getPCDATA();
                if (characters instanceof Base64Data) {
                    contentHandler.characters(characters);
                } else {
                    String value = characters.toString();
                    contentHandler.characters(value.toCharArray(), 0,
                            value.length());
                }
            } catch (Exception ex) {
                throw new WebServiceException(ex);
            }
        }

        @Override
        public Object getValue(CharSequence characters, Class<?> dataType) {
            if (Base64Data.class.equals(characters.getClass())) {
                if (DataHandler.class == dataType || Image.class == dataType
                        || Source.class == dataType
                        || MimeMultipart.class == dataType) {
                    return ((Base64Data) characters).getDataHandler();
                } else if (byte[].class == dataType) {
                    return ((Base64Data) characters).getExact();
                }
            }
            return null;
        }
    }

    public static class NewStreamWriterRecord extends XMLStreamWriterRecord {

        private transient XMLStreamWriterEx xsw;

        public NewStreamWriterRecord(XMLStreamWriterEx xsw) {
            super(xsw);
            this.xsw = xsw;
        }

        @Override
        public void characters(QName schemaType, Object value, String mimeType, boolean isCData) {
            if (mimeType != null) {
                if (value instanceof DataHandler) {
                    try {
                        xsw.writeBinary((DataHandler) value);
                    } catch (XMLStreamException e) {
                        throw new WebServiceException(e);
                    }
                    return;
                } else if (value instanceof byte[]) {
                    try {
                        byte[] b = (byte[])value;
                        xsw.writeBinary(b,0,b.length, null);
                    } catch (XMLStreamException e) {
                        throw new WebServiceException(e);
                    }
                    return;
                }
            }
            super.characters(schemaType, value, mimeType, isCData);
        }
 
        private void readObject(ObjectInputStream aInputStream) throws ClassNotFoundException, IOException {
            throw new UnsupportedOperationException();
        }
        
        private void writeObject(ObjectOutputStream oos) throws IOException {
            throw new UnsupportedOperationException();
        }    
    }
}
