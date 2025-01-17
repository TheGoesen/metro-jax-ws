/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2011, 2022 Oracle and/or its affiliates. All rights reserved.
 *
 * Oracle licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jvnet.jax_ws_commons.jaxws;

import java.io.File;
import java.io.IOException;

import org.testng.annotations.Test;

/**
 *
 * @author Lukas Jungmann
 */
public class WsGenMojoITCase {

    private static final File PROJECTS_DIR = new File(System.getProperty("it.projects.dir"));
    private File project;

    public WsGenMojoITCase() {
    }

    @Test
    public void wsgen22() throws IOException {
        project = new File(PROJECTS_DIR, "wsgen22");
        String v = System.getProperty("jaxws-ri.version");
        //remove 'promoted-' from the version string if needed
        int i = v.indexOf('-');
        int j = v.lastIndexOf('-');
        String version = i != j ? v.substring(0, i) + v.substring(j) : v;

        //check EchoService
        Assertions.assertFilePresent(project, "target/custom/sources/org/jvnet/jax_ws_commons/jaxws/test/jaxws/EchoResponse.java");
        Assertions.assertFilePresent(project, "target/custom/classes/org/jvnet/jax_ws_commons/jaxws/test/jaxws/Echo.class");
        Assertions.assertFilePresent(project, "target/classes/org/jvnet/jax_ws_commons/jaxws/test/EchoService.class");
        //-wsdl[...]
        Assertions.assertFilePresent(project, "target/wsdl/EchoService.wsdl");
        //-inlineSchemas
        Assertions.assertFileContains(project, "target/wsdl/EchoService.wsdl", "xs:complexType");
        Assertions.assertFileNotPresent(project, "target/wsdl/EchoService_schema1.xsd");
        Assertions.assertFileNotPresent(project, "target/jaxws/wsgen/wsdl/EchoService.wsdl");
        Assertions.assertFileNotPresent(project, "target/generated-sources/wsdl/EchoService.wsdl");
        Assertions.assertFileNotPresent(project, "target/generated-sources/test-wsdl/EchoService.wsdl");
        //-wsdl:Xsoap12 + -extension
        Assertions.assertFileContains(project, "target/wsdl/EchoService.wsdl", "http://schemas.xmlsoap.org/wsdl/soap12/");
        //default dependency on 2.2.x
        Assertions.assertFileContains(project, "target/wsdl/EchoService.wsdl", "JAX-WS RI " + version);

        //check AddService
        Assertions.assertFilePresent(project, "target/classes/org/jvnet/jax_ws_commons/jaxws/test/jaxws/Add.class");
        Assertions.assertFilePresent(project, "target/classes/org/jvnet/jax_ws_commons/jaxws/test/AddService.class");
        Assertions.assertFileNotPresent(project, "target/classes/org/jvnet/jax_ws_commons/jaxws/test/jaxws/Add.java");
        Assertions.assertFileNotPresent(project, "target/classes/org/jvnet/jax_ws_commons/jaxws/test/AddService.java");
        Assertions.assertFileNotPresent(project, "target/wsdl/AddService.wsdl");
        Assertions.assertFileNotPresent(project, "target/jaxws/wsgen/wsdl/AddService.wsdl");

        //check TService
        Assertions.assertFilePresent(project, "target/generated-sources/test-wsdl/TService.wsdl");
        Assertions.assertFilePresent(project, "target/generated-sources/test-wsdl/ExService.wsdl");
        Assertions.assertFilePresent(project, "target/test-classes/org/jvnet/jax_ws_commons/jaxws/test/TService.class");
        Assertions.assertFilePresent(project, "target/test-classes/org/jvnet/jax_ws_commons/jaxws/test/jaxws/HelloResponse.class");
        Assertions.assertFilePresent(project, "target/generated-sources/test-wsgen/org/jvnet/jax_ws_commons/jaxws/test/jaxws/HelloResponse.java");
        Assertions.assertFileNotPresent(project, "target/test-classes/org/jvnet/jax_ws_commons/jaxws/test/TService.java");
        Assertions.assertFileNotPresent(project, "target/test-classes/org/jvnet/jax_ws_commons/jaxws/test/jaxws/HelloResponse.java");
        //default dependency on 2.2.x
        Assertions.assertFileContains(project, "target/generated-sources/test-wsdl/ExService.wsdl", "JAX-WS RI " + version);
        //-portname
        Assertions.assertFileContains(project, "target/generated-sources/test-wsdl/ExService.wsdl", "port name=\"ExPort\"");
        //-servicename
        Assertions.assertFileContains(project, "target/generated-sources/test-wsdl/ExService.wsdl", "service name=\"ExService\"");

        //package wsdl
        Assertions.assertJarContains(project, "mojo.it.wsgentest22-2.2.6.jar", "META-INF/wsdl/EchoService.wsdl");
        Assertions.assertJarNotContains(project, "mojo.it.wsgentest22-2.2.6.jar", "META-INF/wsdl/EchoService_schema1.xsd");
        Assertions.assertJarNotContains(project, "mojo.it.wsgentest22-2.2.6.jar", "META-INF/EchoService_schema.xsd");
        Assertions.assertJarNotContains(project, "mojo.it.wsgentest22-2.2.6.jar", "EchoService_schema.xsd");
        Assertions.assertJarNotContains(project, "mojo.it.wsgentest22-2.2.6.jar", "META-INF/wsdl/ExService.wsdl");
        Assertions.assertJarNotContains(project, "mojo.it.wsgentest22-2.2.6.jar", "ExService.wsdl");
    }

    @Test
    public void jaxwscommons43() throws IOException {
        project = new File(PROJECTS_DIR, "jaxwscommons-43");

        Assertions.assertFilePresent(project, "target/classes/tests/jaxwscommons43/jaxws/Bye.class");
        Assertions.assertFilePresent(project, "target/generated-sources/wsdl/WsImplAService.wsdl");
        Assertions.assertFilePresent(project, "target/generated-sources/wsdl/WsImplBService.wsdl");
        Assertions.assertFileContains(project, "build.log", "No @jakarta.jws.WebService found.");
        Assertions.assertFileContains(project, "build.log", "Skipping tests, nothing to do.");
    }

    @Test
    public void jaxwscommons3() throws IOException {
        project = new File(PROJECTS_DIR, "jaxwscommons-3");

        Assertions.assertFilePresent(project, "target/cst/WEB-INF/wsdl/NewWebService.wsdl");
        Assertions.assertJarContains(project, "jaxwscommons-3-1.0.war", "WEB-INF/wsdl/NewWebService.wsdl");
    }

    @Test
    public void jaxwscommons91() throws IOException {
        project = new File(PROJECTS_DIR, "jaxwscommons-91");

        Assertions.assertFilePresent(project, "target/generated-sources/wsgen/com/mycompany/mavenproject2/jaxws/CustomExBean.java");
        Assertions.assertFilePresent(project, "target/generated-sources/wsdl/NewWebService.wsdl");
    }

    @Test
    public void jaxwscommons103() throws IOException {
        project = new File(PROJECTS_DIR, "jaxwscommons-103/ws");

        Assertions.assertJarContains(project, "jaxwscommons-103.ws.war", "WEB-INF/wsdl/EchoImplService.wsdl");
        Assertions.assertJarContains(project, "jaxwscommons-103.ws.war", "WEB-INF/wsdl/EchoImplService_schema1.xsd");
        Assertions.assertJarContains(project, "jaxwscommons-103.ws.war", "WEB-INF/classes/metadata-echo.xml");
    }

}
