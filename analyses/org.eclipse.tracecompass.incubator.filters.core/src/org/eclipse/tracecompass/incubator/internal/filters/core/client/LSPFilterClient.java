/*******************************************************************************
 * Copyright (c) 2019 École Polytechnique de Montréal
 *
 * All rights reserved. This program and the accompanying materials are
 * made available under the terms of the Eclipse Public License v1.0 which
 * accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package org.eclipse.tracecompass.incubator.internal.filters.core.client;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.UnknownHostException;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.lsp4j.jsonrpc.Launcher;
import org.eclipse.lsp4j.launch.LSPLauncher;
import org.eclipse.lsp4j.services.LanguageServer;
import org.eclipse.tracecompass.incubator.internal.filters.core.shared.Configuration;
import org.eclipse.tracecompass.incubator.internal.filters.core.shared.LspObserver;

import com.google.common.annotations.VisibleForTesting;

/**
 * LSP Client simplification that offers an API to its observer. The underlying
 * class is the LanguageClient implementation for the tracecompass FilterBox.
 *
 * @author Maxime Thibault
 *
 */
public class LSPFilterClient {

    private LanguageFilterClient fLanguageClient;
    private Socket fSocket;

    /**
     * Create client: -Connect to server with socket from default hostname and
     * port -Register an observer who can use client API and get notified when
     * server responds
     *
     * @param observer
     *            that uses this API and get notified
     */
    public LSPFilterClient(@NonNull LspObserver observer) throws UnknownHostException, IOException {
        fSocket = new Socket(Configuration.HOSTNAME, Configuration.PORT);
        initialize(fSocket.getInputStream(), fSocket.getOutputStream(), observer);
    }

    /**
     * Create client: -Connect to server with socket from hostname and port
     * -Register an observer who can use client API and get notified when server
     * responds
     *
     * @param hostname
     *            address of server to connect to
     * @param port
     *            port of server
     * @param observer
     *            that uses this API and get notified
     */
    public LSPFilterClient(String hostname, Integer port, @NonNull LspObserver observer) throws UnknownHostException, IOException {
        fSocket = new Socket(hostname, port);
        initialize(fSocket.getInputStream(), fSocket.getOutputStream(), observer);
    }

    /**
     * Use this class for testing only Create client: -Use InputStream and
     * OutputStream instead of socket -Register an observer who can use client
     * API and get notified when server responds
     *
     * @param in
     *            input stream of a stream communication
     * @param out
     *            output stream of a stream communication
     * @param observer
     *            that uses this API to get notified
     */
    @VisibleForTesting
    public LSPFilterClient(InputStream in, OutputStream out, @NonNull LspObserver observer) {
        initialize(in, out, observer);
    }

    /**
     * Initialize the LanguageServer from LanguageClient implementation
     *
     * @param in
     * @param out
     * @param observer
     */
    private void initialize(InputStream in, OutputStream out, @NonNull LspObserver observer) {
        fLanguageClient = new LanguageFilterClient();
        Launcher<LanguageServer> launcher = LSPLauncher.createClientLauncher(fLanguageClient, in, out);
        fLanguageClient.setServer(launcher.getRemoteProxy());
        fLanguageClient.register(observer);
        launcher.startListening();
    }

    /**
     * Return language client used by this LSPClient
     *
     * @return Language client
     */
    public LanguageFilterClient getLanguageClient() {
        return fLanguageClient;
    }

    /**
     * PUBLIC API: Observers use this to tell the server that the file has
     * change
     *
     * @param str
     *            string to send
     */
    public void notify(String Uri, String input, int cursorPos) {
        fLanguageClient.tellDidChange(Uri, input, cursorPos);
    }

    /**
     * Close client-side socket connection
     *
     * @throws IOException
     */
    public void dispose() throws IOException {
        try {
            //Tell server to shutdown
            fLanguageClient.shutdown();
        } catch (Exception e) {
            e.printStackTrace();
        }

        // Close socket if exists
        if (fSocket != null) {
            fSocket.close();
        }
    }
}
