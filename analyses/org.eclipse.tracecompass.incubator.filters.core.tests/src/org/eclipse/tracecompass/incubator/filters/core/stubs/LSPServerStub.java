/*******************************************************************************
 * Copyright (c) 2019 Ecole Polytechnique de Montreal
 *
 * All rights reserved. This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License v1.0 which
 * accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package org.eclipse.tracecompass.incubator.filters.core.stubs;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import org.eclipse.lsp4j.InitializeParams;
import org.eclipse.lsp4j.InitializeResult;
import org.eclipse.lsp4j.services.LanguageServer;
import org.eclipse.lsp4j.services.WorkspaceService;
import org.eclipse.tracecompass.incubator.internal.filters.core.server.FilterWorkspaceService;

/**
 * LanguageServer stub: Wrap around an actual LanguageServerImpl Helps to store
 * data about the real implementation. Use the LSPServerMockup to store data
 * from calls
 *
 * @author Maxime Thibault
 *
 */
public class LSPServerStub implements LanguageServer {

    public LSPServerMockup fMockup = new LSPServerMockup();
    private final FilterBoxServiceStub fFilterBoxService;
    private final WorkspaceService fFilterWorkspaceService;
    private final Stub fStub;

    /**
     *
     * @param languageServer:
     *            The real LanguageServer implementation
     * @param transactionsLock
     *            use this semaphore to count the transactions and use it in the
     *            TestEnvironment
     */
    LSPServerStub(Stub stub) {
        fStub = stub;
        fFilterBoxService = new FilterBoxServiceStub(stub);
        fFilterWorkspaceService = new FilterWorkspaceService();
    }

    @Override
    public CompletableFuture<InitializeResult> initialize(InitializeParams params) {
        return CompletableFuture.completedFuture(new InitializeResult());
    }

    @Override
    public CompletableFuture<Object> shutdown() {
        Object returnVal = null;
        try {
            returnVal = fStub.getProxyServer().shutdown().get();
        } catch (InterruptedException | ExecutionException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        return CompletableFuture.completedFuture(returnVal);
    }

    @Override
    public void exit() {
        fStub.getProxyServer().exit();
        // Count this transaction
        fStub.count();
    }

    @Override
    public FilterBoxServiceStub getTextDocumentService() {
        // Return the FilterBoxServiceStub
        return fFilterBoxService;
    }

    @Override
    public WorkspaceService getWorkspaceService() {
        return fFilterWorkspaceService;
    }

}