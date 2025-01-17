/*
 * Copyright (c) 1997, 2022 Oracle and/or its affiliates. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0, which is available at
 * http://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package com.sun.xml.ws.message.stream;

import com.sun.xml.ws.api.message.Attachment;
import com.sun.xml.ws.util.ByteArrayDataSource;
import com.sun.xml.ws.util.ByteArrayBuffer;
import com.sun.xml.ws.encoding.DataSourceStreamingDataHandler;

import jakarta.activation.DataHandler;
import javax.xml.transform.Source;
import javax.xml.transform.stream.StreamSource;
import jakarta.xml.soap.SOAPMessage;
import jakarta.xml.soap.AttachmentPart;
import jakarta.xml.soap.SOAPException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.IOException;
import java.io.ByteArrayInputStream;

import org.jvnet.staxex.Base64Data;

/**
 * Attachment created from raw bytes.
 *
 * @author Vivek Pandey
 */
public class StreamAttachment implements Attachment {
    private final String contentId;
    private final String contentType;
    private final ByteArrayBuffer byteArrayBuffer;
    private final byte[] data;
    private final int len;

    public StreamAttachment(ByteArrayBuffer buffer, String contentId, String contentType) {
        this.contentId = contentId;
        this.contentType = contentType;
        this.byteArrayBuffer = buffer;
        this.data = byteArrayBuffer.getRawData();
        this.len = byteArrayBuffer.size();
    }

    @Override
    public String getContentId() {
        return contentId;
    }

    @Override
    public String getContentType() {
        return contentType;
    }


    @Override
    public byte[] asByteArray() {
        //we got to reallocate and give the exact byte[]
        return byteArrayBuffer.toByteArray();
    }

    @Override
    public DataHandler asDataHandler() {
        return new DataSourceStreamingDataHandler(new ByteArrayDataSource(data,0,len,getContentType()));
    }

    @Override
    public Source asSource() {
        return new StreamSource(new ByteArrayInputStream(data,0,len));
    }

    @Override
    public InputStream asInputStream() {
        return byteArrayBuffer.newInputStream();
    }

    public Base64Data asBase64Data(){
        Base64Data base64Data = new Base64Data();
        base64Data.set(data, len, contentType);
        return base64Data;
    }

    @Override
    public void writeTo(OutputStream os) throws IOException {
        byteArrayBuffer.writeTo(os);
    }

    @Override
    public void writeTo(SOAPMessage saaj) throws SOAPException {
        AttachmentPart part = saaj.createAttachmentPart();
        part.setRawContentBytes(data,0,len,getContentType());
        part.setContentId(contentId);
        saaj.addAttachmentPart(part);
    }
}
