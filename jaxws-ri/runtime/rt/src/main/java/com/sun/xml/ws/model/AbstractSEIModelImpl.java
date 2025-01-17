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

import com.sun.istack.NotNull;
import org.glassfish.jaxb.runtime.api.Bridge;
import org.glassfish.jaxb.runtime.api.JAXBRIContext;
import org.glassfish.jaxb.runtime.api.TypeReference;
import com.sun.xml.ws.api.BindingID;
import com.sun.xml.ws.api.WSBinding;
import com.sun.xml.ws.api.databinding.Databinding;
import com.sun.xml.ws.api.model.JavaMethod;
import com.sun.xml.ws.api.model.ParameterBinding;
import com.sun.xml.ws.api.model.SEIModel;
import com.sun.xml.ws.api.model.wsdl.WSDLModel;
import com.sun.xml.ws.api.model.wsdl.WSDLPort;
import com.sun.xml.ws.api.model.wsdl.WSDLPart;
import com.sun.xml.ws.api.model.wsdl.WSDLBoundPortType;
import com.sun.xml.ws.api.model.wsdl.WSDLBoundOperation;
import com.sun.xml.ws.encoding.soap.streaming.SOAPNamespaceConstants;
import com.sun.xml.ws.resources.ModelerMessages;
import com.sun.xml.ws.spi.db.BindingContext;
import com.sun.xml.ws.spi.db.BindingContextFactory;
import com.sun.xml.ws.spi.db.BindingInfo;
import com.sun.xml.ws.spi.db.XMLBridge;
import com.sun.xml.ws.spi.db.TypeInfo;
import com.sun.xml.ws.util.Pool;
import com.sun.xml.ws.developer.UsesJAXBContextFeature;
import com.sun.xml.ws.developer.JAXBContextFactory;
import com.sun.xml.ws.binding.WebServiceFeatureList;

import jakarta.jws.WebParam.Mode;
import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.annotation.XmlSeeAlso;
import javax.xml.namespace.QName;
import jakarta.xml.ws.WebServiceException;


import java.lang.reflect.Method;
import java.security.AccessController;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * model of the web service.  Used by the runtime marshall/unmarshall
 * web service invocations
 *
 * @author JAXWS Development Team
 */
public abstract class AbstractSEIModelImpl implements SEIModel {

    protected AbstractSEIModelImpl(WebServiceFeatureList features) {
        this.features = features;
        databindingInfo = new BindingInfo();
        databindingInfo.setSEIModel(this);
    }

    void postProcess() {
        // should be called only once.
        if (jaxbContext != null) {
            return;
        }
        populateMaps();
        createJAXBContext();
    }

    public BindingInfo databindingInfo() {
        return databindingInfo;
    }
    
    /**
     * Link {@link SEIModel} to {@link WSDLModel}.
     * Merge it with {@link #postProcess()}.
     */
    public void freeze(WSDLPort port) {
        this.port = port;
        for (JavaMethodImpl m : javaMethods) {
            m.freeze(port);
            putOp(m.getOperationQName(),m);

        }
        if (databinding != null) {
            ((com.sun.xml.ws.db.DatabindingImpl)databinding).freeze(port);
        }
    }

    /**
     * Populate methodToJM and nameToJM maps.
     */
    abstract protected void populateMaps();

    @Override
    public Pool.Marshaller getMarshallerPool() {
        return marshallers;
    }

    /**
     * @return the <code>JAXBRIContext</code>
     * @deprecated
     */
    @Deprecated
    @Override
    public JAXBContext getJAXBContext() {
    	JAXBContext jc = bindingContext.getJAXBContext();
    	if (jc != null) {
            return jc;
        }
    	return jaxbContext;
    }

    public BindingContext getBindingContext() {
        return bindingContext;
    }

    /**
     * @return the known namespaces from JAXBRIContext
     */
    public List<String> getKnownNamespaceURIs() {
        return knownNamespaceURIs;
    }

    /**
     * @return the <code>Bridge</code> for the <code>type</code>
     * @deprecated use getBond
     */
    public final Bridge getBridge(TypeReference type) {
        Bridge b = bridgeMap.get(type);
        assert b!=null; // we should have created Bridge for all TypeReferences known to this model
        return b;
    }
    
    public final XMLBridge getXMLBridge(TypeInfo type) {
        XMLBridge b = xmlBridgeMap.get(type);
        assert b!=null; // we should have created Bridge for all TypeReferences known to this model
        return b;
    }

    private void /*JAXBRIContext*/ createJAXBContext() {
        final List<TypeInfo> types = getAllTypeInfos();
        final List<Class> cls = new ArrayList<>(types.size() + additionalClasses.size());

        cls.addAll(additionalClasses);
        for (TypeInfo type : types) {
            cls.add((Class) type.type);
        }

        try {
            //jaxbContext = JAXBRIContext.newInstance(cls, types, targetNamespace, false);
            // Need to avoid doPriv block once JAXB is fixed. Afterwards, use the above
            bindingContext = AccessController.doPrivileged(new PrivilegedExceptionAction<>() {
                @Override
                public BindingContext run() throws Exception {
                    if (LOGGER.isLoggable(Level.FINEST)) {
                        LOGGER.log(Level.FINEST, "Creating JAXBContext with classes={0} and types={1}", new Object[]{cls, types});
                    }
                    UsesJAXBContextFeature f = features.get(UsesJAXBContextFeature.class);
                    com.oracle.webservices.api.databinding.DatabindingModeFeature dmf =
                            features.get(com.oracle.webservices.api.databinding.DatabindingModeFeature.class);
                    JAXBContextFactory factory = f != null ? f.getFactory() : null;
                    if (factory == null) factory = JAXBContextFactory.DEFAULT;

//                    return factory.createJAXBContext(AbstractSEIModelImpl.this,cls,types);

                    databindingInfo.properties().put(JAXBContextFactory.class.getName(), factory);
                    if (dmf != null) {
                        if (LOGGER.isLoggable(Level.FINE))
                            LOGGER.log(Level.FINE, "DatabindingModeFeature in SEI specifies mode: {0}", dmf.getMode());
                        databindingInfo.setDatabindingMode(dmf
                                .getMode());
                    }

                    if (f != null) databindingInfo.setDatabindingMode(BindingContextFactory.DefaultDatabindingMode);
                    databindingInfo.setClassLoader(classLoader);
                    databindingInfo.contentClasses().addAll(cls);
                    databindingInfo.typeInfos().addAll(types);
                    databindingInfo.properties().put("c14nSupport", Boolean.FALSE);
                    databindingInfo.setDefaultNamespace(AbstractSEIModelImpl.this.getDefaultSchemaNamespace());
                    BindingContext bc = BindingContextFactory.create(databindingInfo);
                    if (LOGGER.isLoggable(Level.FINE))
                        LOGGER.log(Level.FINE, "Created binding context: {0}", bc.getClass().getName());
//                	System.out.println("---------------------- databinding " + bc);
                    return bc;
                }
            });
//          createBridgeMap(types);
            createBondMap(types);
        } catch (PrivilegedActionException e) {
            throw new WebServiceException(ModelerMessages.UNABLE_TO_CREATE_JAXB_CONTEXT(), e);
        }
        knownNamespaceURIs = new ArrayList<>();
        for (String namespace : bindingContext.getKnownNamespaceURIs()) {
            if (namespace.length() > 0) {
                if (!namespace.equals(SOAPNamespaceConstants.XSD) && !namespace.equals(SOAPNamespaceConstants.XMLNS))
                    knownNamespaceURIs.add(namespace);
            }
        }

        marshallers = new Pool.Marshaller(jaxbContext);

        //return getJAXBContext();
    }

    /**
     * @return returns non-null list of TypeReference
     */
    private List<TypeInfo> getAllTypeInfos() {
        List<TypeInfo> types = new ArrayList<>();
        Collection<JavaMethodImpl> methods = methodToJM.values();
        for (JavaMethodImpl m : methods) {
            m.fillTypes(types);
        }
        return types;
    }

    private void createBridgeMap(List<TypeReference> types) {
        for (TypeReference type : types) {
            Bridge bridge = jaxbContext.createBridge(type);
            bridgeMap.put(type, bridge);
        }
    }
    private void createBondMap(List<TypeInfo> types) {
        for (TypeInfo type : types) {
            XMLBridge binding = bindingContext.createBridge(type);
            xmlBridgeMap.put(type, binding);
        }
    }


    /**
     * @return true if <code>name</code> is the name
     * of a known fault name for the <code>Method method</code>
     */
    public boolean isKnownFault(QName name, Method method) {
        JavaMethodImpl m = getJavaMethod(method);
        for (CheckedExceptionImpl ce : m.getCheckedExceptions()) {
            if (ce.getDetailType().tagName.equals(name))
                return true;
        }
        return false;
    }

    /**
     * @return true if <code>ex</code> is a Checked Exception
     * for <code>Method m</code>
     */
    public boolean isCheckedException(Method m, Class ex) {
        JavaMethodImpl jm = getJavaMethod(m);
        for (CheckedExceptionImpl ce : jm.getCheckedExceptions()) {
            if (ce.getExceptionClass().equals(ex))
                return true;
        }
        return false;
    }

    /**
     * @return the <code>JavaMethod</code> representing the <code>method</code>
     */
    @Override
    public JavaMethodImpl getJavaMethod(Method method) {
        return methodToJM.get(method);
    }

    /**
     * @return the <code>JavaMethod</code> associated with the
     * operation named name
     */
    @Override
    public JavaMethodImpl getJavaMethod(QName name) {
        return nameToJM.get(name);
    }

    @Override
    public JavaMethod getJavaMethodForWsdlOperation(QName operationName) {
        return wsdlOpToJM.get(operationName);
    }


    /**
     * @return the <code>QName</code> associated with the
     * JavaMethod jm.
     *
     * @deprecated
     *      Use {@link JavaMethod#getOperationName()}.
     */
    @Deprecated
    public QName getQNameForJM(JavaMethodImpl jm) {
        for (Map.Entry<QName, JavaMethodImpl> entry : nameToJM.entrySet()) {
            JavaMethodImpl jmethod = entry.getValue();
            if (jmethod.getOperationName().equals(jm.getOperationName())){
               return entry.getKey();
            }
        }
        return null;
    }

    /**
     * @return a <code>Collection</code> of <code>JavaMethods</code>
     * associated with this <code>RuntimeModel</code>
     */
    @Override
    public final Collection<JavaMethodImpl> getJavaMethods() {
        return Collections.unmodifiableList(javaMethods);
    }

    void addJavaMethod(JavaMethodImpl jm) {
        if (jm != null)
            javaMethods.add(jm);
    }

    /**
     * Applies binding related information to the RpcLitPayload. The payload map is populated correctly
     * @return
     * Returns attachment parameters if/any.
     */
    private List<ParameterImpl> applyRpcLitParamBinding(JavaMethodImpl method, WrapperParameter wrapperParameter, WSDLBoundPortType boundPortType, Mode mode) {
        QName opName = new QName(boundPortType.getPortTypeName().getNamespaceURI(), method.getOperationName());
        WSDLBoundOperation bo = boundPortType.get(opName);
        Map<Integer, ParameterImpl> bodyParams = new HashMap<>();
        List<ParameterImpl> unboundParams = new ArrayList<>();
        List<ParameterImpl> attachParams = new ArrayList<>();
        for(ParameterImpl param : wrapperParameter.wrapperChildren){
            String partName = param.getPartName();
            if(partName == null)
                continue;

            ParameterBinding paramBinding = boundPortType.getBinding(opName,
                    partName, mode);
            if(paramBinding != null){
                if(mode == Mode.IN)
                    param.setInBinding(paramBinding);
                else if(mode == Mode.OUT || mode == Mode.INOUT)
                    param.setOutBinding(paramBinding);

                if(paramBinding.isUnbound()){
                        unboundParams.add(param);
                } else if(paramBinding.isAttachment()){
                    attachParams.add(param);
                }else if(paramBinding.isBody()){
                    if(bo != null){
                        WSDLPart p = bo.getPart(param.getPartName(), mode);
                        if(p != null)
                            bodyParams.put(p.getIndex(), param);
                        else
                            bodyParams.put(bodyParams.size(), param);
                    }else{
                        bodyParams.put(bodyParams.size(), param);
                    }
                }
            }

        }
        wrapperParameter.clear();
        for(int i = 0; i <  bodyParams.size();i++){
            ParameterImpl p = bodyParams.get(i);
            wrapperParameter.addWrapperChild(p);
        }

        //add unbounded parts
        for(ParameterImpl p:unboundParams){
            wrapperParameter.addWrapperChild(p);
        }
        return attachParams;
    }


    void put(QName name, JavaMethodImpl jm) {
        nameToJM.put(name, jm);
    }

    void put(Method method, JavaMethodImpl jm) {
        methodToJM.put(method, jm);
    }

    void putOp(QName opName, JavaMethodImpl jm) {
        wsdlOpToJM.put(opName, jm);
    }
    @Override
    public String getWSDLLocation() {
        return wsdlLocation;
    }

    void setWSDLLocation(String location) {
        wsdlLocation = location;
    }

    @Override
    public QName getServiceQName() {
        return serviceName;
    }

    @Override
    public WSDLPort getPort() {
        return port;
    }

    @Override
    public QName getPortName() {
        return portName;
    }

    @Override
    public QName getPortTypeName() {
        return portTypeName;
    }

    void setServiceQName(QName name) {
        serviceName = name;
    }

    void setPortName(QName name) {
        portName = name;
    }

    void setPortTypeName(QName name) {
        portTypeName = name;
    }

    /**
     * This is the targetNamespace for the WSDL containing the PortType
     * definition
     */
    void setTargetNamespace(String namespace) {
        targetNamespace = namespace;
    }

    /**
     * This is the targetNamespace for the WSDL containing the PortType
     * definition
     */
    @Override
    public String getTargetNamespace() {
        return targetNamespace;
    }

    String getDefaultSchemaNamespace() {
        String defaultNamespace = getTargetNamespace();
        if (defaultSchemaNamespaceSuffix == null) return defaultNamespace; 
        if (!defaultNamespace.endsWith("/")) {
            defaultNamespace += "/";
        }
        return (defaultNamespace + defaultSchemaNamespaceSuffix);
    }

    @NotNull
    @Override
    public QName getBoundPortTypeName() {
        assert portName != null;
        return new QName(portName.getNamespaceURI(), portName.getLocalPart()+"Binding");
    }

    /**
     * Adds additional classes obtained from {@link XmlSeeAlso} annotation. In starting
     * from wsdl case these classes would most likely be JAXB ObjectFactory that references other classes.
     */
    public void addAdditionalClasses(Class... additionalClasses) {
        this.additionalClasses.addAll(Arrays.asList(additionalClasses));
    }
    
    public Databinding getDatabinding() {
		return databinding;
	}

	public void setDatabinding(Databinding wsRuntime) {
		this.databinding = wsRuntime;
	}
	
	public WSBinding getWSBinding() {
		return wsBinding;
	}
	
    public Class getContractClass() {
		return contractClass;
	}

	public Class getEndpointClass() {
		return endpointClass;
	}

	private List<Class> additionalClasses = new ArrayList<>();

    private Pool.Marshaller marshallers;
    /**
     * @deprecated
     */
    protected JAXBRIContext jaxbContext;
    protected BindingContext bindingContext;
    private String wsdlLocation;
    private QName serviceName;
    private QName portName;
    private QName portTypeName;
    private Map<Method,JavaMethodImpl> methodToJM = new HashMap<>();
    /**
     * Payload QName to the method that handles it.
     */
    private Map<QName,JavaMethodImpl> nameToJM = new HashMap<>();
    /**
     * Wsdl Operation QName to the method that handles it.
     */
    private Map<QName, JavaMethodImpl> wsdlOpToJM = new HashMap<>();

    private List<JavaMethodImpl> javaMethods = new ArrayList<>();
    private final Map<TypeReference, Bridge> bridgeMap = new HashMap<>();
    private final Map<TypeInfo, XMLBridge> xmlBridgeMap = new HashMap<>();
    protected final QName emptyBodyName = new QName("");
    private String targetNamespace = "";
    private List<String> knownNamespaceURIs = null;
    private WSDLPort port;
    private final WebServiceFeatureList features;
    private Databinding databinding;
    BindingID bindingId;
    protected Class contractClass;
	protected Class endpointClass;
	protected ClassLoader classLoader = null;
	protected WSBinding wsBinding;
	protected BindingInfo databindingInfo;
	protected String defaultSchemaNamespaceSuffix;
	private static final Logger LOGGER = Logger.getLogger(AbstractSEIModelImpl.class.getName());
}
