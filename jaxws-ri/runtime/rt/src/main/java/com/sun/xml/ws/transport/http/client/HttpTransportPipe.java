/*
 * Copyright (c) 1997, 2022 Oracle and/or its affiliates. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0, which is available at
 * http://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package com.sun.xml.ws.transport.http.client;

import com.sun.istack.NotNull;
import com.sun.xml.ws.api.SOAPVersion;
import com.sun.xml.ws.api.WSBinding;
import com.sun.xml.ws.api.ha.StickyFeature;
import com.sun.xml.ws.api.message.Packet;
import com.sun.xml.ws.api.pipe.*;
import com.sun.xml.ws.api.pipe.helper.AbstractTubeImpl;
import com.sun.xml.ws.client.ClientTransportException;
import com.sun.xml.ws.developer.HttpConfigFeature;
import com.sun.xml.ws.resources.ClientMessages;
import com.sun.xml.ws.resources.WsservletMessages;
import com.sun.xml.ws.transport.Headers;
import com.sun.xml.ws.transport.http.HttpAdapter;
import com.sun.xml.ws.util.ByteArrayBuffer;
import com.sun.xml.ws.util.RuntimeVersion;
import com.sun.xml.ws.util.StreamUtils;

import jakarta.xml.bind.DatatypeConverter;
import jakarta.xml.ws.BindingProvider;
import jakarta.xml.ws.WebServiceException;
import jakarta.xml.ws.WebServiceFeature;
import jakarta.xml.ws.handler.MessageContext;
import jakarta.xml.ws.soap.SOAPBinding;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.Authenticator;
import java.net.CookieHandler;
import java.net.HttpURLConnection;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * {@link Tube} that sends a request to a remote HTTP server.
 *
 * TODO: need to create separate HTTP transport pipes for binding. SOAP1.1, SOAP1.2,
 * TODO: XML/HTTP differ in handling status codes.
 *
 * @author Jitendra Kotamraju
 */
public class HttpTransportPipe extends AbstractTubeImpl {

    private static final List<String> USER_AGENT = Collections.singletonList(RuntimeVersion.VERSION.toString());
    private static final Logger LOGGER = Logger.getLogger(HttpTransportPipe.class.getName());

    /**
     * Dumps what goes across HTTP transport.
     */
    public static boolean dump;

    private final Codec codec;
    private final WSBinding binding;
    private final CookieHandler cookieJar;      // shared object among the tubes
    private final boolean sticky;

    static {
        try {
            dump = Boolean.getBoolean(HttpTransportPipe.class.getName() + ".dump");
        } catch (SecurityException se) {
            if (LOGGER.isLoggable(Level.CONFIG)) {
                LOGGER.log(Level.CONFIG, "Cannot read ''{0}'' property, using defaults.",
                        new Object[]{HttpTransportPipe.class.getName() + ".dump"});
            }
        }
    }

    public HttpTransportPipe(Codec codec, WSBinding binding) {
        this.codec = codec;
        this.binding = binding;
        this.sticky = isSticky(binding);
        HttpConfigFeature configFeature = binding.getFeature(HttpConfigFeature.class);
        if (configFeature == null) {
            configFeature = new HttpConfigFeature();
        }
        this.cookieJar = configFeature.getCookieHandler();
    }

    private static boolean isSticky(WSBinding binding) {
        boolean tSticky = false;
        WebServiceFeature[] features = binding.getFeatures().toArray();
        for(WebServiceFeature f : features) {
            if (f instanceof StickyFeature) {
                tSticky = true;
                break;
            }
        }
        return tSticky;
    }

    /*
     * Copy constructor for {@link Tube#copy(TubeCloner)}.
     */
    private HttpTransportPipe(HttpTransportPipe that, TubeCloner cloner) {
        this(that.codec.copy(), that.binding);
        cloner.add(that,this);
    }

    @Override
    public NextAction processException(@NotNull Throwable t) {
        return doThrow(t);
    }

    @Override
    public NextAction processRequest(@NotNull Packet request) {
        return doReturnWith(process(request));
    }

    @Override
    public NextAction processResponse(@NotNull Packet response) {
        return doReturnWith(response);
    }

    protected HttpClientTransport getTransport(Packet request, Map<String, List<String>> reqHeaders,  Authenticator authenticator) {
        return new HttpClientTransport(request, reqHeaders, authenticator);
    }

    @Override
    public Packet process(Packet request) {
        HttpClientTransport con;
        try {
            // get transport headers from message
            Map<String, List<String>> reqHeaders = new Headers();
            @SuppressWarnings("unchecked")
            Map<String, List<String>> userHeaders = (Map<String, List<String>>) request.invocationProperties.get(MessageContext.HTTP_REQUEST_HEADERS);
            boolean addUserAgent = true;
            if (userHeaders != null) {
                // userHeaders may not be modifiable like SingletonMap, just copy them
                reqHeaders.putAll(userHeaders);
                // application wants to use its own User-Agent header
                if (userHeaders.get("User-Agent") != null) {
                    addUserAgent = false;
                }
            }
            if (addUserAgent) {
                reqHeaders.put("User-Agent", USER_AGENT);
            }

            addCookies(request, reqHeaders);

            final Authenticator authentication = getAuthentication(request);


            con = getTransport(request, reqHeaders,  authentication);
            request.addSatellite(new HttpResponseProperties(con));

            ContentType ct = codec.getStaticContentType(request);
            if (ct == null) {
                ByteArrayBuffer buf = new ByteArrayBuffer();

                ct = codec.encode(request, buf);
                // data size is available, set it as Content-Length
                reqHeaders.put("Content-Length", Collections.singletonList(Integer.toString(buf.size())));
                reqHeaders.put("Content-Type", Collections.singletonList(ct.getContentType()));
                if (ct.getAcceptHeader() != null) {
                    reqHeaders.put("Accept", Collections.singletonList(ct.getAcceptHeader()));
                }
                if (binding instanceof SOAPBinding) {
                    writeSOAPAction(reqHeaders, ct.getSOAPActionHeader());
                }

                if (dump || LOGGER.isLoggable(Level.FINER)) {
                    dump(buf, "HTTP request", reqHeaders);
                }

                buf.writeTo(con.getOutput());
            } else {
                // Set static Content-Type
                reqHeaders.put("Content-Type", Collections.singletonList(ct.getContentType()));
                if (ct.getAcceptHeader() != null) {
                    reqHeaders.put("Accept", Collections.singletonList(ct.getAcceptHeader()));
                }
                if (binding instanceof SOAPBinding) {
                    writeSOAPAction(reqHeaders, ct.getSOAPActionHeader());
                }

                if(dump || LOGGER.isLoggable(Level.FINER)) {
                    ByteArrayBuffer buf = new ByteArrayBuffer();
                    codec.encode(request, buf);
                    dump(buf, "HTTP request - "+request.endpointAddress, reqHeaders);
                    OutputStream out = con.getOutput();
                    if (out != null) {
                        buf.writeTo(out);
                    }
                } else {
                    OutputStream os = con.getOutput();
                    if (os != null) {
                        codec.encode(request, os);
                    }
                }
            }

            con.closeOutput();

            return createResponsePacket(request, con);
        } catch(WebServiceException wex) {
            throw wex;
        } catch(Exception ex) {
            throw new WebServiceException(ex);
        }
    }

    private Authenticator getAuthentication(Packet request) {
        String user = (String) request.invocationProperties.get(BindingProvider.USERNAME_PROPERTY);
        if (user != null) {
            String pw = (String) request.invocationProperties.get(BindingProvider.PASSWORD_PROPERTY);
            if (pw != null) {
                return new Authenticator() {
                    @Override
                    protected PasswordAuthentication getPasswordAuthentication() {
                        return new PasswordAuthentication(user, pw.toCharArray());
                    }
                };

            }
        }
        return null;
    }

    private Packet createResponsePacket(Packet request, HttpClientTransport con) throws IOException {
        con.readResponseCodeAndMessage();   // throws IOE
        recordCookies(request, con);

        InputStream responseStream = con.getInput();
        if (dump || LOGGER.isLoggable(Level.FINER)) {
            ByteArrayBuffer buf = new ByteArrayBuffer();
            if (responseStream != null) {
                buf.write(responseStream);
                responseStream.close();
            }
            dump(buf,"HTTP response - "+request.endpointAddress+" - "+con.statusCode, con.getHeaders());
            responseStream = buf.newInputStream();
        }

        // Check if stream contains any data
        int cl = con.contentLength;
        InputStream tempIn = null;
        if (cl == -1) {                     // No Content-Length header
            tempIn = StreamUtils.hasSomeData(responseStream);
            if (tempIn != null) {
                responseStream = tempIn;
            }
        }
        if (cl == 0 || (cl == -1 && tempIn == null)) {
            if(responseStream != null) {
                responseStream.close();         // No data, so close the stream
                responseStream = null;
            }

        }

        // Allows only certain http status codes for a binding. For all
        // other status codes, throws exception
        checkStatusCode(responseStream, con); // throws ClientTransportException
        //To avoid zero-length chunk for One-Way
        if (cl ==-1 && con.statusCode == 202 && "Accepted".equals(con.statusMessage) && responseStream != null) {
            ByteArrayBuffer buf = new ByteArrayBuffer();
            buf.write(responseStream); //What is within the responseStream?
            responseStream.close();
            responseStream = (buf.size()==0)? null : buf.newInputStream();
            buf.close();
        }
        Packet reply = request.createClientResponse(null);
        reply.wasTransportSecure = con.isSecure();
        if (responseStream != null) {
            String contentType = con.getContentType();
            if (contentType != null && contentType.contains("text/html") && binding instanceof SOAPBinding) {
                throw new ClientTransportException(ClientMessages.localizableHTTP_STATUS_CODE(con.statusCode, con.statusMessage));
            }
            codec.decode(responseStream, contentType, reply);
        }
        return reply;
    }

    /*
     * Allows the following HTTP status codes.
     * SOAP 1.1/HTTP - 200, 202, 500
     * SOAP 1.2/HTTP - 200, 202, 400, 500
     * XML/HTTP - all
     *
     * For all other status codes, it throws an exception
     */
    private void checkStatusCode(InputStream in, HttpClientTransport con) throws IOException {
        int statusCode = con.statusCode;
        String statusMessage = con.statusMessage;
        // SOAP1.1 and SOAP1.2 differ here
        if (binding instanceof SOAPBinding) {
            if (binding.getSOAPVersion() == SOAPVersion.SOAP_12) {
                //In SOAP 1.2, Fault messages can be sent with 4xx and 5xx error codes
                if (statusCode == HttpURLConnection.HTTP_OK || statusCode == HttpURLConnection.HTTP_ACCEPTED || isErrorCode(statusCode)) {
                    // acceptable status codes for SOAP 1.2
                    if (isErrorCode(statusCode) && in == null) {
                        // No envelope for the error, so throw an exception with http error details
                        throw new ClientTransportException(ClientMessages.localizableHTTP_STATUS_CODE(statusCode, statusMessage));
                    }
                    return;
                }
            } else {
                // SOAP 1.1
                if (statusCode == HttpURLConnection.HTTP_OK || statusCode == HttpURLConnection.HTTP_ACCEPTED || statusCode == HttpURLConnection.HTTP_INTERNAL_ERROR) {
                    // acceptable status codes for SOAP 1.1
                    if (statusCode == HttpURLConnection.HTTP_INTERNAL_ERROR && in == null) {
                        // No envelope for the error, so throw an exception with http error details
                        throw new ClientTransportException(ClientMessages.localizableHTTP_STATUS_CODE(statusCode, statusMessage));
                    }
                    return;
                }
            }
            if (in != null) {
                in.close();
            }
            throw new ClientTransportException(ClientMessages.localizableHTTP_STATUS_CODE(statusCode, statusMessage));
        }
        // Every status code is OK for XML/HTTP
    }

    private boolean isErrorCode(int code) {
        //if(code/100 == 5/*Server-side error*/ || code/100 == 4 /*client error*/ ) {
        return code == 500 || code == 400;
    }

    private void addCookies(Packet context, Map<String, List<String>> reqHeaders) throws IOException {
        Boolean shouldMaintainSessionProperty =
                (Boolean) context.invocationProperties.get(BindingProvider.SESSION_MAINTAIN_PROPERTY);
        if (shouldMaintainSessionProperty != null && !shouldMaintainSessionProperty) {
            return;         // explicitly turned off
        }
        if (sticky || (shouldMaintainSessionProperty != null && shouldMaintainSessionProperty)) {
            Map<String, List<String>> rememberedCookies = cookieJar.get(context.endpointAddress.getURI(), reqHeaders);
            processCookieHeaders(reqHeaders, rememberedCookies, "Cookie");
            processCookieHeaders(reqHeaders, rememberedCookies, "Cookie2");
        }
    }

    private void processCookieHeaders(Map<String, List<String>> requestHeaders, Map<String, List<String>> rememberedCookies, String cookieHeader) {
        List<String> jarCookies = rememberedCookies.get(cookieHeader);
        if (jarCookies != null && !jarCookies.isEmpty()) {
            List<String> resultCookies = mergeUserCookies(jarCookies, requestHeaders.get(cookieHeader));
            requestHeaders.put(cookieHeader, resultCookies);
        }
    }

    private List<String> mergeUserCookies(List<String> rememberedCookies, List<String> userCookies) {

        // nothing to merge
        if (userCookies == null || userCookies.isEmpty()) {
            return rememberedCookies;
        }

        Map<String, String> map = new HashMap<>();
        cookieListToMap(rememberedCookies, map);
        cookieListToMap(userCookies, map);

        return new ArrayList<>(map.values());
    }

    private void cookieListToMap(List<String> cookieList, Map<String, String> targetMap) {
        for(String cookie : cookieList) {
            int index = cookie.indexOf("=");
            String cookieName = cookie.substring(0, index);
            targetMap.put(cookieName, cookie);
        }
    }

    private void recordCookies(Packet context, HttpClientTransport con) throws IOException {
        Boolean shouldMaintainSessionProperty =
                (Boolean) context.invocationProperties.get(BindingProvider.SESSION_MAINTAIN_PROPERTY);
        if (shouldMaintainSessionProperty != null && !shouldMaintainSessionProperty) {
            return;         // explicitly turned off
        }
        if (sticky || (shouldMaintainSessionProperty != null && shouldMaintainSessionProperty)) {
            cookieJar.put(context.endpointAddress.getURI(), con.getHeaders());
        }
    }



    /*
     * write SOAPAction header if the soapAction parameter is non-null or BindingProvider properties set.
     * BindingProvider properties take precedence.
     */
    private void writeSOAPAction(Map<String, List<String>> reqHeaders, String soapAction) {
        //dont write SOAPAction HTTP header for SOAP 1.2 messages.
        if(SOAPVersion.SOAP_12.equals(binding.getSOAPVersion())) {
            return;
        }
        if (soapAction != null) {
            reqHeaders.put("SOAPAction", Collections.singletonList(soapAction));
        } else {
            reqHeaders.put("SOAPAction", Collections.singletonList("\"\""));
        }
    }

    @Override
    public void preDestroy() {
        // nothing to do. Intentionally left empty.
    }

    @Override
    public HttpTransportPipe copy(TubeCloner cloner) {
        return new HttpTransportPipe(this,cloner);
    }


    private void dump(ByteArrayBuffer buf, String caption, Map<String, List<String>> headers) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PrintWriter pw = new PrintWriter(baos, true);
        pw.println("---["+caption +"]---");
        for (Entry<String,List<String>> header : headers.entrySet()) {
            if(header.getValue().isEmpty()) {
                // I don't think this is legal, but let's just dump it,
                // as the point of the dump is to uncover problems.
                pw.println(header.getValue());
            } else {
                for (String value : header.getValue()) {
                    pw.println(header.getKey()+": "+value);
                }
            }
        }

        if (buf.size() > HttpAdapter.dump_threshold) {
            byte[] b = buf.getRawData();
            baos.write(b, 0, HttpAdapter.dump_threshold);
            pw.println();
            pw.println(WsservletMessages.MESSAGE_TOO_LONG(HttpAdapter.class.getName() + ".dumpTreshold"));
        } else {
            buf.writeTo(baos);
            pw.println();
        }
        pw.println("--------------------");

        String msg = baos.toString();
        if (dump) {
            System.out.println(msg);
        }
        if (LOGGER.isLoggable(Level.FINER)) {
            LOGGER.log(Level.FINER, msg);
        }
    }

    public static void setDump(boolean dumpMessages) {
        HttpTransportPipe.dump = dumpMessages;
    }
}
