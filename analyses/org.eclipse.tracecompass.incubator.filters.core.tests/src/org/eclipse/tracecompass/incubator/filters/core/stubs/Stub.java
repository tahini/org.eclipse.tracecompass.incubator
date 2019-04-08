/*******************************************************************************
 * Copyright (c) 2019 École Polytechnique de Montréal
 *
 * All rights reserved. This program and the accompanying materials are
 * made available under the terms of the Eclipse Public License v1.0 which
 * accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package org.eclipse.tracecompass.incubator.filters.core.stubs;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.Semaphore;

import org.eclipse.lsp4j.jsonrpc.Launcher;
import org.eclipse.lsp4j.launch.LSPLauncher;
import org.eclipse.lsp4j.services.LanguageClient;
import org.eclipse.lsp4j.services.LanguageServer;

/**
 * A complete stub to test LSP client/server communication
 *
 * @author Maxime Thibault
 *
 */
public class Stub {

    private static LanguageServer fProxyServer;
    private static LanguageClient fProxyClient;

    private LSPClientStub fClientStub;
    private LSPServerStub fServerStub;
    private final FakeClientStub fFakeClientStub;

    private final Semaphore fTransactionsLock;

    /**
     * Create the stub environment. This environment contains the client and the
     * server stub Both can access the server and client proxy.
     *
     * @param transactionLock
     */
    public Stub(Semaphore transactionLock) {
        fTransactionsLock = transactionLock;
        fFakeClientStub = new FakeClientStub();
    }

    /**
     * Initialize the serverStub
     *
     * @param streamFromClient
     *            Stream to read from client
     * @param streamToClient
     *            Strean ti write to client
     */
    public void initServer(InputStream streamFromClient, OutputStream streamToClient) {
        // Start the stub server
        fServerStub = new LSPServerStub(this);
        Launcher<LanguageClient> serverLauncher = LSPLauncher.createServerLauncher(fServerStub, streamFromClient, streamToClient);
        fProxyClient = serverLauncher.getRemoteProxy();
        serverLauncher.startListening();

    }

    /**
     * Initialize the clientStub
     *
     * @param streamFromServer
     *            Stream to read from server
     * @param streamToServer
     *            Stream to write to server
     */
    public void initClient(InputStream streamFromServer, OutputStream streamToServer) {
        // Start the stub client
        fClientStub = new LSPClientStub(this);
        Launcher<LanguageServer> clientLauncher = LSPLauncher.createClientLauncher(fClientStub, streamFromServer, streamToServer);
        fProxyServer = clientLauncher.getRemoteProxy();
        fClientStub.register(fFakeClientStub);
        clientLauncher.startListening();
    }

    /**
     * Return the stub observer Not used yet
     *
     * @return
     */
    public FakeClientStub getObserver() {
        return fFakeClientStub;
    }

    /**
     * Return the clientStub
     *
     * @return
     */
    public LSPClientStub getClientStub() {
        return fClientStub;
    }

    /**
     * Return the serverStub
     *
     * @return
     */
    public LSPServerStub getServerStub() {
        return fServerStub;
    }

    /**
     * Return the proxy of the real server implementation
     *
     * @return
     */
    public LanguageServer getProxyServer() {
        return fProxyServer;
    }

    /**
     * Return the proxy of the real client implementation
     *
     * @return
     */
    public LanguageClient getProxyClient() {
        return fProxyClient;
    }

    /**
     * Increment semaphore (used for synchronization based on the number of
     * expected transaction)
     */
    public void count() {
        fTransactionsLock.release();
    }

}
