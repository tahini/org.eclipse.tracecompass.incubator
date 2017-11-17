/*******************************************************************************
 * Copyright (c) 2016 École Polytechnique de Montréal
 *
 * All rights reserved. This program and the accompanying materials are
 * made available under the terms of the Eclipse Public License v1.0 which
 * accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package org.eclipse.tracecompass.incubator.callstack.core.tests.perf.analysis;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.util.Random;

import org.eclipse.test.performance.Dimension;
import org.eclipse.test.performance.Performance;
import org.eclipse.test.performance.PerformanceMeter;
import org.eclipse.tracecompass.incubator.callstack.core.callgraph.CallGraph;
import org.eclipse.tracecompass.incubator.callstack.core.instrumented.statesystem.InstrumentedCallStackAnalysis;
import org.eclipse.tracecompass.tmf.core.exceptions.TmfTraceException;
import org.eclipse.tracecompass.tmf.core.signal.TmfTraceOpenedSignal;
import org.eclipse.tracecompass.tmf.core.tests.shared.TmfTestHelper;
import org.eclipse.tracecompass.tmf.core.timestamp.TmfTimestamp;
import org.eclipse.tracecompass.tmf.core.trace.TmfTrace;
import org.eclipse.tracecompass.tmf.core.trace.TmfTraceManager;
import org.eclipse.tracecompass.tmf.core.trace.TmfTraceUtils;
import org.junit.Test;

/**
 * Benchmarks the kernel execution graph
 *
 * @author Geneviève Bastien
 */
public abstract class CallStackAndGraphBenchmark {

    /**
     * Test test ID for kernel analysis benchmarks
     */
    public static final String TEST_ID = "org.eclipse.tracecompass.incubator#CallStack#";
    private static final String TEST_CALLSTACK_BUILD = "Building Callstack (%s)";
    private static final String TEST_CALLGRAPH_BUILD = "Building CallGraph (%s)";
    private static final String TEST_CALLGRAPH_MEMORY = "Memory Usage (%s)";
    private static final String TEST_CALLGRAPH_QUERY = "CallGraph Query (%s)";

    private static final long SEED = 473892745896L;

    private static final int LOOP_COUNT = 5;

    private final String fName;
    private final String fAnalysisId;

    /**
     * Constructor
     *
     * @param name
     *            The name of this test
     * @param analysisId
     *            the ID of the analysis to run this benchmark on
     */
    public CallStackAndGraphBenchmark(String name, String analysisId) {
        fName = name;
        fAnalysisId = analysisId;
    }

    /**
     * Run benchmark for the trace
     * @throws TmfTraceException Exceptions thrown getting the trace
     */
    @Test
    public void runCpuBenchmark() throws TmfTraceException {
        Performance perf = Performance.getDefault();
        PerformanceMeter callStackBuildPm = perf.createPerformanceMeter(TEST_ID + String.format(TEST_CALLSTACK_BUILD, fName));
        perf.tagAsSummary(callStackBuildPm, String.format(TEST_CALLSTACK_BUILD, fName), Dimension.CPU_TIME);
        PerformanceMeter callgraphBuildPm = perf.createPerformanceMeter(TEST_ID + String.format(TEST_CALLGRAPH_BUILD, fName));
        perf.tagAsSummary(callgraphBuildPm, String.format(TEST_CALLGRAPH_BUILD, fName), Dimension.CPU_TIME);
        PerformanceMeter callgraphQueryPm = perf.createPerformanceMeter(TEST_ID + String.format(TEST_CALLGRAPH_QUERY, fName));
        perf.tagAsSummary(callgraphQueryPm, String.format(TEST_CALLGRAPH_QUERY, fName), Dimension.CPU_TIME);

        for (int i = 0; i < LOOP_COUNT; i++) {
            System.out.println("Doing iterator " + i);
            InstrumentedCallStackAnalysis module = null;

            TmfTrace trace = null;
            try {
                trace = getTrace();
                trace.traceOpened(new TmfTraceOpenedSignal(this, trace, null));
                module = TmfTraceUtils.getAnalysisModuleOfClass(trace, InstrumentedCallStackAnalysis.class, fAnalysisId);
                assertNotNull(module);
                module.triggerAutomatically(false);

                callStackBuildPm.start();
                TmfTestHelper.executeAnalysis(module);
                callStackBuildPm.stop();

                // Getting the callgraph will schedule the analysis and wait for its completion
                callgraphBuildPm.start();
                CallGraph callGraph = module.getCallGraph();
                callgraphBuildPm.stop();

                assertTrue(callGraph.getElements().size() > 0);

                // We just read the trace for the first time, so it should be safe to use the
                // end time
                long startTime = trace.getStartTime().toNanos();
                long endTime = trace.getEndTime().toNanos();
                long delta = endTime - startTime;

                // Get partial callgraphs
                Random randomGenerator = new Random(SEED);
                callgraphQueryPm.start();
                for (int j = 0; j < 50; j++) {
                    long time0 = Math.abs(randomGenerator.nextLong()) % delta;
                    long time1 = Math.abs(randomGenerator.nextLong()) % delta;
                    System.out.println("Getting callgraph between " + Math.min(time0,  time1) + " and " + Math.max(time0,  time1) + " with delta " + Math.abs(time0-time1));
                    callGraph = module.getCallGraph(TmfTimestamp.fromNanos(startTime + Math.min(time0, time1)), TmfTimestamp.fromNanos(startTime + Math.max(time0, time1)));
                }
                callgraphQueryPm.stop();

                /*
                 * Delete the supplementary files, so that the next iteration rebuilds the state
                 * system.
                 */
                File suppDir = new File(TmfTraceManager.getSupplementaryFileDir(trace));
                for (File file : suppDir.listFiles()) {
                    file.delete();
                }

            } finally {
                if (module != null) {
                    module.dispose();
                }
                if (trace != null) {
                    trace.dispose();
                }
            }
        }
        callStackBuildPm.commit();
        callgraphBuildPm.commit();
        callgraphQueryPm.commit();
    }

    /**
     * Get the trace for this analysis. Every call to getTrace() should return a
     * fresh trace fully initialized. The caller is responsible to dispose the trace
     * when not required anymore
     *
     * @return A freshly initialized trace
     * @throws TmfTraceException Exceptions thrown getting the trace
     */
    protected abstract TmfTrace getTrace() throws TmfTraceException;

    /**
     * Run memory benchmark for the trace
     * @throws TmfTraceException Exceptions thrown getting the trace
     */
    @Test
    public void runMemoryBenchmark() throws TmfTraceException {
        Performance perf = Performance.getDefault();
        PerformanceMeter callStackMemoryPm = perf.createPerformanceMeter(TEST_ID + String.format(TEST_CALLGRAPH_MEMORY, fName));
        perf.tagAsSummary(callStackMemoryPm, String.format(TEST_CALLGRAPH_MEMORY, fName), Dimension.USED_JAVA_HEAP);

        for (int i = 0; i < LOOP_COUNT; i++) {
            InstrumentedCallStackAnalysis module = null;

            TmfTrace trace = null;
            try {
                trace = getTrace();
                trace.traceOpened(new TmfTraceOpenedSignal(this, trace, null));
                module = TmfTraceUtils.getAnalysisModuleOfClass(trace, InstrumentedCallStackAnalysis.class, fAnalysisId);
                assertNotNull(module);
                module.triggerAutomatically(false);

                TmfTestHelper.executeAnalysis(module);

                // Getting the callgraph will schedule the analysis and wait for its completion
                System.gc();
                callStackMemoryPm.start();
                module.getCallGraph();
                System.gc();
                callStackMemoryPm.stop();

                /*
                 * Delete the supplementary files, so that the next iteration rebuilds the state
                 * system.
                 */
                File suppDir = new File(TmfTraceManager.getSupplementaryFileDir(trace));
                for (File file : suppDir.listFiles()) {
                    file.delete();
                }

            } finally {
                if (module != null) {
                    module.dispose();
                }
                if (trace != null) {
                    trace.dispose();
                }
            }
        }
        callStackMemoryPm.commit();
    }

}
