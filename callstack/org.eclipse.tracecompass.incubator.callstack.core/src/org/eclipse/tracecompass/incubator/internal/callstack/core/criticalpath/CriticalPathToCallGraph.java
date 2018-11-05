/*******************************************************************************
 * Copyright (c) 2018 École Polytechnique de Montréal
 *
 * All rights reserved. This program and the accompanying materials are
 * made available under the terms of the Eclipse Public License v1.0 which
 * accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package org.eclipse.tracecompass.incubator.internal.callstack.core.criticalpath;

import java.util.Collection;
import java.util.Collections;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.tracecompass.analysis.graph.core.base.IGraphWorker;
import org.eclipse.tracecompass.analysis.graph.core.base.ITmfGraphVisitor;
import org.eclipse.tracecompass.analysis.graph.core.base.TmfEdge;
import org.eclipse.tracecompass.analysis.graph.core.base.TmfEdge.EdgeType;
import org.eclipse.tracecompass.analysis.graph.core.base.TmfGraph;
import org.eclipse.tracecompass.analysis.graph.core.base.TmfVertex;
import org.eclipse.tracecompass.incubator.analysis.core.concepts.AggregatedCallSite;
import org.eclipse.tracecompass.incubator.callstack.core.base.CallStackElement;
import org.eclipse.tracecompass.incubator.callstack.core.base.CallStackGroupDescriptor;
import org.eclipse.tracecompass.incubator.callstack.core.base.ICallStackElement;
import org.eclipse.tracecompass.incubator.callstack.core.base.ICallStackGroupDescriptor;
import org.eclipse.tracecompass.incubator.callstack.core.callgraph.CallGraph;
import org.eclipse.tracecompass.incubator.callstack.core.callgraph.ICallGraphProvider;
import org.eclipse.tracecompass.tmf.core.timestamp.ITmfTimestamp;

/**
 *
 *
 * @author Geneviève Bastien
 */
public class CriticalPathToCallGraph implements ICallGraphProvider {

    private static final CallGraph EMPTY_CALLGRAPH = new CallGraph();

    private static final ICallStackGroupDescriptor DESCRIPTOR = new CallStackGroupDescriptor("Worker", null, false);
    private static final Collection<ICallStackGroupDescriptor> DESCRIPTORS = Collections.singleton(DESCRIPTOR);

    private final CallGraph fCallGraph = new CallGraph();

    private class graphToCallGraphConverter implements ITmfGraphVisitor {

        private final TmfGraph fGraph;
        private final IGraphWorker fMainWorker;
        private final ICallStackElement fElement;

        public graphToCallGraphConverter(ICallStackElement descriptor, IGraphWorker mainWorker, TmfGraph graph) {
            fGraph = graph;
            fMainWorker = mainWorker;
            fElement = descriptor;
        }

        @Override
        public void visitHead(TmfVertex vertex) {
            // Nothing to do

        }

        @Override
        public void visit(TmfVertex vertex) {
            // Nothing to do
        }

        @Override
        public void visit(TmfEdge edge, boolean horizontal) {
            // Get the worker to which to attribute this edge, whether vertical or horizontal
            IGraphWorker worker = fGraph.getParentOf(edge.getVertexTo());
            if (worker == null) {
                return;
            }

            // If it is the main worker, just add a 1st level call of the edge type
            if (worker == fMainWorker) {
                AggregatedCallSite callSite = createCallSite(edge.getType());
                callSite.addToLength(edge.getDuration());
                fCallGraph.addAggregatedCallSite(fElement, callSite);
                return;
            }

            // Otherwise, add a first level call that corresponds to the worker
            AggregatedCallSite callSite = createCallSite(worker.toString());
            callSite.addToLength(edge.getDuration());

            // Then, add a second level for the edge type if it is not running
            if (!edge.getType().equals(EdgeType.RUNNING)) {
                AggregatedCallSite childType = createCallSite(edge.getType());
                childType.addToLength(edge.getDuration());
                callSite.addCallee(childType);
            }
            fCallGraph.addAggregatedCallSite(fElement, callSite);
        }

    }

    public CriticalPathToCallGraph(@Nullable TmfGraph graph) {
        if (graph == null) {
            return;
        }
        TmfVertex head = graph.getHead();
        if (head == null) {
            return;
        }

        IGraphWorker worker = graph.getParentOf(head);
        ICallStackElement element = new CallStackElement(String.valueOf(worker), DESCRIPTOR);
        graphToCallGraphConverter converter = new graphToCallGraphConverter(element, worker, graph);
        graph.scanLineTraverse(worker, converter);
    }

    @Override
    public Collection<ICallStackGroupDescriptor> getGroupDescriptors() {
        return DESCRIPTORS;
    }

    @Override
    public CallGraph getCallGraph(ITmfTimestamp start, ITmfTimestamp end) {
        return EMPTY_CALLGRAPH;
    }

    @Override
    public CallGraph getCallGraph() {
        return fCallGraph;
    }

    @Override
    public @NonNull AggregatedCallSite createCallSite(Object symbol) {
        return new AggregatedCallSite(symbol, 0);
    }

}
