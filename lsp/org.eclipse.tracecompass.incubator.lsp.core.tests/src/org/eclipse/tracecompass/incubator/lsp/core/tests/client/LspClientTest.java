/*******************************************************************************
 * Copyright (c) 2019 École Polytechnique de Montréal
 *
 * All rights reserved. This program and the accompanying materials are
 * made available under the terms of the Eclipse Public License v1.0 which
 * accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package org.eclipse.tracecompass.incubator.lsp.core.tests.client;

import static org.junit.Assert.assertEquals;

import java.io.IOException;

import org.eclipse.tracecompass.incubator.lsp.core.environment.TestEnvironment;
import org.junit.Test;

public class LspClientTest {

    /**
     * Simple hello world tests. LSPClient send 'hello' to LSPServer
     *
     * @throws InterruptedException
     * @throws IOException
     */
    @Test
    public void hello() throws InterruptedException, IOException {
        String input = "Hello";
        String uri = "Mamma mia";
        /**
         * We expect 5 transactions: 1.DidOpen: client -> Server 2.DidChange:
         * client -> server 3.publishDiagnostics: server -> client
         * 4.syntaxHighlight: client <-> server 5.documentColor: client
         * <->server
         */
        TestEnvironment te = new TestEnvironment(5);
        te.getClient().getLanguageClient().tellDidOpen(uri);
        te.getClient().notify(uri, input, input.length());

        // Lock till the transactions we're expecting is not over
        te.waitForTransactionToTerminate();

        // Check mockup for stored values
        assertEquals(input, te.getStub().getServerStub().getTextDocumentService().fMockup.fInputReceived);
        assertEquals(0, te.getStub().getClientStub().fMockup.fDiagnosticsReceived.size());
    }

    /**
     * TODO: ADD MORE TESTS!!
     */

}
