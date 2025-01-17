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
public class WsImportMojoITCase {

    private static final File PROJECTS_DIR = new File(System.getProperty("it.projects.dir"));
    private File project;

    public WsImportMojoITCase() {
    }

    @Test
    public void wsimport22() throws IOException {
        project = new File(PROJECTS_DIR, "wsimport22");
        String v = System.getProperty("jaxws-ri.version");
        //remove 'promoted-' from the version string if needed
        int i = v.indexOf('-');
        int j = v.lastIndexOf('-');
        String version = i != j ? v.substring(0, i) + v.substring(j) : v;
        
        //check HelloWs
        Assertions.assertFilePresent(project, "target/classes/org/jvnet/jax_ws_commons/wsimport/test/HelloWs.class");
        Assertions.assertFileNotPresent(project, "target/test-classes/org/jvnet/jax_ws_commons/wsimport/test/HelloWs.class");
        Assertions.assertFilePresent(project, "target/generated-sources/wsimport/org/jvnet/jax_ws_commons/wsimport/test/HelloWs_Service.java");
        //this needs to be fixed as there should be a way to not generate sources
        //assertFileNotPresent(project, "target/jaxws/wsimport/org/jvnet/jax_ws_commons/wsimport/test/HelloWs.java");

        //check sample.wsdl (url)
        Assertions.assertFilePresent(project, "target/custom/classes/org/jvnet/jaxwsri/sample/GreetersPortT.class");
        Assertions.assertFilePresent(project, "target/custom/sources/org/jvnet/jaxwsri/sample/MyGreeter.java");
        //-wsdlLocation
        Assertions.assertFileContains(project, "target/custom/sources/org/jvnet/jaxwsri/sample/MyGreeter.java", "http://example.com:43210/my?wsdl");
        //default dependency on 2.2.x
        Assertions.assertFileContains(project, "target/custom/sources/org/jvnet/jaxwsri/sample/GreetersPortT.java", "JAX-WS RI " + version);
        //-target 2.0
//        assertFileContains(project, "target/custom/sources/org/jvnet/jaxwsri/sample/GreetersPortT.java", "Generated source version: 2.0");
        //-XadditionalHeaders
        Assertions.assertFileContains(project, "target/custom/sources/org/jvnet/jaxwsri/sample/GreetersPortT.java", "Holder<String> additionalHeader2");

        //check AddService
        Assertions.assertFilePresent(project, "target/test-classes/wsimport/test/AddService.class");
        Assertions.assertFilePresent(project, "target/test-classes/wsimport/test/schema/SumType.class");
        Assertions.assertFilePresent(project, "target/generated-sources/test-wsimport/wsimport/test/SumUtil.java");
        Assertions.assertFileNotPresent(project, "target/classes/wsimport/test/AddService.class");
        Assertions.assertFileContains(project, "target/generated-sources/test-wsimport/wsimport/test/SumUtil.java", "JAX-WS RI " + version);
    }

    @Test
    public void jaxwscommons4() throws IOException {
        project = new File(PROJECTS_DIR, "jaxwscommons-4");

        Assertions.assertFilePresent(project, "target/generated-sources/wsimport/sample/ProcessOrder_Service.java");
        Assertions.assertFilePresent(project, "target/generated-sources/wsimport/tests/CustomOrder_Service.java");
    }

    @Test
    public void jaxwscommons49() throws IOException {
        project = new File(PROJECTS_DIR, "jaxwscommons-49");

        Assertions.assertFilePresent(project, "foo/AddService.wsdl");
        Assertions.assertFilePresent(project, "src/main/java/org/jvnet/jax_ws_commons/wsimport/test/AddService_Service.java");
        Assertions.assertFilePresent(project, "target/classes/org/jvnet/jax_ws_commons/wsimport/test/AddService.class");
    }

    @Test
    public void jaxwscommons62() throws IOException {
        project = new File(PROJECTS_DIR, "jaxwscommons-62");

        Assertions.assertFileContains(project, "target/generated-sources/wsimport/test/jaxwscommons_62/A.java", "http://example.com/mywebservices/a.wsdl");
        Assertions.assertFileContains(project, "target/generated-sources/wsimport/test/jaxwscommons_62/B.java", "http://example.com/mywebservices/b/b.wsdl");
        Assertions.assertFileContains(project, "target/generated-sources/wsimport/test/jaxwscommons_62/C.java", "jaxwscommons-62/src/mywsdls/c.wsdl");
    }

    @Test
    public void jaxwscommons87() throws IOException {
        project = new File(PROJECTS_DIR, "jaxwscommons-87");

        Assertions.assertFileNotPresent(project, "org/jvnet/jax_ws_commons/jaxws/test/EchoService_Service_handler.xml");
        Assertions.assertFilePresent(project, "child/target/classes/org/jvnet/jax_ws_commons/jaxws/test/EchoService_Service_handler.xml");
    }

    @Test(enabled = false)
    public void metro_wsimport() throws IOException {
        project = new File(PROJECTS_DIR, "metro");

        Assertions.assertFilePresent(project, "target/gen-src/org/example/duke/AddNumbersService_AddNumbersPortImpl.java");
        Assertions.assertFilePresent(project, "target/classes/org/example/duke/AddNumbersService_AddNumbersPortImpl.class");
    }

}
