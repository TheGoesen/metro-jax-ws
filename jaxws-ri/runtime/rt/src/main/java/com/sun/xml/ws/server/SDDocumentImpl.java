/*
 * Copyright (c) 1997, 2022 Oracle and/or its affiliates. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0, which is available at
 * http://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package com.sun.xml.ws.server;

import com.sun.istack.Nullable;
import com.sun.xml.ws.api.server.*;
import com.sun.xml.ws.api.streaming.XMLStreamWriterFactory;
import com.sun.xml.ws.streaming.XMLStreamReaderUtil;
import com.sun.xml.ws.wsdl.SDDocumentResolver;
import com.sun.xml.ws.util.RuntimeVersion;
import org.jvnet.staxex.util.XMLStreamReaderToXMLStreamWriter;
import com.sun.xml.ws.wsdl.parser.ParserUtil;
import com.sun.xml.ws.wsdl.parser.WSDLConstants;
import com.sun.xml.ws.wsdl.writer.DocumentLocationResolver;
import com.sun.xml.ws.wsdl.writer.WSDLPatcher;

import javax.xml.namespace.QName;
import javax.xml.stream.*;
import jakarta.xml.ws.WebServiceException;
import java.io.IOException;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * {@link SDDocument} implmentation.
 *
 * <p>
 * This extends from {@link SDDocumentSource} so that
 * JAX-WS server runtime code can use {@link SDDocument}
 * as {@link SDDocumentSource}.
 *
 * @author Kohsuke Kawaguchi
 * @author Jitendra Kotamraju
 */
public class SDDocumentImpl extends SDDocumentSource implements SDDocument {

    private static final String NS_XSD = "http://www.w3.org/2001/XMLSchema";
    private static final QName SCHEMA_INCLUDE_QNAME = new QName(NS_XSD, "include");
    private static final QName SCHEMA_IMPORT_QNAME = new QName(NS_XSD, "import");
    private static final QName SCHEMA_REDEFINE_QNAME = new QName(NS_XSD, "redefine");
    private static final String VERSION_COMMENT =
        " Published by JAX-WS RI (https://github.com/eclipse-ee4j/metro-jax-ws). RI's version is "+RuntimeVersion.VERSION+". ";

    private final QName rootName;
    private final SDDocumentSource source;

    /**
     * Set when {@link ServiceDefinitionImpl} is constructed.
     */
    @Nullable List<SDDocumentFilter> filters;
    @Nullable SDDocumentResolver sddocResolver;


    /**
     * The original system ID of this document.
     *
     * When this document contains relative references to other resources,
     * this field is used to find which {@link com.sun.xml.ws.server.SDDocumentImpl} it refers to.
     *
     * Must not be null.
     */
    private final URL url;
    private final Set<String> imports;

    /**
     * Creates {@link SDDocument} from {@link SDDocumentSource}.
     * @param src WSDL document infoset
     * @param serviceName wsdl:service name
     * @param portTypeName
     *      The information about the port of {@link WSEndpoint} to which this document is built for.
     *      These values are used to determine which document is the concrete and abstract WSDLs
     *      for this endpoint.
     *
     * @return null
     *      Always non-null.
     */
    public static SDDocumentImpl create(SDDocumentSource src, QName serviceName, QName portTypeName) {
        URL systemId = src.getSystemId();

        try {
            // RuntimeWSDLParser parser = new RuntimeWSDLParser(null);
            XMLStreamReader reader = src.read();
            try {
                XMLStreamReaderUtil.nextElementContent(reader);

                QName rootName = reader.getName();
                if(rootName.equals(WSDLConstants.QNAME_SCHEMA)) {
                    String tns = ParserUtil.getMandatoryNonEmptyAttribute(reader, WSDLConstants.ATTR_TNS);
                    Set<String> importedDocs = new HashSet<>();
                    while (XMLStreamReaderUtil.nextContent(reader) != XMLStreamConstants.END_DOCUMENT) {
                         if (reader.getEventType() != XMLStreamConstants.START_ELEMENT)
                            continue;
                        QName name = reader.getName();
                        if (SCHEMA_INCLUDE_QNAME.equals(name) || SCHEMA_IMPORT_QNAME.equals(name) ||
                                SCHEMA_REDEFINE_QNAME.equals(name)) {
                            String importedDoc = reader.getAttributeValue(null, "schemaLocation");
                            if (importedDoc != null) {
                                importedDocs.add(new URL(src.getSystemId(), importedDoc).toString());
                            }
                        }
                    }
                    return new SchemaImpl(rootName,systemId,src,tns,importedDocs);
                } else if (rootName.equals(WSDLConstants.QNAME_DEFINITIONS)) {
                    String tns = ParserUtil.getMandatoryNonEmptyAttribute(reader, WSDLConstants.ATTR_TNS);

                    boolean hasPortType = false;
                    boolean hasService = false;
                    Set<String> importedDocs = new HashSet<>();
                    Set<QName> allServices = new HashSet<>();

                    // if WSDL, parse more
                    while (XMLStreamReaderUtil.nextContent(reader) != XMLStreamConstants.END_DOCUMENT) {
                         if(reader.getEventType() != XMLStreamConstants.START_ELEMENT)
                            continue;

                        QName name = reader.getName();
                        if (WSDLConstants.QNAME_PORT_TYPE.equals(name)) {
                            String pn = ParserUtil.getMandatoryNonEmptyAttribute(reader, WSDLConstants.ATTR_NAME);
                            if (portTypeName != null) {
                                if(portTypeName.getLocalPart().equals(pn)&&portTypeName.getNamespaceURI().equals(tns)) {
                                    hasPortType = true;
                                }
                            }
                        } else if (WSDLConstants.QNAME_SERVICE.equals(name)) {
                            String sn = ParserUtil.getMandatoryNonEmptyAttribute(reader, WSDLConstants.ATTR_NAME);
                            QName sqn = new QName(tns,sn);
                            allServices.add(sqn);
                            if(serviceName.equals(sqn)) {
                                hasService = true;
                            }
                        } else if (WSDLConstants.QNAME_IMPORT.equals(name)) {
                            String importedDoc = reader.getAttributeValue(null, "location");
                            if (importedDoc != null) {
                                importedDocs.add(new URL(src.getSystemId(), importedDoc).toString());
                            }
                        } else if (SCHEMA_INCLUDE_QNAME.equals(name) || SCHEMA_IMPORT_QNAME.equals(name) ||
                                SCHEMA_REDEFINE_QNAME.equals(name)) {
                            String importedDoc = reader.getAttributeValue(null, "schemaLocation");
                            if (importedDoc != null) {
                                importedDocs.add(new URL(src.getSystemId(), importedDoc).toString());
                            }
                        }
                    }
                    return new WSDLImpl(
                        rootName,systemId,src,tns,hasPortType,hasService,importedDocs,allServices);
                } else {
                    return new SDDocumentImpl(rootName,systemId,src);
                }
            } finally {
                reader.close();
            }
        } catch (WebServiceException | XMLStreamException | IOException e) {
            throw new ServerRtException("runtime.parser.wsdl", systemId,e);
        }
    }

    protected SDDocumentImpl(QName rootName, URL url, SDDocumentSource source) {
        this(rootName, url, source, new HashSet<>());
    }

    protected SDDocumentImpl(QName rootName, URL url, SDDocumentSource source, Set<String> imports) {
        if (url == null) {
            throw new IllegalArgumentException("Cannot construct SDDocument with null URL.");
        }
        this.rootName = rootName;
        this.source = source;
        this.url = url;
        this.imports = imports;
    }

    void setFilters(List<SDDocumentFilter> filters) {
        this.filters = filters;
    }

    void setResolver(SDDocumentResolver sddocResolver) {
        this.sddocResolver = sddocResolver;
    }

    @Override
    public QName getRootName() {
        return rootName;
    }

    @Override
    public boolean isWSDL() {
        return false;
    }

    @Override
    public boolean isSchema() {
        return false;
    }

    @Override
    public URL getURL() {
        return url;
    }

    @Override
    public XMLStreamReader read(XMLInputFactory xif) throws IOException, XMLStreamException {
        return source.read(xif);
    }

    @Override
    public XMLStreamReader read() throws IOException, XMLStreamException {
        return source.read();
    }

    @Override
    public URL getSystemId() {
        return url;
    }

    @Override
    public Set<String> getImports() {
        return imports;
    }

    public void writeTo(OutputStream os) throws IOException {
        XMLStreamWriter w = null;
        try {
            //generate the WSDL with utf-8 encoding and XML version 1.0
            w = XMLStreamWriterFactory.create(os, "UTF-8");
            w.writeStartDocument("UTF-8", "1.0");
            new XMLStreamReaderToXMLStreamWriter().bridge(source.read(), w);
            w.writeEndDocument();
        } catch (XMLStreamException e) {
            IOException ioe = new IOException(e.getMessage());
            ioe.initCause(e);
            throw ioe;
        } finally {
            try {
                if (w != null)
                    w.close();
            } catch (XMLStreamException e) {
                IOException ioe = new IOException(e.getMessage());
                ioe.initCause(e);
                throw ioe;
            }
        }
    }


    @Override
    public void writeTo(PortAddressResolver portAddressResolver, DocumentAddressResolver resolver, OutputStream os) throws IOException {
        XMLStreamWriter w = null;
        try {
            //generate the WSDL with utf-8 encoding and XML version 1.0
            w = XMLStreamWriterFactory.create(os, "UTF-8");
            w.writeStartDocument("UTF-8", "1.0");
            writeTo(portAddressResolver,resolver,w);
            w.writeEndDocument();
        } catch (XMLStreamException e) {
            IOException ioe = new IOException(e.getMessage());
            ioe.initCause(e);
            throw ioe;
        } finally {
            try {
                if (w != null)
                    w.close();
            } catch (XMLStreamException e) {
                IOException ioe = new IOException(e.getMessage());
                ioe.initCause(e);
                throw ioe;
            }
        }
    }

    @Override
    public void writeTo(PortAddressResolver portAddressResolver, DocumentAddressResolver resolver, XMLStreamWriter out) throws XMLStreamException, IOException {
        if (filters != null) {
            for (SDDocumentFilter f : filters) {
                out = f.filter(this,out);
            }
        }

        XMLStreamReader xsr = source.read();
        try {
            out.writeComment(VERSION_COMMENT);
            new WSDLPatcher(portAddressResolver, new DocumentLocationResolverImpl(resolver)).bridge(xsr,out);
        } finally {
            xsr.close();
        }
    }


    /**
     * {@link SDDocument.Schema} implementation.
     *
     * @author Kohsuke Kawaguchi
     */
    private static final class SchemaImpl extends SDDocumentImpl implements SDDocument.Schema {
        private final String targetNamespace;

        public SchemaImpl(QName rootName, URL url, SDDocumentSource source, String targetNamespace,
                          Set<String> imports) {
            super(rootName, url, source, imports);
            this.targetNamespace = targetNamespace;
        }

        @Override
        public String getTargetNamespace() {
            return targetNamespace;
        }

        @Override
        public boolean isSchema() {
            return true;
        }
    }


    private static final class WSDLImpl extends SDDocumentImpl implements SDDocument.WSDL {
        private final String targetNamespace;
        private final boolean hasPortType;
        private final boolean hasService;
        private final Set<QName> allServices;

        public WSDLImpl(QName rootName, URL url, SDDocumentSource source, String targetNamespace, boolean hasPortType,
                        boolean hasService, Set<String> imports,Set<QName> allServices) {
            super(rootName, url, source, imports);
            this.targetNamespace = targetNamespace;
            this.hasPortType = hasPortType;
            this.hasService = hasService;
            this.allServices = allServices;
        }

        @Override
        public String getTargetNamespace() {
            return targetNamespace;
        }

        @Override
        public boolean hasPortType() {
            return hasPortType;
        }

        @Override
        public boolean hasService() {
            return hasService;
        }

        @Override
        public Set<QName> getAllServices() {
            return allServices;
        }

        @Override
        public boolean isWSDL() {
            return true;
        }
    }

    private class DocumentLocationResolverImpl implements DocumentLocationResolver {
        private DocumentAddressResolver delegate;

        DocumentLocationResolverImpl(DocumentAddressResolver delegate) {
            this.delegate = delegate;
        }

        @Override
        public String getLocationFor(String namespaceURI, String systemId) {
            if (sddocResolver == null) {
                return systemId;
            }
            try {
                URL ref = new URL(getURL(), systemId);
                SDDocument refDoc = sddocResolver.resolve(ref.toExternalForm());
                if (refDoc == null)
                    return systemId;  // not something we know. just leave it as is.

                return delegate.getRelativeAddressFor(SDDocumentImpl.this, refDoc);
            } catch (MalformedURLException mue) {
                return null;
            }
        }
    }

}
