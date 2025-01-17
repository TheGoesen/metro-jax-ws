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

import com.sun.xml.ws.api.SOAPVersion;
import com.sun.xml.ws.api.WSFeatureList;
import com.sun.xml.ws.api.message.Attachment;
import com.sun.xml.ws.api.message.AttachmentEx;
import com.sun.xml.ws.api.message.Message;
import com.sun.xml.ws.api.message.Packet;
import com.sun.xml.ws.api.pipe.Codec;
import com.sun.xml.ws.api.pipe.ContentType;
import com.sun.xml.ws.developer.StreamingAttachmentFeature;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.channels.ReadableByteChannel;
import java.util.Iterator;
import java.util.UUID;

/**
 * {@link Codec}s that uses the MIME multipart as the underlying format.
 *
 * <p>
 * When the runtime needs to dynamically choose a {@link Codec}, and
 * when there are more than one {@link Codec}s that use MIME multipart,
 * it is often impossible to determine the right {@link Codec} unless
 * you parse the multipart message to some extent.
 *
 * <p>
 * By having all such {@link Codec}s extending from this class,
 * the "sniffer" can decode a multipart message partially, and then
 * pass the partial parse result to the ultimately-responsible {@link Codec}.
 * This improves the performance.
 *
 * @author Kohsuke Kawaguchi
 */
abstract class MimeCodec implements Codec {
    
    public static final String MULTIPART_RELATED_MIME_TYPE = "multipart/related";
    
    protected Codec mimeRootCodec;
    protected final SOAPVersion version;
    protected final WSFeatureList features;

    protected MimeCodec(SOAPVersion version, WSFeatureList f) {
        this.version = version;
        this.features = f;
    }
    
    @Override
    public String getMimeType() {
        return MULTIPART_RELATED_MIME_TYPE;
    }
    
    protected Codec getMimeRootCodec(Packet packet) {
        return mimeRootCodec;
    }

    // TODO: preencode String literals to byte[] so that they don't have to
    // go through char[]->byte[] conversion at runtime.
    @Override
    public ContentType encode(Packet packet, OutputStream out) throws IOException {
        Message msg = packet.getMessage();
        if (msg == null) {
            return null;
        }
        ContentTypeImpl ctImpl = (ContentTypeImpl)getStaticContentType(packet);
        String boundary = ctImpl.getBoundary();
        String rootId = ctImpl.getRootId();
        boolean hasAttachments = (boundary != null);
        Codec rootCodec = getMimeRootCodec(packet);
        if (hasAttachments) {
            writeln("--"+boundary, out);
            ContentType ct = rootCodec.getStaticContentType(packet);
            String ctStr = (ct != null) ? ct.getContentType() : rootCodec.getMimeType();
            if (rootId != null) writeln("Content-ID: " + rootId, out);
            writeln("Content-Type: " + ctStr, out);
            writeln(out);
        }
        ContentType primaryCt = rootCodec.encode(packet, out);

        if (hasAttachments) {
            writeln(out);
            // Encode all the attchments
            for (Attachment att : msg.getAttachments()) {
                writeln("--"+boundary, out);
                //SAAJ's AttachmentPart.getContentId() returns content id already enclosed with
                //angle brackets. For now put angle bracket only if its not there
                String cid = att.getContentId();
                if(cid != null && cid.length() >0 && cid.charAt(0) != '<')
                    cid = '<' + cid + '>';
                writeln("Content-Id:" + cid, out);
                writeln("Content-Type: " + att.getContentType(), out);
                writeCustomMimeHeaders(att, out);
                writeln("Content-Transfer-Encoding: binary", out);
                writeln(out);                    // write \r\n
                att.writeTo(out);
                writeln(out);                    // write \r\n
            }
            writeAsAscii("--"+boundary, out);
            writeAsAscii("--", out);
        }
        // TODO not returing correct multipart/related type(no boundary)
        return hasAttachments ? ctImpl : primaryCt;
    }
    
    private void writeCustomMimeHeaders(Attachment att, OutputStream out) throws IOException {
        if (att instanceof AttachmentEx) {
            Iterator<AttachmentEx.MimeHeader> allMimeHeaders = ((AttachmentEx) att).getMimeHeaders();
            while (allMimeHeaders.hasNext()) {
                AttachmentEx.MimeHeader mh = allMimeHeaders.next();
                String name = mh.getName();

                if (!"Content-Type".equalsIgnoreCase(name) && !"Content-Id".equalsIgnoreCase(name)) {
                    writeln(name +": " + mh.getValue(), out);
                }
            }
        }
    }

    @Override
    public ContentType getStaticContentType(Packet packet) {
        ContentType ct = (ContentType) packet.getInternalContentType();
        if ( ct != null ) return ct;
        Message msg = packet.getMessage();
        boolean hasAttachments = !msg.getAttachments().isEmpty();
        Codec rootCodec = getMimeRootCodec(packet);

        if (hasAttachments) {
            String boundary = "uuid:" + UUID.randomUUID();
            String boundaryParameter = "boundary=\"" + boundary + "\"";
            // TODO use primaryEncoder to get type
            String messageContentType =  MULTIPART_RELATED_MIME_TYPE + 
                    "; type=\"" + rootCodec.getMimeType() + "\"; " +
                    boundaryParameter;
            ContentTypeImpl impl = new ContentTypeImpl(messageContentType, packet.soapAction, null);
            impl.setBoundary(boundary);
            impl.setBoundaryParameter(boundaryParameter);
            packet.setContentType(impl);
            return impl;
        } else {
            ct = rootCodec.getStaticContentType(packet);
            packet.setContentType(ct);
            return ct;
        }
    }

    /**
     * Copy constructor.
     */
    protected MimeCodec(MimeCodec that) {
        this.version = that.version;
        this.features = that.features;
    }

    @Override
    public void decode(InputStream in, String contentType, Packet packet) throws IOException {
        MimeMultipartParser parser = new MimeMultipartParser(in, contentType, features.get(StreamingAttachmentFeature.class));
        decode(parser,packet);
    }

    @Override
    public void decode(ReadableByteChannel in, String contentType, Packet packet) {
        throw new UnsupportedOperationException();
    }

    /**
     * Parses a {@link Packet} from a {@link MimeMultipartParser}.
     */
    protected abstract void decode(MimeMultipartParser mpp, Packet packet) throws IOException;

    @Override
    public abstract MimeCodec copy();


    public static void writeln(String s,OutputStream out) throws IOException {
        writeAsAscii(s,out);
        writeln(out);
    }

    /**
     * Writes a string as ASCII string.
     */
    public static void writeAsAscii(String s,OutputStream out) throws IOException {
        int len = s.length();
        for( int i=0; i<len; i++ )
            out.write((byte)s.charAt(i));
    }

    public static void writeln(OutputStream out) throws IOException {
        out.write('\r');
        out.write('\n');
    }
}
