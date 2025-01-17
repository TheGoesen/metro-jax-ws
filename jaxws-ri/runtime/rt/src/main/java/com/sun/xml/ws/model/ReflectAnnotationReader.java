/*
 * Copyright (c) 1997, 2022 Oracle and/or its affiliates. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0, which is available at
 * http://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package com.sun.xml.ws.model;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.Map;

import com.sun.xml.ws.api.databinding.MetadataReader;

/**
 * ReflectAnnotationReader
 * 
 * @author shih-chang.chen@oracle.com
 */
public class ReflectAnnotationReader implements MetadataReader {
//getAnnotationOnImpl SEIorIMpl
	@Override
    public Annotation[] getAnnotations(Method m) {
		return m.getAnnotations();
	}

	@Override
    public Annotation[][] getParameterAnnotations(final Method method) {
        return AccessController.doPrivileged(new PrivilegedAction<>() {
            @Override
            public Annotation[][] run() {
                return method.getParameterAnnotations();
            }
        });
    }
	
	@Override
    public <A extends Annotation> A getAnnotation(final Class<A> annType, final Method m) {
        return AccessController.doPrivileged(new PrivilegedAction<>() {
            @Override
            public A run() {
                return m.getAnnotation(annType);
            }
        });
	}
	
	@Override
    public <A extends Annotation> A getAnnotation(final Class<A> annType, final Class<?> cls) {
        return AccessController.doPrivileged(new PrivilegedAction<>() {
            @Override
            public A run() {
                return cls.getAnnotation(annType);
            }
        });
	}

    @Override
    public Annotation[] getAnnotations(final Class<?> cls) {
        return AccessController.doPrivileged(new PrivilegedAction<>() {
            @Override
            public Annotation[] run() {
                return cls.getAnnotations();
            }
        });
    }

    @Override
    public void getProperties(final Map<String, Object> prop, final Class<?> cls){}
    
    @Override
    public void getProperties(final Map<String, Object> prop, final Method method){}
    
    @Override
    public void getProperties(final Map<String, Object> prop, final Method method, int pos){}
}
