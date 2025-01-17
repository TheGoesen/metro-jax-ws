/*
 * Copyright (c) 1997, 2022 Oracle and/or its affiliates. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0, which is available at
 * http://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package com.sun.xml.ws.message.saaj;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.UUID;

import jakarta.activation.DataHandler;
import jakarta.xml.bind.attachment.AttachmentMarshaller;
import javax.xml.namespace.QName;
import javax.xml.parsers.DocumentBuilderFactory;
import jakarta.xml.soap.AttachmentPart;
import jakarta.xml.soap.MessageFactory;
import jakarta.xml.soap.SOAPException;
import jakarta.xml.soap.SOAPBody;
import jakarta.xml.soap.SOAPBodyElement;
import jakarta.xml.soap.SOAPMessage;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import javax.xml.transform.Source;
import javax.xml.transform.stream.StreamSource;
import jakarta.xml.ws.soap.MTOMFeature;

import com.sun.xml.ws.api.message.saaj.SaajStaxWriter;
import junit.framework.TestCase;

import org.jvnet.staxex.NamespaceContextEx;
import org.jvnet.staxex.XMLStreamWriterEx;

import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

import org.xml.sax.InputSource;

import com.oracle.webservices.api.message.ContentType;
import com.oracle.webservices.api.message.MessageContext;
import com.oracle.webservices.api.message.MessageContextFactory;
import com.sun.xml.ws.api.SOAPVersion;
import com.sun.xml.ws.api.addressing.AddressingVersion;
import com.sun.xml.ws.api.message.AddressingUtils;
import com.sun.xml.ws.api.message.Attachment;
import com.sun.xml.ws.api.message.Header;
import com.sun.xml.ws.api.message.Message;
import com.sun.xml.ws.api.message.Packet;
import com.sun.xml.ws.api.streaming.XMLStreamWriterFactory;
import com.sun.xml.ws.developer.StreamingDataHandler;
import com.sun.xml.ws.encoding.MIMEPartStreamingDataHandler;
import com.sun.xml.ws.message.StringHeader;

/**
 * @author Rama Pulavarthi
 */
public class SAAJMessageTest extends TestCase {
    String MESSAGE  = 	"<S:Envelope xmlns:S=\"http://schemas.xmlsoap.org/soap/envelope/\">"+
            "<S:Header>" +
            "<wsa:Action xmlns:wsa=\"http://www.w3.org/2005/08/addressing\">http://example.com/addNumbers</wsa:Action>" +
            "</S:Header>" +
            "<S:Body>" +
            "<addNumbers xmlns=\"http://example.com/\">" +
            "<number1>10</number1>" +
            "<number2>10</number2>" +
            "</addNumbers>" +
            "</S:Body></S:Envelope>";

    String FAULT_MESSAGE = "<S:Envelope xmlns:S='http://schemas.xmlsoap.org/soap/envelope/'>"+
            "<S:Header>" +
            "<wsa:Action xmlns:wsa='http://www.w3.org/2005/08/addressing'>http://example.com/addNumbers</wsa:Action>" +
            "</S:Header>" +
            "<S:Body><S:Fault>" +
            "<faultCode>S:Client</faultCode>" +
            "<faultString>Fault Test</faultString>" +
            "<detail xmlns:ns1='urn:fault'><ns1:entry></ns1:entry></detail>" +
            "</S:Fault></S:Body></S:Envelope>";

    public void test1() throws Exception {
        MessageFactory factory = MessageFactory.newInstance();
        SOAPMessage message = factory.createMessage();
        Source src = new StreamSource(new ByteArrayInputStream(MESSAGE.getBytes()));
        message.getSOAPPart().setContent(src);

        SAAJMessage saajMsg = new SAAJMessage(message);
        assertEquals("addNumbers",saajMsg.getPayloadLocalPart());
        assertEquals("http://example.com/addNumbers",AddressingUtils.getAction(saajMsg.getHeaders(), AddressingVersion.W3C, SOAPVersion.SOAP_11));
        Header header = new StringHeader(new QName("urn:foo","header1"),"test header  ");
        saajMsg.getHeaders().add(header);
        
        SOAPMessage newMsg = saajMsg.readAsSOAPMessage();
        newMsg.writeTo(System.out);
        SAAJMessage saajMsg2 = new SAAJMessage(newMsg);
        assertEquals(2,saajMsg2.getHeaders().asList().size());

        Message saajMsg3 = saajMsg2.copy();
        assertEquals("addNumbers",saajMsg3.getPayloadLocalPart());
        assertEquals("http://example.com/addNumbers",AddressingUtils.getAction(saajMsg3.getHeaders(), AddressingVersion.W3C, SOAPVersion.SOAP_11));
        assertEquals(2,saajMsg2.getHeaders().asList().size());
        XMLStreamWriter writer = XMLStreamWriterFactory.create(System.out);
        saajMsg3.writeTo(writer);
        writer.close();

    }

    public void testWhiteSpaceCharacters() throws Exception {
        MessageFactory messageFactory = MessageFactory.newInstance();
        SOAPMessage message = messageFactory.createMessage();
        SOAPBody body = message.getSOAPBody();
        QName name = new QName("testString1");
        SOAPBodyElement bodyElement = body.addBodyElement(name);
        bodyElement.addTextNode("Hello World, ---\003\007\024---");

        name = new QName("testString2");
        bodyElement = body.addBodyElement(name);
        bodyElement.addTextNode("Hello \t\n\r World");

        SAAJMessage saajMsg = new SAAJMessage(message);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        XMLStreamWriter writer = XMLStreamWriterFactory.create(baos);
        saajMsg.writeTo(writer);
        writer.close();

        Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse( new InputSource( new StringReader( baos.toString() ) ) );
        NodeList nodeList = doc.getElementsByTagName("testString1");
        assertEquals(nodeList.item(0).getFirstChild().getNodeValue(), "Hello World, ---&#3;&#7;&#20;---");
        nodeList = doc.getElementsByTagName("testString2");
        assertEquals(nodeList.item(0).getFirstChild().getNodeValue(), "Hello \t\n\r World");
    }

    public void testFirstDetailEntryName() throws Exception {
        MessageFactory factory = MessageFactory.newInstance();
        SOAPMessage message = factory.createMessage();
        Source src = new StreamSource(new ByteArrayInputStream(FAULT_MESSAGE.getBytes()));
        message.getSOAPPart().setContent(src);

        SAAJMessage saajMsg = new SAAJMessage(message);
        QName exp = new QName("urn:fault", "entry");
        assertEquals(exp, saajMsg.getFirstDetailEntryName());
    }
    
//    private MTOMFeature mtomf = new MTOMFeature(true);
    private MessageContextFactory mcf = MessageContextFactory.createFactory(new MTOMFeature(true));   

    public void testMtomMessageReload() throws Exception {
        String testMtomMessageReload_01 = "multipart/related;type=\"application/xop+xml\";boundary=\"----=_Part_0_1145105632.1353005695468\";start=\"<cbe648b3-2055-413e-b8ed-877cdf0f2477>\";start-info=\"text/xml\"";
        String testMtomMessageReload_02 = "multipart/related;type=\"application/xop+xml\";boundary=\"----=_Part_0_1145105632.1353005695468\";start=\"<6e1e30fe-9874-43ff-a9d1-c02af3969f04>\";start-info=\"text/xml\"";
       
        {
            MessageContext m1 = mcf.createContext(getResource("testMtomMessageReload_01.msg"), testMtomMessageReload_01);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ContentType disiContentType = m1.writeTo(baos);  
            byte[] bytes = baos.toByteArray();
            String strMsg = new String(bytes);
            assertFalse(strMsg.startsWith("--null"));
        }
        {
            MessageContext m2 = mcf.createContext(getResource("testMtomMessageReload_02.msg"), testMtomMessageReload_02);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ContentType disiContentType = m2.writeTo(baos);  
            byte[] bytes = baos.toByteArray();
            String strMsg = new String(bytes);
            assertFalse(strMsg.startsWith("--null"));
        }
    }

    public void testMtomAttachment() throws Exception {
        String testMtomMessageReload_01 = "multipart/related;type=\"application/xop+xml\";boundary=\"----=_Part_0_1145105632.1353005695468\";start=\"<cbe648b3-2055-413e-b8ed-877cdf0f2477>\";start-info=\"text/xml\"";
        
        MessageContext m1 = mcf.createContext(getResource("testMtomMessageReload_01.msg"), testMtomMessageReload_01);
        Packet packet = (Packet) m1;
        Message message = packet.getInternalMessage();
        Iterator<Attachment> as = packet.getInternalMessage().getAttachments().iterator();
        Attachment att = null;
        int counter = 0;
        String cid1 = null;
        while (as.hasNext()) {
            att = as.next();
            cid1 = att.getContentId();
            counter++;
        }
        assertEquals(1, counter);
        
        //SAAJFactory:
        SOAPVersion soapVersion = packet.getMessage().getSOAPVersion();
        SOAPMessage msg = soapVersion.getMessageFactory().createMessage();
        SaajStaxWriterEx writer = new SaajStaxWriterEx(msg, soapVersion.nsUri);
        try {
            message.writeTo(writer);
        } catch (XMLStreamException e) {
            throw (e.getCause() instanceof SOAPException) ? (SOAPException) e.getCause() : new SOAPException(e);
        }
        msg = writer.getSOAPMessage();

        counter = 0;
        String cid2 = null;
        for(Attachment a : message.getAttachments()) {
            counter++;
            cid2 = a.getContentId();
        }
        assertEquals(writer.ma.size(), counter);
        StreamingDataHandler sdh = (StreamingDataHandler)writer.ma.get(0);
        assertEquals(cid1, sdh.getHrefCid());
        assertEquals(cid2, sdh.getHrefCid());
    }


    public void testMtomAttachmentCid() throws Exception {
        String testMtomMessageReload_01 = "multipart/related;type=\"application/xop+xml\";boundary=\"----=_Part_0_1145105632.1353005695468\";start=\"<cbe648b3-2055-413e-b8ed-877cdf0f2477>\";start-info=\"text/xml\"";
        
        MessageContext m1 = mcf.createContext(getResource("testMtomMessageReload_01.msg"), testMtomMessageReload_01);
        Packet packet = (Packet) m1;
        Message riMsg = packet.getInternalMessage();
        //This will cause all the attachments to be created ...
//        Iterator<Attachment> as = packet.getInternalMessage().getAttachments().iterator();
        
        //SAAJFactory:
        SOAPVersion soapVersion = packet.getMessage().getSOAPVersion();
        SOAPMessage saajMsg = soapVersion.getMessageFactory().createMessage();
        SaajStaxWriterEx writer = new SaajStaxWriterEx(saajMsg, soapVersion.nsUri);
        try {
            riMsg.writeTo(writer);
        } catch (XMLStreamException e) {
            throw (e.getCause() instanceof SOAPException) ? (SOAPException) e.getCause() : new SOAPException(e);
        }
        saajMsg = writer.getSOAPMessage();

        int counter = 0;
        String hredCid = null;
        for(Attachment a : riMsg.getAttachments()) {
            hredCid = ((StreamingDataHandler)a.asDataHandler()).getHrefCid();
            counter++;
        }
        assertEquals(writer.ma.size(), counter);
        AttachmentPart ap = null;
//        for (Iterator<AttachmentPart> itr = saajMsg.getAttachments(); itr.hasNext(); ) {
//            System.out.println("\r\n itr.next().getContentId()  " + itr.next().getContentId() );            
//        }
        StreamingDataHandler sdh = (StreamingDataHandler)writer.ma.get(0);
        assertEquals(hredCid, sdh.getHrefCid());
    }
    
    
    private InputStream getResource(String str) throws Exception {
//      return new File("D:/oc4j/webservices/devtest/data/cts15/DLSwaTest/" + str).toURL();
      return Thread.currentThread().getContextClassLoader().getResource("etc/"+str).openStream();
    }
    
    static class SaajStaxWriterEx extends SaajStaxWriter implements XMLStreamWriterEx, org.jvnet.staxex.util.MtomStreamWriter {
        
        static final protected String xopNS = "http://www.w3.org/2004/08/xop/include";
        static final protected String Include = "Include";
        static final protected String href = "href";
        
        private final int mtomThreshold = 0;
        private enum State {xopInclude, others};
        private State state = State.others;
        ArrayList<Object> ma;
        private Object binaryText;

        public SaajStaxWriterEx(SOAPMessage msg, String ns) throws SOAPException {
            super(msg, ns);
            ma = new ArrayList<Object> ();
        }
        
        public void writeStartElement(String prefix, String ln, String ns) throws XMLStreamException {
            if (xopNS.equals(ns) && Include.equals(ln)) {
                state = State.xopInclude;
                return;
            } else {
                super.writeStartElement(prefix, ln, ns);
            }
        }
        
        @Override
        public void writeEndElement() throws XMLStreamException {
            if (state.equals(State.xopInclude)) {
                state = State.others;
            } else {
                super.writeEndElement();
            }
        }

        @Override
        public void writeAttribute(String prefix, String ns, String ln, String value) throws XMLStreamException {
            if (binaryText != null && href.equals(ln)) {
                return;
            } else {
                super.writeAttribute(prefix, ns, ln, value);
            }
        }

        @Override
        public NamespaceContextEx getNamespaceContext() {
            return new NamespaceContextEx() {
                public String getNamespaceURI(String prefix) {
                    return currentElement.getNamespaceURI(prefix);
                }
                public String getPrefix(String namespaceURI) {
                    return currentElement.lookupPrefix(namespaceURI);
                }
                public Iterator getPrefixes(final String namespaceURI) {
                    return new Iterator() {
                        String prefix = getPrefix(namespaceURI);
                        public boolean hasNext() {
                            return (prefix != null);
                        }
                        public Object next() {
                            String next = prefix;
                            prefix = null;
                            return next;
                        }
                        public void remove() {}                    
                    };
                } 
                public Iterator<Binding> iterator() {
                    return new Iterator<Binding>() {
                        public boolean hasNext() { return false; }
                        public Binding next() { return null; }
                        public void remove() {}                    
                    };
                }            
            };
        }

        @Override
        public void writeBinary(DataHandler data) throws XMLStreamException {
            binaryText = data;
            ma.add(data);
//            binaryText = BinaryTextImpl.createBinaryTextFromDataHandler((MessageImpl)soap, null, currentElement.getOwnerDocument(), data);
//            currentElement.appendChild(binaryText);        
        }

        @Override
        public OutputStream writeBinary(String arg0) throws XMLStreamException {
            return null;
        }

        @Override
        public void writeBinary(byte[] data, int offset, int length, String contentType) throws XMLStreamException {
//            if (mtomThreshold == -1 || mtomThreshold > length) return null;
            byte[] bytes = (offset == 0 && length == data.length) ? data : Arrays.copyOfRange(data, offset, offset + length);
//          binaryText = (BinaryTextImpl) ((ElementImpl) currentElement).addAsBase64TextNode(bytes);     
            binaryText = bytes;
            ma.add(data);
        }

        @Override
        public void writePCDATA(CharSequence arg0) throws XMLStreamException {
//          binaryText = (BinaryTextImpl) ((ElementImpl) currentElement).addAsBase64TextNode(((Base64Data)arg0).getExact());
            binaryText = arg0;
            ma.add(arg0);
        }

        public AttachmentMarshaller getAttachmentMarshaller() {
            return new AttachmentMarshaller() {
                private String cid(){
                    String cid="example.jaxws.sun.com";
                    String name = UUID.randomUUID()+"@";
                    return name + cid;
                }

                @Override
                public String addMtomAttachment(DataHandler data, String ns, String ln) {
                    if (mtomThreshold == -1) return null;
                    if (data instanceof MIMEPartStreamingDataHandler) {
                        StreamingDataHandler dh = (StreamingDataHandler)data;
                    }
                    // Should we do the threshold processing on DataHandler ? But that would be
                    // expensive as DataHolder need to read the data again from its source
//                    binaryText = BinaryTextImpl.createBinaryTextFromDataHandler((MessageImpl)soap, null, currentElement.getOwnerDocument(), data);
//                    currentElement.appendChild(binaryText);
                    binaryText = data;
                    ma.add(data);
//                    return binaryText.getHref();
                    return ((StreamingDataHandler)data).getHrefCid();
                }

                @Override
                public String addMtomAttachment(byte[] data, int offset, int length, String mimeType, String ns, String ln) {
                    if (mtomThreshold == -1 || mtomThreshold > length) return null;
                    byte[] bytes = (offset == 0 && length == data.length) ? data : Arrays.copyOfRange(data, offset, offset + length);
//                    binaryText = (BinaryTextImpl) ((ElementImpl) currentElement).addAsBase64TextNode(bytes);
                    binaryText = data;
                    ma.add(data);
                    
//                    return binaryText.getHref();
                    return cid();
                }

                @Override
                public String addSwaRefAttachment(DataHandler data) {
                    return "cid:"+cid();
                }

                @Override
                public boolean isXOPPackage() {
                    return true;
                }
            };
        }
    }
}
