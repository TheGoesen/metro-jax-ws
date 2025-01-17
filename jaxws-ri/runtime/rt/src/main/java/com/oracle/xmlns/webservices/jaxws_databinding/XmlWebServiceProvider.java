/*
 * Copyright (c) 2012, 2022 Oracle and/or its affiliates. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0, which is available at
 * http://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package com.oracle.xmlns.webservices.jaxws_databinding;
import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlAttribute;
import jakarta.xml.bind.annotation.XmlRootElement;
import jakarta.xml.bind.annotation.XmlType;
import java.lang.annotation.Annotation;


/**
 * This file was generated by JAXB-RI v2.2.6 and afterwards modified
 * to implement appropriate Annotation
 *
 * <p>Java class for anonymous complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType>
 *   &lt;complexContent>
 *     &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType">
 *       &lt;attribute name="targetNamespace" type="{http://www.w3.org/2001/XMLSchema}string" />
 *       &lt;attribute name="serviceName" type="{http://www.w3.org/2001/XMLSchema}string" />
 *       &lt;attribute name="portName" type="{http://www.w3.org/2001/XMLSchema}string" />
 *       &lt;attribute name="wsdlLocation" type="{http://www.w3.org/2001/XMLSchema}string" />
 *     &lt;/restriction>
 *   &lt;/complexContent>
 * &lt;/complexType>
 * </pre>
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "")
@XmlRootElement(name = "web-service-provider")
public class XmlWebServiceProvider implements jakarta.xml.ws.WebServiceProvider {

    @XmlAttribute(name = "targetNamespace")
    protected String targetNamespace;
    @XmlAttribute(name = "serviceName")
    protected String serviceName;
    @XmlAttribute(name = "portName")
    protected String portName;
    @XmlAttribute(name = "wsdlLocation")
    protected String wsdlLocation;

    /**
     * Gets the value of the targetNamespace property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getTargetNamespace() {
        return targetNamespace;
    }

    /**
     * Sets the value of the targetNamespace property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setTargetNamespace(String value) {
        this.targetNamespace = value;
    }

    /**
     * Gets the value of the serviceName property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getServiceName() {
        return serviceName;
    }

    /**
     * Sets the value of the serviceName property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setServiceName(String value) {
        this.serviceName = value;
    }

    /**
     * Gets the value of the portName property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getPortName() {
        return portName;
    }

    /**
     * Sets the value of the portName property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setPortName(String value) {
        this.portName = value;
    }

    /**
     * Gets the value of the wsdlLocation property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getWsdlLocation() {
        return wsdlLocation;
    }

    /**
     * Sets the value of the wsdlLocation property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setWsdlLocation(String value) {
        this.wsdlLocation = value;
    }

    @Override
    public String wsdlLocation() {
        return Util.nullSafe(wsdlLocation);
    }

    @Override
    public String serviceName() {
        return Util.nullSafe(serviceName);
    }

    @Override
    public String targetNamespace() {
        return Util.nullSafe(targetNamespace);
    }

    @Override
    public String portName() {
        return Util.nullSafe(portName);
    }

    @Override
    public Class<? extends Annotation> annotationType() {
        return jakarta.xml.ws.WebServiceProvider.class;
    }
}
