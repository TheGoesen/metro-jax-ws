/*
 * Copyright (c) 1997, 2020 Oracle and/or its affiliates. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0, which is available at
 * http://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package async.wsdl_rpclit.server;

import jakarta.jws.WebService;
import jakarta.xml.ws.Holder;

@WebService(endpointInterface="async.wsdl_rpclit.server.Hello")
public class HelloService implements Hello {
    public void hello(Holder<HelloType> req){
        System.out.println("Hello_PortType_Impl received: " + req.value.getArgument() +
            ", " + req.value.getExtra());
    }

    public int hello2(Holder<HelloType> req, String name){
        if(name.equals("foo"))
            return 1234;
        return -1;
    }

    public void hello1(HelloType req, HelloType inHeader,
            Holder<HelloType> resp, Holder<HelloType> outHeader){
        resp.value = req;
        outHeader.value = inHeader;
    }

    public HelloType hello4(HelloType req, HelloType inHeader,
            Holder<HelloType> resp){
        resp.value = req;
        return inHeader;
    }

    public int hello0(int param_in){
        return param_in;
    }

}
