/*
 * Copyright (c) 1997, 2022 Oracle and/or its affiliates. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0, which is available at
 * http://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package com.sun.xml.ws.util.pipe;

import com.sun.istack.NotNull;
import com.sun.xml.ws.api.pipe.ClientTubeAssemblerContext;
import com.sun.xml.ws.api.pipe.ServerTubeAssemblerContext;
import com.sun.xml.ws.api.pipe.Tube;
import com.sun.xml.ws.api.pipe.TubelineAssembler;

/**
 * Default Pipeline assembler for JAX-WS client and server side runtimes. It
 * assembles various pipes into a pipeline that a message needs to be passed
 * through.
 *
 * @author Jitendra Kotamraju
 */
public class StandaloneTubeAssembler implements TubelineAssembler {

    @Override
    @NotNull
    public Tube createClient(ClientTubeAssemblerContext context) {
        Tube head = context.createTransportTube();
        head = context.createSecurityTube(head);
        if (dump) {
            // for debugging inject a dump pipe. this is left in the production code,
            // as it would be very handy for a trouble-shooting at the production site.
            head = context.createDumpTube("client", System.out, head);
        }
        head = context.createWsaTube(head);
        head = context.createClientMUTube(head);
        head = context.createValidationTube(head);
        return context.createHandlerTube(head);        
    }

    /**
     * On Server-side, HandlerChains cannot be changed after it is deployed.
     * During assembling the Pipelines, we can decide if we really need a
     * SOAPHandlerPipe and LogicalHandlerPipe for a particular Endpoint.
     */
    @Override
    public Tube createServer(ServerTubeAssemblerContext context) {
        Tube head = context.getTerminalTube();
        head = context.createValidationTube(head);
        head = context.createHandlerTube(head);
        head = context.createMonitoringTube(head);
        head = context.createServerMUTube(head);
        head = context.createWsaTube(head);
        if (dump) {
            // for debugging inject a dump pipe. this is left in the production code,
            // as it would be very handy for a trouble-shooting at the production site.
            head = context.createDumpTube("server", System.out, head);
        }
        head = context.createSecurityTube(head);
        return head;
    }

    /**
     * Are we going to dump the message to System.out?
     */
    public static final boolean dump;

    static {
        boolean b = false;
        try {
            b = Boolean.getBoolean(StandaloneTubeAssembler.class.getName()+".dump");
        } catch (Throwable t) {
            // treat it as false
        }
        dump = b;
    }
}
