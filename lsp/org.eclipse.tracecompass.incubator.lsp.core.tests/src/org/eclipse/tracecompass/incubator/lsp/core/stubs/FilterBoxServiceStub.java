/*******************************************************************************
 * Copyright (c) 2019 Ecole Polytechnique de Montreal
 *
 * All rights reserved. This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License v1.0 which
 * accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package org.eclipse.tracecompass.incubator.lsp.core.stubs;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.eclipse.lsp4j.CodeActionParams;
import org.eclipse.lsp4j.CodeLens;
import org.eclipse.lsp4j.CodeLensParams;
import org.eclipse.lsp4j.ColorInformation;
import org.eclipse.lsp4j.Command;
import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.CompletionList;
import org.eclipse.lsp4j.CompletionParams;
import org.eclipse.lsp4j.DidChangeTextDocumentParams;
import org.eclipse.lsp4j.DidCloseTextDocumentParams;
import org.eclipse.lsp4j.DidOpenTextDocumentParams;
import org.eclipse.lsp4j.DidSaveTextDocumentParams;
import org.eclipse.lsp4j.DocumentColorParams;
import org.eclipse.lsp4j.DocumentFormattingParams;
import org.eclipse.lsp4j.DocumentHighlight;
import org.eclipse.lsp4j.DocumentOnTypeFormattingParams;
import org.eclipse.lsp4j.DocumentRangeFormattingParams;
import org.eclipse.lsp4j.DocumentSymbolParams;
import org.eclipse.lsp4j.Hover;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.ReferenceParams;
import org.eclipse.lsp4j.RenameParams;
import org.eclipse.lsp4j.SignatureHelp;
import org.eclipse.lsp4j.SymbolInformation;
import org.eclipse.lsp4j.TextDocumentPositionParams;
import org.eclipse.lsp4j.TextEdit;
import org.eclipse.lsp4j.WorkspaceEdit;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.lsp4j.services.TextDocumentService;

/**
 * FilterBoxService stub: Wrap around the actual textDocumentService
 * implementation. Use this class to store data coming from the LSPCLient bound
 * in the TestEnvironment. Store the data into the FilterBoxServiceMocukup
 *
 * @author Maxime Thibault
 *
 */
public class FilterBoxServiceStub implements TextDocumentService {

    public FilterBoxServiceMockup fMockup = new FilterBoxServiceMockup();
    private final Stub fStub;

    public FilterBoxServiceStub(Stub stub) {
        fStub = stub;
    }

    @Override
    public CompletableFuture<Either<List<CompletionItem>, CompletionList>> completion(CompletionParams position) {
        // Count the transaction
        fStub.count();

        // Call the real implementation
        fMockup.fCursor = position.getPosition().getCharacter();
        fMockup.fCompletionsReceived = fStub.getProxyServer().getTextDocumentService().completion(position);
        return fMockup.fCompletionsReceived;
    }

    @Override
    public CompletableFuture<CompletionItem> resolveCompletionItem(CompletionItem unresolved) {
        // Not implemented
        return null;
    }

    @Override
    public CompletableFuture<Hover> hover(TextDocumentPositionParams position) {
        // Not implemented
        return null;
    }

    @Override
    public CompletableFuture<SignatureHelp> signatureHelp(TextDocumentPositionParams position) {
        // Not implemented
        return null;
    }

    @Override
    public CompletableFuture<List<? extends Location>> definition(TextDocumentPositionParams position) {
        // Not implemented
        return null;
    }

    @Override
    public CompletableFuture<List<? extends Location>> references(ReferenceParams params) {
        // Not implemented
        return null;
    }

    @Override
    public CompletableFuture<List<? extends DocumentHighlight>> documentHighlight(TextDocumentPositionParams position) {
        // Not implemented
        return null;
    }

    @Override
    public CompletableFuture<List<? extends SymbolInformation>> documentSymbol(DocumentSymbolParams params) {
        // Not implemented
        return null;
    }

    @Override
    public CompletableFuture<List<? extends Command>> codeAction(CodeActionParams params) {
        // Not implemented
        return null;
    }

    @Override
    public CompletableFuture<List<? extends CodeLens>> codeLens(CodeLensParams params) {
        // Not implemented
        return null;
    }

    @Override
    public CompletableFuture<CodeLens> resolveCodeLens(CodeLens unresolved) {
        // Not implemented
        return null;
    }

    @Override
    public CompletableFuture<List<? extends TextEdit>> formatting(DocumentFormattingParams params) {
        // Not implemented
        return null;
    }

    @Override
    public CompletableFuture<List<? extends TextEdit>> rangeFormatting(DocumentRangeFormattingParams params) {
        // Not implemented
        return null;
    }

    @Override
    public CompletableFuture<List<? extends TextEdit>> onTypeFormatting(DocumentOnTypeFormattingParams params) {
        // Not implemented
        return null;
    }

    @Override
    public CompletableFuture<WorkspaceEdit> rename(RenameParams params) {
        // Not implemented
        return null;
    }

    @Override
    public void didOpen(DidOpenTextDocumentParams params) {
        // Not implemented
        // Call the real implementation
        fStub.getProxyServer().getTextDocumentService().didOpen(params);
        // Count this transaction
        fStub.count();
    }

    @Override
    public void didChange(DidChangeTextDocumentParams params) {
        // Store data in mockup
        fMockup.fInputReceived = params.getContentChanges().get(0).getText();
        // Call the function on the real implementation
        fStub.getProxyServer().getTextDocumentService().didChange(params);
        // Count this transaction
        fStub.count();
    }

    @Override
    public void didClose(DidCloseTextDocumentParams params) {
        // Not implemented
    }

    @Override
    public void didSave(DidSaveTextDocumentParams params) {
        // Not implemented
    }

    @Override
    public CompletableFuture<List<ColorInformation>> documentColor(DocumentColorParams params) {
        // Count transaction
        fStub.count();

        // Call the real implementation
        fMockup.fColorsReceived = fStub.getProxyServer().getTextDocumentService().documentColor(params);
        return fMockup.fColorsReceived;

    }

}
