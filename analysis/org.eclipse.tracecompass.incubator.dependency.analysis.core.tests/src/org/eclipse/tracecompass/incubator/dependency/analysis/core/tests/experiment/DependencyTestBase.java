/*******************************************************************************
 * Copyright (c) 2016 École Polytechnique de Montréal
 *
 * All rights reserved. This program and the accompanying materials are
 * made available under the terms of the Eclipse Public License v1.0 which
 * accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package org.eclipse.tracecompass.incubator.dependency.analysis.core.tests.experiment;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.tracecompass.analysis.os.linux.core.event.aspect.LinuxTidAspect;
import org.eclipse.tracecompass.incubator.dependency.analysis.core.matching.SpanDependencyAnalysis;
import org.eclipse.tracecompass.incubator.dependency.analysis.core.tests.Activator;
import org.eclipse.tracecompass.incubator.dependency.analysis.core.tests.stubs.OpenTracingAPIMatchingStub;
import org.eclipse.tracecompass.tmf.core.event.ITmfEvent;
import org.eclipse.tracecompass.tmf.core.event.TmfEvent;
import org.eclipse.tracecompass.tmf.core.event.matching.TmfEventMatching;
import org.eclipse.tracecompass.tmf.core.exceptions.TmfTraceException;
import org.eclipse.tracecompass.tmf.core.signal.TmfTraceOpenedSignal;
import org.eclipse.tracecompass.tmf.core.trace.ITmfTrace;
import org.eclipse.tracecompass.tmf.core.trace.TmfTraceUtils;
import org.eclipse.tracecompass.tmf.core.trace.experiment.TmfExperiment;
import org.eclipse.tracecompass.tmf.tests.stubs.trace.xml.TmfXmlTraceStub;
import org.eclipse.tracecompass.tmf.tests.stubs.trace.xml.TmfXmlTraceStubNs;
import org.junit.After;
import org.junit.Before;

import com.google.common.collect.ImmutableList;

/**
 * Base class for call stack tests. It sets up the trace and analysis module.
 *
 * @author Geneviève Bastien
 */
public class DependencyTestBase {

    private static final String FIRST_TRACE_FILE = "testfiles/traces/first_trace.xml";
    private static final String SECOND_TRACE_FILE = "testfiles/traces/second_trace.xml";

    private @Nullable ITmfTrace fTrace;
    private @Nullable SpanDependencyAnalysis fModule;
    private static final LinuxTidAspect TID_ASPECT = new LinuxTidAspect() {

        @Override
        public @Nullable Integer resolve(@NonNull ITmfEvent event) {
            String tidStr = event.getContent().getFieldValue(String.class, "tid");
            return (tidStr == null) ? null : Integer.valueOf(tidStr);
        }

    };

    private static ITmfTrace getTrace(String traceFile) {
        TmfXmlTraceStub trace = new TmfXmlTraceStubNs();
        trace.addEventAspect(TID_ASPECT);
        IPath filePath = Activator.getAbsoluteFilePath(traceFile);
        IStatus status = trace.validate(null, filePath.toOSString());
        if (!status.isOK()) {
            fail(status.getException().getMessage());
        }
        try {
            trace.initTrace(null, filePath.toOSString(), TmfEvent.class);
        } catch (TmfTraceException e) {
            fail(e.getMessage());
        }
        return trace;
    }

    /**
     * Setup the trace for the tests
     */
    @Before
    public void setUp() {
        TmfEventMatching.registerMatchObject(new OpenTracingAPIMatchingStub());
        ITmfTrace trace1 = getTrace(FIRST_TRACE_FILE);
        ITmfTrace trace2 = getTrace(SECOND_TRACE_FILE);
        TmfExperiment exp = new TmfExperiment(ITmfEvent.class, "test-exp", ImmutableList.of(trace1, trace2).toArray(new ITmfTrace[2]),
                TmfExperiment.DEFAULT_INDEX_PAGE_SIZE, null);
        fTrace = exp;
        exp.traceOpened(new TmfTraceOpenedSignal(this, exp, null));

        SpanDependencyAnalysis module = TmfTraceUtils.getAnalysisModuleOfClass(exp, SpanDependencyAnalysis.class, SpanDependencyAnalysis.ID);
        assertNotNull(module);

        module.schedule();
        assertTrue(module.waitForCompletion());
        fModule = module;
    }

    /**
     * Dispose of the test data
     */
    @After
    public void tearDown() {
        ITmfTrace trace = fTrace;
        if (trace != null) {
            trace.dispose();
        }
        SpanDependencyAnalysis module = fModule;
        if (module != null) {
            module.dispose();
        }
    }

    /**
     * Get the analysis module. Its execution is complete.
     *
     * The structure of the callstack provided by this module is the following:
     *
     * <pre>
     * where 1e2 means at timestamp 1, entry of function named op2
     *   and 10x means at timestamp 10, exit of the function
     *
     * pid1 --- tid2   1e1 ------------- 10x  12e4------------20x
     *      |             3e2-------7x
     *      |               4e3--5x
     *      |-- tid3      3e2 --------------------------------20x
     *                       5e3--6x  7e2--------13x
     *
     * pid5 --- tid6   1e1 -----------------------------------20x
     *      |            2e3 ---------7x      12e4------------20x
     *      |                4e1--6x
     *      |-- tid7   1e5 -----------------------------------20x
     *                   2e2 +++ 6x  9e2 ++++ 13x 15e2 ++ 19x
     *                                10e3 + 11x
     * </pre>
     *
     * @return The analysis module
     */
    public SpanDependencyAnalysis getModule() {
        SpanDependencyAnalysis module = fModule;
        if (module == null) {
            throw new NullPointerException("Module should not be null");
        }
        return module;
    }

    /**
     * Get the trace
     *
     * @return The trace used for this test
     */
    public ITmfTrace getTrace() {
        ITmfTrace trace = fTrace;
        if (trace == null) {
            throw new NullPointerException();
        }
        return trace;
    }

}
