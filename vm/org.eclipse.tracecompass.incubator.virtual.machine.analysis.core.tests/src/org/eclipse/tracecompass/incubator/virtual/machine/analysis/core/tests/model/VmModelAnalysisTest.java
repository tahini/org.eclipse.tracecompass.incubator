/*******************************************************************************
 * Copyright (c) 2018 École Polytechnique de Montréal
 *
 * All rights reserved. This program and the accompanying materials are
 * made available under the terms of the Eclipse Public License v1.0 which
 * accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package org.eclipse.tracecompass.incubator.virtual.machine.analysis.core.tests.model;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.tracecompass.analysis.os.linux.core.tests.stubs.LinuxTestCase.IntervalInfo;
import org.eclipse.tracecompass.analysis.os.linux.core.tests.stubs.LinuxTestCase.PunctualInfo;
import org.eclipse.tracecompass.incubator.internal.virtual.machine.analysis.core.model.analysis.VirtualMachineModelAnalysis;
import org.eclipse.tracecompass.incubator.virtual.machine.analysis.core.tests.shared.vm.VmTestCase;
import org.eclipse.tracecompass.incubator.virtual.machine.analysis.core.tests.shared.vm.VmTestExperiment;
import org.eclipse.tracecompass.statesystem.core.ITmfStateSystem;
import org.eclipse.tracecompass.statesystem.core.tests.shared.utils.StateSystemTestUtils;
import org.eclipse.tracecompass.tmf.core.exceptions.TmfAnalysisException;
import org.eclipse.tracecompass.tmf.core.signal.TmfTraceOpenedSignal;
import org.eclipse.tracecompass.tmf.core.trace.ITmfTrace;
import org.eclipse.tracecompass.tmf.core.trace.TmfTrace;
import org.eclipse.tracecompass.tmf.core.trace.experiment.TmfExperiment;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

/**
 * Test the {@link VirtualMachineModelAnalysis} analysis
 *
 * @author Geneviève Bastien
 */
@RunWith(Parameterized.class)
public class VmModelAnalysisTest {

    private final VmTestCase fTestCase;

    /**
     * Constructor
     *
     * @param testName
     *            A name for the test, to display in the header
     * @param test
     *            A test case parameter for this test
     */
    public VmModelAnalysisTest(String testName, VmTestCase test) {
        super();
        fTestCase = test;
    }

    /**
     * Clean up
     */
    @After
    public void tearDown() {
        fTestCase.dispose();
    }

    /**
     * @return The arrays of parameters
     */
    @Parameters(name = "{index}: {0}")
    public static Iterable<Object[]> getParameters() {
        return Arrays.asList(new Object[][] {
                { VmTestExperiment.ONE_QEMUKVM.name(), new OneQemuKvmModelTestCase() },
                // { VmTestExperiment.ONE_CONTAINER.name(), new SimpleContainersTestCase() },
                // { VmTestExperiment.QEMU_CONTAINER.name(), new QemuContainerTestCase() },
                // TODO: Support experiment with multiple physical machines
                // { VmTestExperiment.TWO_HOSTS.name(), new TwoHostsTestCase() },
        });
    }

    private VirtualMachineModelAnalysis setUp() throws TmfAnalysisException {
        TmfExperiment experiment = fTestCase.getExperiment();

        /* Open the traces */
        for (ITmfTrace trace : experiment.getTraces()) {
            ((TmfTrace) trace).traceOpened(new TmfTraceOpenedSignal(this, trace, null));
        }

        experiment.traceOpened(new TmfTraceOpenedSignal(this, experiment, null));

        VirtualMachineModelAnalysis module = VirtualMachineModelAnalysis.getModel(experiment);
        module.setTrace(experiment);
        module.schedule();
        assertTrue(module.waitForCompletion());

        return module;
    }

    /**
     * Test that the analysis executes without problems
     *
     * @throws TmfAnalysisException
     *             Exception thrown by set up
     */
    @Test
    public void testAnalysisExecution() throws TmfAnalysisException {
        VirtualMachineModelAnalysis module = setUp();
        assertNotNull(module.getStateSystem());
    }

    /**
     * Test the intervals built by the state provider
     *
     * @throws TmfAnalysisException
     *             Exception thrown by set up
     */
    @Test
    public void testStateProviderIntervalData() throws TmfAnalysisException {
        VirtualMachineModelAnalysis module = setUp();
        assertNotNull(module);

        ITmfStateSystem ss = module.getStateSystem();
        assertNotNull(ss);

        for (@NonNull
        IntervalInfo info : fTestCase.getTestIntervals()) {
            StateSystemTestUtils.testIntervalForAttributes(ss, info.getIntervals(), info.getAttributePath());
        }
    }

    /**
     * Test the data of attributes at punctual times
     *
     * @throws TmfAnalysisException
     *             Exception thrown by set up
     */
    @Test
    public void testStateProviderPunctualData() throws TmfAnalysisException {
        VirtualMachineModelAnalysis module = setUp();
        assertNotNull(module);

        ITmfStateSystem ss = module.getStateSystem();
        assertNotNull(ss);

        for (@NonNull
        PunctualInfo info : fTestCase.getPunctualTestData()) {
            StateSystemTestUtils.testValuesAtTime(ss, info.getTimestamp(), info.getValues());
        }
    }

}
