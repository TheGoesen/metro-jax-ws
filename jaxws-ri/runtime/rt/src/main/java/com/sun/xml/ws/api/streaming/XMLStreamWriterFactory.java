/*
 * Copyright (c) 1997, 2022 Oracle and/or its affiliates. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0, which is available at
 * http://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package com.sun.xml.ws.api.streaming;

import com.sun.istack.NotNull;
import com.sun.istack.Nullable;
import com.sun.xml.ws.encoding.HasEncoding;
import com.sun.xml.ws.encoding.SOAPBindingCodec;
import com.sun.xml.ws.streaming.XMLReaderException;
import com.sun.xml.ws.util.MrJarUtil;
import com.sun.xml.ws.util.xml.XMLStreamWriterFilter;

import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.XMLStreamWriter;
import javax.xml.transform.stream.StreamResult;
import jakarta.xml.ws.WebServiceException;
import java.io.OutputStream;
import java.io.StringWriter;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Factory for {@link XMLStreamWriter}.
 *
 * <p>
 * This wraps {@link XMLOutputFactory} and allows us to reuse {@link XMLStreamWriter} instances
 * when appropriate.
 *
 * @author Kohsuke Kawaguchi
 */
@SuppressWarnings("StaticNonFinalUsedInInitialization")
public abstract class XMLStreamWriterFactory {

    private static final Logger LOGGER = Logger.getLogger(XMLStreamWriterFactory.class.getName());

    /**
     * Singleton instance.
     */
    private static volatile ContextClassloaderLocal<XMLStreamWriterFactory> writerFactory =
            new ContextClassloaderLocal<>() {

                @Override
                protected XMLStreamWriterFactory initialValue() {
                    XMLOutputFactory xof = null;
                    if (Boolean.getBoolean(XMLStreamWriterFactory.class.getName() + ".woodstox")) {
                        try {
                            xof = (XMLOutputFactory) Class.forName("com.ctc.wstx.stax.WstxOutputFactory").getConstructor().newInstance();
                        } catch (Exception e) {
                            // Ignore and fallback to default XMLOutputFactory
                        }
                    }
                    if (xof == null) {
                        xof = XMLOutputFactory.newInstance();
                    }

                    XMLStreamWriterFactory f = null;

                    // this system property can be used to disable the pooling altogether,
                    // in case someone hits an issue with pooling in the production system.
                    if (!MrJarUtil.getNoPoolProperty(XMLStreamWriterFactory.class.getName())) {
                        try {
                            Class<?> clazz = xof.createXMLStreamWriter(new StringWriter()).getClass();
                            if (clazz.getName().startsWith("com.sun.xml.stream.")) {
                                f = new Zephyr(xof, clazz);
                            }
                        } catch (XMLStreamException ex) {
                            Logger.getLogger(XMLStreamWriterFactory.class.getName()).log(Level.INFO, null, ex);
                        }
                    }

                    if (f == null) {
                        // is this Woodstox?
                        if (xof.getClass().getName().equals("com.ctc.wstx.stax.WstxOutputFactory"))
                            f = new NoLock(xof);
                    }
                    if (f == null)
                        f = new Default(xof);

                    if (LOGGER.isLoggable(Level.FINE)) {
                        LOGGER.log(Level.FINE, "XMLStreamWriterFactory instance is = {0}", f);
                    }
                    return f;
                }
            };

    /**
     * See {@link #create(OutputStream)} for the contract.
     * This method may be invoked concurrently.
     */
    public abstract XMLStreamWriter doCreate(OutputStream out);

    /**
     * See {@link #create(OutputStream,String)} for the contract.
     * This method may be invoked concurrently.
     */
    public abstract XMLStreamWriter doCreate(OutputStream out, String encoding);

    /**
     * See {@link #recycle(XMLStreamWriter)} for the contract.
     * This method may be invoked concurrently.
     */
    public abstract void doRecycle(XMLStreamWriter r);

    /**
     * Should be invoked when the code finished using an {@link XMLStreamWriter}.
     *
     * <p>
     * If the recycled instance implements {@link RecycleAware},
     * {@link RecycleAware#onRecycled()} will be invoked to let the instance
     * know that it's being recycled.
     *
     * <p>
     * It is not a hard requirement to call this method on every {@link XMLStreamReader}
     * instance. Not doing so just reduces the performance by throwing away
     * possibly reusable instances. So the caller should always consider the effort
     * it takes to recycle vs the possible performance gain by doing so.
     *
     * <p>
     * This method may be invked by multiple threads concurrently.
     *
     * @param r
     *      The {@link XMLStreamReader} instance that the caller finished using.
     *      This could be any {@link XMLStreamReader} implementation, not just
     *      the ones that were created from this factory. So the implementation
     *      of this class needs to be aware of that.
     */
    public static void recycle(XMLStreamWriter r) {
        get().doRecycle(r);
    }

    /**
     * Interface that can be implemented by {@link XMLStreamWriter} to
     * be notified when it's recycled.
     *
     * <p>
     * This provides a filtering {@link XMLStreamWriter} an opportunity to
     * recycle its inner {@link XMLStreamWriter}.
     */
    public interface RecycleAware {
        void onRecycled();
    }

    /**
     * Gets the singleton instance.
     */
    public static @NotNull XMLStreamWriterFactory get() {
        return writerFactory.get();
    }

    /**
     * Overrides the singleton {@link XMLStreamWriterFactory} instance that
     * the JAX-WS RI uses.
     *
     * @param f
     *      must not be null.
     */
    @SuppressWarnings({"null", "ConstantConditions"})
    public static void set(@NotNull XMLStreamWriterFactory f) {
        if(f==null) throw new IllegalArgumentException();
        writerFactory.set(f);
    }

    /**
     * Short-cut for {@link #create(OutputStream, String)} with UTF-8.
     */
    public static XMLStreamWriter create(OutputStream out) {
        return get().doCreate(out);
    }

    public static XMLStreamWriter create(OutputStream out, String encoding) {
        return get().doCreate(out, encoding);
    }

    /**
     * Default {@link XMLStreamWriterFactory} implementation
     * that can work with any {@link XMLOutputFactory}.
     *
     * <p>
     * {@link XMLOutputFactory} is not required to be thread-safe, so the
     * create method on this implementation is synchronized.
     */
    public static final class Default extends XMLStreamWriterFactory {
        private final XMLOutputFactory xof;

        public Default(XMLOutputFactory xof) {
            this.xof = xof;
        }

        @Override
        public XMLStreamWriter doCreate(OutputStream out) {
            return doCreate(out,"UTF-8");
        }

        @Override
        public synchronized XMLStreamWriter doCreate(OutputStream out, String encoding) {
            try {
                XMLStreamWriter writer = xof.createXMLStreamWriter(out,encoding);
                return new HasEncodingWriter(writer, encoding);
            } catch (XMLStreamException e) {
                throw new XMLReaderException("stax.cantCreate",e);
            }
        }

        @Override
        public void doRecycle(XMLStreamWriter r) {
            // no recycling
        }
    }

    /**
     * {@link XMLStreamWriterFactory} implementation for Sun's StaX implementation.
     *
     * <p>
     * This implementation supports instance reuse.
     */
    public static final class Zephyr extends XMLStreamWriterFactory {
        private final XMLOutputFactory xof;
        private final ThreadLocal<XMLStreamWriter> pool = new ThreadLocal<>();
        private final Method resetMethod;
        private final Method setOutputMethod;
        private final Class zephyrClass;

        public static XMLStreamWriterFactory newInstance(XMLOutputFactory xof) {
            try {
                Class<?> clazz = xof.createXMLStreamWriter(new StringWriter()).getClass();

                if(!clazz.getName().startsWith("com.sun.xml.stream."))
                return null;    // nope

                return new Zephyr(xof,clazz);
            } catch (XMLStreamException e) {
                return null;    // impossible
            }
        }

        private Zephyr(XMLOutputFactory xof, Class clazz) {
            this.xof = xof;

            zephyrClass = clazz;
            setOutputMethod = getMethod(clazz, "setOutput", StreamResult.class, String.class);
            resetMethod = getMethod(clazz, "reset");
        }

        private static Method getMethod(final Class<?> c, final String methodname, final Class<?>... params) {
            return AccessController.doPrivileged(
                    new PrivilegedAction<>() {
                        @Override
                        public Method run() {
                            try {
                                return c.getMethod(methodname, params);
                            } catch (NoSuchMethodException e) {
                                // impossible
                                throw new NoSuchMethodError(e.getMessage());
                            }
                        }
                    }
            );
        }

        /**
         * Fetchs an instance from the pool if available, otherwise null.
         */
        private @Nullable XMLStreamWriter fetch() {
            XMLStreamWriter sr = pool.get();
            if(sr==null)    return null;
            pool.set(null);
            return sr;
        }

        @Override
        public XMLStreamWriter doCreate(OutputStream out) {
            return doCreate(out,"UTF-8");
        }

        @Override
        public XMLStreamWriter doCreate(OutputStream out, String encoding) {
            XMLStreamWriter xsw = fetch();
            if(xsw!=null) {
                // try to reuse
                try {
                    resetMethod.invoke(xsw);
                    setOutputMethod.invoke(xsw,new StreamResult(out),encoding);
                } catch (IllegalAccessException | InvocationTargetException e) {
                    throw new XMLReaderException("stax.cantCreate",e);
                }
            } else {
                // create a new instance
                try {
                    xsw = xof.createXMLStreamWriter(out,encoding);
                } catch (XMLStreamException e) {
                    throw new XMLReaderException("stax.cantCreate",e);
                }
            }
            return new HasEncodingWriter(xsw, encoding);
        }

        @Override
        public void doRecycle(XMLStreamWriter r) {
            if (r instanceof HasEncodingWriter) {
                r = ((HasEncodingWriter)r).getWriter();
            }
            if(zephyrClass.isInstance(r)) {
                // this flushes the underlying stream, so it might cause chunking issue 
                try {
                    r.close();
                } catch (XMLStreamException e) {
                    throw new WebServiceException(e);
                }
                pool.set(r);
            }
            if(r instanceof RecycleAware)
                ((RecycleAware)r).onRecycled();
        }
    }

    /**
     *
     * For {@link javax.xml.stream.XMLOutputFactory} is thread safe.
     */
    public static final class NoLock extends XMLStreamWriterFactory {
        private final XMLOutputFactory xof;

        public NoLock(XMLOutputFactory xof) {
            this.xof = xof;
        }

        @Override
        public XMLStreamWriter doCreate(OutputStream out) {
            return doCreate(out, SOAPBindingCodec.UTF8_ENCODING);
        }

        @Override
        public XMLStreamWriter doCreate(OutputStream out, String encoding) {
            try {
                XMLStreamWriter writer = xof.createXMLStreamWriter(out,encoding);
                return new HasEncodingWriter(writer, encoding);
            } catch (XMLStreamException e) {
                throw new XMLReaderException("stax.cantCreate",e);
            }
        }

        @Override
        public void doRecycle(XMLStreamWriter r) {
            // no recycling
        }

    }

    public static class HasEncodingWriter extends XMLStreamWriterFilter implements HasEncoding {
        private final String encoding;

        HasEncodingWriter(XMLStreamWriter writer, String encoding) {
            super(writer);
            this.encoding = encoding;
        }

        @Override
        public String getEncoding() {
            return encoding;
        }

        public XMLStreamWriter getWriter() {
            return writer;
        }
    }
}
