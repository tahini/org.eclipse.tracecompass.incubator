/*******************************************************************************
 * Copyright (c) 2019 École Polytechnique de Montréal
 *
 * All rights reserved. This program and the accompanying materials are
 * made available under the terms of the Eclipse Public License v1.0 which
 * accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package org.eclipse.tracecompass.incubator.filters.core.stubs;

import java.util.concurrent.CompletableFuture;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.lsp4j.MessageActionItem;
import org.eclipse.lsp4j.MessageParams;
import org.eclipse.lsp4j.PublishDiagnosticsParams;
import org.eclipse.lsp4j.ShowMessageRequestParams;
import org.eclipse.lsp4j.services.LanguageClient;
import org.eclipse.tracecompass.incubator.internal.filters.core.shared.LspObservable;
import org.eclipse.tracecompass.incubator.internal.filters.core.shared.LspObserver;

/**
 * LanguageClient stub: Wrap around LanguageClientImpl Helps to store data about
 * the real implementation. Use the LSPClientMockup to store data from calls
 *
 * @author Maxime Thibault
 * @author David-Alexandre Beaupre
 *
 */
public class LSPClientStub implements LanguageClient, LspObservable {

    public LSPClientMockup fMockup = new LSPClientMockup();
    private final Stub fStub;
    public LspObserver fObserver;

    public LSPClientStub(Stub stub) {
        fStub = stub;
    }

    @Override
    public void telemetryEvent(Object object) {
        // Not implemented
    }

    @Override
    public void publishDiagnostics(PublishDiagnosticsParams diagnostics) {
        // Store data into mockup
        fMockup.fDiagnosticsReceived = diagnostics.getDiagnostics();
        // Call the real Client implementation
        fStub.getProxyClient().publishDiagnostics(diagnostics);
        // Count this transaction
        fStub.count();
    }

    @Override
    public void showMessage(MessageParams messageParams) {
        // Not implemented
    }

    @Override
    public CompletableFuture<MessageActionItem> showMessageRequest(ShowMessageRequestParams requestParams) {
        return null;
    }

    @Override
    public void logMessage(MessageParams message) {
        // Not implemented
    }

    @Override
    public void register(@NonNull LspObserver obs) {
        fObserver = obs;
    }

}