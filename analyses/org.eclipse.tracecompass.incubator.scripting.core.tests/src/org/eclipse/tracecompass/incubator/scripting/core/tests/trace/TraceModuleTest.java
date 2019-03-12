/*******************************************************************************
 * Copyright (c) 2019 École Polytechnique de Montréal
 *
 * All rights reserved. This program and the accompanying materials are
 * made available under the terms of the Eclipse Public License v1.0 which
 * accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package org.eclipse.tracecompass.incubator.scripting.core.tests.trace;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.Iterator;
import java.util.concurrent.TimeUnit;

import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.tracecompass.incubator.internal.scripting.core.trace.TraceModule;
import org.eclipse.tracecompass.incubator.scripting.core.tests.ActivatorTest;
import org.eclipse.tracecompass.tmf.core.event.ITmfEvent;
import org.eclipse.tracecompass.tmf.core.event.TmfEvent;
import org.eclipse.tracecompass.tmf.core.exceptions.TmfTraceException;
import org.eclipse.tracecompass.tmf.core.signal.TmfTraceOpenedSignal;
import org.eclipse.tracecompass.tmf.core.trace.TmfTraceManager;
import org.eclipse.tracecompass.tmf.tests.stubs.trace.xml.TmfXmlTraceStub;
import org.eclipse.tracecompass.tmf.tests.stubs.trace.xml.TmfXmlTraceStubNs;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;
import org.junit.rules.Timeout;

/**
 * Test the trace scripting module's specific objects and methods
 *
 * @author Geneviève Bastien
 */
public class TraceModuleTest {

    private static final String CALLSTACK_FILE = "testfiles/traces/callstack.xml";

    /** Time-out tests after 1 minute. */
    @Rule
    public TestRule globalTimeout = new Timeout(5, TimeUnit.MINUTES);

    /**
     * Test the event iterator
     */
    @Test
    public void testEventIterator() {
        TraceModule scriptModule = new TraceModule();

        TmfXmlTraceStub trace = new TmfXmlTraceStubNs();
        try {

            IPath filePath = ActivatorTest.getAbsoluteFilePath(CALLSTACK_FILE);
            IStatus status = trace.validate(null, filePath.toOSString());
            if (!status.isOK()) {
                fail(status.getException().getMessage());
            }
            try {
                trace.initTrace(null, filePath.toOSString(), TmfEvent.class);
            } catch (TmfTraceException e) {
                fail(e.getMessage());
            }
            TmfTraceOpenedSignal signal = new TmfTraceOpenedSignal(this, trace, null);
            trace.traceOpened(signal);
            TmfTraceManager.getInstance().traceOpened(signal);

            Iterator<ITmfEvent> eventIterator = scriptModule.getEventIterator(trace);
            assertNotNull(eventIterator);

            int count = 0;
            while (eventIterator.hasNext()) {
                eventIterator.next();
                count++;
            }
            assertTrue(count > 0);

        } finally {
            trace.dispose();
        }
    }

}
