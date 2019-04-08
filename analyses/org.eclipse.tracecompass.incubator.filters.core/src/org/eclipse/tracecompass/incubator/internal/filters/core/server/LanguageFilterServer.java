/*******************************************************************************
 * Copyright (c) 2019 École Polytechnique de Montréal
 *
 * All rights reserved. This program and the accompanying materials are
 * made available under the terms of the Eclipse Public License v1.0 which
 * accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package org.eclipse.tracecompass.incubator.internal.filters.core.server;

import java.util.concurrent.CompletableFuture;

import org.eclipse.lsp4j.InitializeParams;
import org.eclipse.lsp4j.InitializeResult;
import org.eclipse.lsp4j.services.LanguageClient;
import org.eclipse.lsp4j.services.LanguageClientAware;
import org.eclipse.lsp4j.services.LanguageServer;
import org.eclipse.lsp4j.services.TextDocumentService;
import org.eclipse.lsp4j.services.WorkspaceService;

/**
 * LanguageServer implementation for the tracecompass FilterBox
 *
 * @author Maxime Thibault
 * @author David-Alexandre Beaupre
 * @author Remi Croteau
 */
public class LanguageFilterServer implements LanguageServer, LanguageClientAware {

    private final TextDocumentService filterBoxService;
    private final WorkspaceService filterWorkspaceService;
    private LanguageClient fClient;

    /**
     * Server constructor
     */
    public LanguageFilterServer() {
        filterBoxService = new FilterBoxService(this);
        filterWorkspaceService = new FilterWorkspaceService();
    }

    @Override
    public CompletableFuture<InitializeResult> initialize(InitializeParams params) {
        return CompletableFuture.completedFuture(new InitializeResult());
    }

    @Override
    public CompletableFuture<Object> shutdown() {
        // Nothing to do
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public void exit() {
        // Nothing to do
    }

    @Override
    public TextDocumentService getTextDocumentService() {
        return filterBoxService;
    }

    @Override
    public WorkspaceService getWorkspaceService() {
        return filterWorkspaceService;
    }

    @Override
    public void connect(LanguageClient client) {
        fClient = client;
    }

    /**
     * Used by the filterBoxService when it needs to make a call on the client.
     *
     * @return LanguageClient
     */
    public LanguageClient getClient() {
        return fClient;
    }
}