/*******************************************************************************
 * Copyright (c) 2019 École Polytechnique de Montréal
 *
 * All rights reserved. This program and the accompanying materials are
 * made available under the terms of the Eclipse Public License v1.0 which
 * accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package org.eclipse.tracecompass.incubator.lsp.core.tests.server;

import static org.junit.Assert.assertEquals;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.ExecutionException;

import org.eclipse.lsp4j.Color;
import org.eclipse.lsp4j.ColorInformation;
import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.tracecompass.incubator.internal.lsp.core.server.SyntaxHighlighting;
import org.eclipse.tracecompass.incubator.lsp.core.environment.TestEnvironment;
import org.junit.Test;

public class ServerTest {

    @Test
    public void ValidityReply() throws InterruptedException, IOException {
        String[] strArray = { "TID", "TID==", "TID==28" };
        int[] validity = { 0, 1, 0 };

        String uri = "Arriba";

        for (int i = 0; i < strArray.length; i++) {
            /**
             * We expect 5 transactions:
             * 1.DidOpen: client -> Server
             * 2.DidChange: client -> server
             * 3.publishDiagnostics: server -> client
             * 4.syntaxHighlight: client <-> server
             * 5.documentColor: client <->server
             */
            TestEnvironment te = new TestEnvironment(5);
            te.getClient().getLanguageClient().tellDidOpen(uri);
            te.getClient().getLanguageClient().tellDidChange(uri, strArray[i], strArray[i].length());

            // Wait for transactions to complete
            te.waitForTransactionToTerminate();

            // Check mockup for stored values
            assertEquals(validity[i], te.getStub().getClientStub().fMockup.fDiagnosticsReceived.size());
        }
    }

    @Test
    public void ColorHighlightReply() throws InterruptedException, IOException, ExecutionException {
        String input = "TID == 42 || PID != 12 && Poly matches Ericsson";
        Color[] expectedColors = {SyntaxHighlighting.TEXT_COLOR,
                SyntaxHighlighting.OPERATION_COLOR,
                SyntaxHighlighting.TEXT_COLOR,
                SyntaxHighlighting.SEPARATOR_COLOR,
                SyntaxHighlighting.TEXT_COLOR,
                SyntaxHighlighting.OPERATION_COLOR,
                SyntaxHighlighting.TEXT_COLOR,
                SyntaxHighlighting.SEPARATOR_COLOR,
                SyntaxHighlighting.TEXT_COLOR,
                SyntaxHighlighting.OPERATION_COLOR,
                SyntaxHighlighting.TEXT_COLOR};

        String uri = "Arriba";

        TestEnvironment tEnvironment = new TestEnvironment(5);
        tEnvironment.getClient().getLanguageClient().tellDidOpen(uri);
        tEnvironment.getClient().getLanguageClient().tellDidChange(uri, input, input.length());

        tEnvironment.waitForTransactionToTerminate();

        List<ColorInformation> colors = tEnvironment.getStub().getServerStub().getTextDocumentService().fMockup.fColorsReceived.get();

        for (int j = 0; j < colors.size(); j++) {
            assertEquals(expectedColors[j], colors.get(j).getColor());
        }
    }

    @Test
    public void CompletionReply() throws InterruptedException, IOException, ExecutionException {
        String input = "(TID == 42 || PID != 12 && Poly matches Ericsson)";
        int[] cursors = {5, 7, 23, input.length()};
        int[] expectedLength = {9, 0, 2, 2};

        String uri = "Arriba";

        for (int i = 0; i < cursors.length; i++) {
            TestEnvironment tEnvironment = new TestEnvironment(5);
            tEnvironment.getClient().getLanguageClient().tellDidOpen(uri);
            tEnvironment.getClient().getLanguageClient().tellDidChange(uri, input, cursors[i]);

            tEnvironment.waitForTransactionToTerminate();

            List<CompletionItem> completionItems = tEnvironment.getStub().getServerStub().getTextDocumentService().fMockup.fCompletionsReceived.get().getLeft();

            assertEquals(expectedLength[i], completionItems.size());
        }
    }
}
