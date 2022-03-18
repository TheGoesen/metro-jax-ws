/*
 * Copyright (c) 1997, 2020 Oracle and/or its affiliates. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0, which is available at
 * http://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package async.wsdl_rpclit.client;

import junit.framework.TestCase;

import jakarta.xml.ws.AsyncHandler;
import jakarta.xml.ws.Response;
import java.util.concurrent.ExecutionException;

public class HelloCallbackHandler extends TestCase implements AsyncHandler<HelloType> {

    /*
     * (non-Javadoc)
     *
     * @see javax.xml.rpc.AsyncHandler#handleResponse(javax.xml.rpc.Response)
     */
    public void handleResponse(Response<HelloType> response) {
        try {
            HelloType output = response.get();
            assertEquals("foo", output.getArgument());
            assertEquals("bar", output.getExtra());
        } catch (ExecutionException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}
