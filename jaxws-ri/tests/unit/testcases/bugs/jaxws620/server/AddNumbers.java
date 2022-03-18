/*
 * Copyright (c) 1997, 2020 Oracle and/or its affiliates. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0, which is available at
 * http://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package bugs.jaxws620.server;


import jakarta.jws.WebMethod;
import jakarta.jws.WebParam;
import jakarta.jws.WebResult;
import jakarta.jws.WebService;
import jakarta.xml.ws.RequestWrapper;
import jakarta.xml.ws.ResponseWrapper;


/**
 * This class was generated by 
 * Interstage Java EE (JAX-WS 2.1.3-r3242, WSIT-Runtime 1.0-r3242M) 09/02/2008 02:04:45
 * Generated source version: 2.1
 * 
 */
@WebService(name = "AddNumbers", targetNamespace = "http://duke.example.org")
public interface AddNumbers {


    /**
     * 
     * @param arg1
     * @param arg0
     * @return
     *     returns int
     */
    @WebMethod
    @WebResult(targetNamespace = "http://duke.example.org")
    @RequestWrapper(localName = "addNumbers", targetNamespace = "http://duke.example.org", className = "dispatch.generate.client.AddNumbers")
    @ResponseWrapper(localName = "addNumbersResponse", targetNamespace = "http://duke.example.org", className = "dispatch.generate.client.AddNumbersResponse")
    public int addNumbers(
            @WebParam(name = "arg0", targetNamespace = "http://duke.example.org")
            int arg0,
            @WebParam(name = "arg1", targetNamespace = "http://duke.example.org")
            int arg1);

}
