/*******************************************************************************
 * Copyright (c) 2019 École Polytechnique de Montréal
 *
 * All rights reserved. This program and the accompanying materials are
 * made available under the terms of the Eclipse Public License v1.0 which
 * accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package org.eclipse.tracecompass.incubator.scripting.core.tests.perf;

import static org.junit.Assert.fail;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;

import org.apache.commons.io.FileUtils;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.FileLocator;
import org.eclipse.core.runtime.IPath;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.swt.widgets.Display;
import org.eclipse.test.performance.Dimension;
import org.eclipse.test.performance.Performance;
import org.eclipse.test.performance.PerformanceMeter;
import org.eclipse.tracecompass.analysis.os.linux.core.tid.TidAnalysisModule;
import org.eclipse.tracecompass.ctf.core.tests.shared.CtfBenchmarkTrace;
import org.eclipse.tracecompass.incubator.internal.scripting.core.ScriptExecutionHelper;
import org.eclipse.tracecompass.incubator.scripting.core.trace.TraceScriptingModule;
import org.eclipse.tracecompass.incubator.scripting.ui.tests.ActivatorTest;
import org.eclipse.tracecompass.incubator.scripting.ui.tests.TestModule;
import org.eclipse.tracecompass.internal.tmf.ui.project.model.TmfImportHelper;
import org.eclipse.tracecompass.lttng2.kernel.core.trace.LttngKernelTrace;
import org.eclipse.tracecompass.testtraces.ctf.CtfTestTrace;
import org.eclipse.tracecompass.tmf.core.TmfCommonConstants;
import org.eclipse.tracecompass.tmf.core.event.ITmfEvent;
import org.eclipse.tracecompass.tmf.core.exceptions.TmfAnalysisException;
import org.eclipse.tracecompass.tmf.core.exceptions.TmfTraceException;
import org.eclipse.tracecompass.tmf.core.tests.shared.TmfTestHelper;
import org.eclipse.tracecompass.tmf.core.trace.ITmfTrace;
import org.eclipse.tracecompass.tmf.core.trace.TmfTraceManager;
import org.eclipse.tracecompass.tmf.ctf.core.event.CtfTmfEvent;
import org.eclipse.tracecompass.tmf.ui.project.model.TmfProjectElement;
import org.eclipse.tracecompass.tmf.ui.project.model.TmfProjectRegistry;
import org.eclipse.tracecompass.tmf.ui.project.model.TmfTraceElement;
import org.eclipse.tracecompass.tmf.ui.project.model.TmfTraceFolder;
import org.eclipse.tracecompass.tmf.ui.tests.shared.ProjectModelTestData;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import com.google.common.collect.ImmutableList;

/**
 * Benchmarks EASE Scripting in native java, javascript (Nashorn and Rhino) and
 * python (Jython and Py4j)
 *
 * @author Geneviève Bastien
 */
@RunWith(Parameterized.class)
public class ScriptingBenchmark {

    /**
     * Initial value for which to compute the Collatz sequence
     */
    public static final int INITIAL_VALUE = 10;
    /**
     * Last value for which to compute the Collatz sequence
     */
    public static final int LIMIT = 300000;

    private static final String JAVA_PREFIX = "Java: ";
    private static final String RHINO_PREFIX = "JS Rhino: ";
    private static final String NASHORN_PREFIX = "JS Nashorn: ";
    private static final String PY4J_PREFIX = "Py4j: ";
    private static final String JYTHON_PREFIX = "Jython: ";
    private static final int LOOP_COUNT = 2;

    private static final String DEFAULT_PROJECT = "Tracing";

    private static final String JAVASCRIPT_PATH = "scripts/perf/javascript/";
    private static final String PYTHON_PATH = "scripts/perf/python/";

    private static final String RHINO_ENGINE = "org.eclipse.ease.javascript.rhino";
    private static final String NASHORN_ENGINE = "org.eclipse.ease.javascript.nashorn";
    private static final String JYTHON_ENGINE = "org.eclipse.ease.python.jython";
    private static final String PY4J_ENGINE = "org.eclipse.ease.lang.python.py4j.engine";
    private static TmfProjectElement sfProject;

    /**
     * @return The arrays of parameters
     * @throws IOException
     *             Exception thrown initializing the traces
     */
    @Parameters(name = "{index}: {0}")
    public static Iterable<Object[]> getParameters() throws IOException {
        return Arrays.asList(new Object[][] {
//                { "Empty Script", EMPTY, "empty.js", "empty.py", null },
//                { "Simple Computation", SIMPLE_COMPUTATION, "simpleComputation.js", "simpleComputation.py", null },
//                { "Computation Through Java", JAVA_COMPUTATION, "computationJava.js", "computationJava.py", null },
//                { "Computation Through Callback", CALLBACK_COMPUTATION, "computationCallback.js", "computationCallback.py", null },
//                { "Compute Values in Java", COMPUTE_EACH_VALUE, "computationEachValue.js", "computationEachValue.py", null },
//                { "Read trace events for os-events", READ_TRACE, "readTrace.js", "readTrace.py", ImmutableList.of(String.valueOf(CtfBenchmarkTrace.ALL_OS_ANALYSES.getTracePath().getFileName())) },
//          { "Read trace events for django", READ_TRACE, "readTrace.js", "readTrace.py", ImmutableList.of(String.valueOf(FileUtils.toFile(FileLocator.toFileURL(CtfTestTrace.DJANGO_HTTPD.getTraceURL())).getName())) },
//                { "TID analysis for Os-events", TID_ANALYSIS, "tidAnalysis.js", "tidAnalysis.py", ImmutableList.of(String.valueOf(CtfBenchmarkTrace.ALL_OS_ANALYSES.getTracePath().getFileName())) },
                { "TID analysis for django", TID_ANALYSIS, "tidAnalysis.js", "tidAnalysis.py", ImmutableList.of(String.valueOf(FileUtils.toFile(FileLocator.toFileURL(CtfTestTrace.DJANGO_HTTPD.getTraceURL())).getName())) },

        });
    }

    private static final Runnable EMPTY = () -> {
        // Do nothing much, to benchmark script initialization
        int i = 0;
        System.out.println(i);
    };

    private static final Runnable SIMPLE_COMPUTATION = () -> {
        // Compute the Collatz Conjecture sequence for integers between INITIAL_VALUE and LIMIT
        int base = INITIAL_VALUE;
        long value = base;
        while (base < LIMIT) {
            if (value == 1) {
                value = base++;
            }
            if (value % 2 == 0) {
                value = value / 2;
            } else {
                value = 3 * value + 1;
            }
        }
    };

    private static final Runnable JAVA_COMPUTATION = () -> {
        TestModule testModule = new TestModule();
        testModule.doJavaLoop();
    };

    private static final Runnable CALLBACK_COMPUTATION = () -> {
        TestModule testModule = new TestModule();
        testModule.doLoopWithCallback(value -> {
            if (value % 2 == 0) {
                return value / 2;
            }
            return 3 * value + 1;
        });
    };

    private static final Runnable COMPUTE_EACH_VALUE = () -> {
        // Compute the Collatz Conjecture sequence for integers between INITIAL_VALUE and LIMIT
        TestModule testModule = new TestModule();
        testModule.doJavaLoop();
        int base = INITIAL_VALUE;
        long value = base;
        while (base < LIMIT) {
            if (value == 1) {
                value = base++;
            }
            value = testModule.compute(value);
        }
    };

    private static final Runnable READ_TRACE = () -> {
        Path tracePath = CtfBenchmarkTrace.ALL_OS_ANALYSES.getTracePath();
        LttngKernelTrace trace = new LttngKernelTrace();
        try {
            trace.initTrace(null, tracePath.toString(), CtfTmfEvent.class);
            TraceScriptingModule module = new TraceScriptingModule();
            Iterator<@NonNull ITmfEvent> eventIterator = module.getEventIterator(trace);
            int schedSwitchCnt = 0;
            while (eventIterator.hasNext()) {
                ITmfEvent event = eventIterator.next();
                if (event.getName().equals("sched_switch")) {
                    schedSwitchCnt++;
                }
            }
            System.out.println("Count sched switch: " + schedSwitchCnt);
        } catch (TmfTraceException e) {
            fail(e.getMessage());
        } finally {
            trace.dispose();
        }
    };

    private static void deleteSupplementaryFiles(@NonNull ITmfTrace trace) {
        /*
         * Delete the supplementary files at the end of the benchmarks
         */
        File suppDir = new File(TmfTraceManager.getSupplementaryFileDir(trace));
        for (File file : suppDir.listFiles()) {
            file.delete();
        }
    }

    private static final Runnable TID_ANALYSIS = () -> {
        Path tracePath = CtfBenchmarkTrace.ALL_OS_ANALYSES.getTracePath();
        LttngKernelTrace trace = new LttngKernelTrace();
        TidAnalysisModule analysisModule = null;
        try {
            trace.initTrace(null, tracePath.toString(), CtfTmfEvent.class);

            analysisModule = new TidAnalysisModule();
            analysisModule.setTrace(trace);

            TmfTestHelper.executeAnalysis(analysisModule);

        } catch (TmfTraceException | TmfAnalysisException e) {
            fail(e.getMessage());
        } finally {
            deleteSupplementaryFiles(trace);
            if (analysisModule != null) {
                analysisModule.dispose();
            }
            trace.dispose();
        }
    };

    private final String fName;
    private final Runnable fJavaMethod;
    private final String fJs;
    private final String fPy;
    private @Nullable List<@NonNull String> fArguments;

    /**
     * Constructor
     *
     * @param name
     *            The name of the test
     * @param javaMethod
     *            The java runnable method to benchmark the java part
     * @param jsScript
     *            The name of the file containing the javascript code
     * @param pyScript
     *            The name of the file containing the python code
     * @param arguments
     *            The list of arguments to pass to the script
     */
    public ScriptingBenchmark(String name, Runnable javaMethod, String jsScript, String pyScript, @Nullable List<@NonNull String> arguments) {
        fName = name;
        fJavaMethod = javaMethod;
        fJs = jsScript;
        fPy = pyScript;
        fArguments = arguments;
    }

    /**
     * Prepare the workspace by preloading the required traces
     *
     * @throws CoreException
     *             Exception preparing the traces
     * @throws IOException
     *             Exception preparing the traces
     */
    @BeforeClass
    public static void prepareWorkspace() throws CoreException, IOException {
        IProject project = TmfProjectRegistry.createProject(DEFAULT_PROJECT, null, null);
        final TmfProjectElement projectElement = TmfProjectRegistry.getProject(project, true);
        TmfTraceFolder tracesFolder = projectElement.getTracesFolder();
        if (tracesFolder != null) {
            IFolder traceFolder = tracesFolder.getResource();

            /* Add the all os events trace from benchmark */
            Path tracePath = CtfBenchmarkTrace.ALL_OS_ANALYSES.getTracePath();
            IPath pathString = new org.eclipse.core.runtime.Path(tracePath.toString());
            IResource linkedTrace = TmfImportHelper.createLink(traceFolder, pathString, pathString.lastSegment());
            if (!(linkedTrace != null && linkedTrace.exists())) {
                throw new NullPointerException("Trace cannot be created");
            }
            linkedTrace.setPersistentProperty(TmfCommonConstants.TRACETYPE,
                    "org.eclipse.linuxtools.lttng2.kernel.tracetype");

            /* Add the django test trace */
            String absolutePath = FileUtils.toFile(FileLocator.toFileURL(CtfTestTrace.DJANGO_HTTPD.getTraceURL())).getAbsolutePath();
            pathString = new org.eclipse.core.runtime.Path(absolutePath);
            linkedTrace = TmfImportHelper.createLink(traceFolder, pathString, pathString.lastSegment());
            if (!(linkedTrace != null && linkedTrace.exists())) {
                throw new NullPointerException("Trace cannot be created");
            }
            linkedTrace.setPersistentProperty(TmfCommonConstants.TRACETYPE,
                    "org.eclipse.linuxtools.lttng2.kernel.tracetype");

            // Refresh the project model
            tracesFolder.refresh();

            for (TmfTraceElement traceElement : tracesFolder.getTraces()) {
                traceElement.refreshTraceType();
            }
        }
        projectElement.refresh();
        sfProject = projectElement;
    }

    /**
     * Delete project and traces at the end
     */
    @AfterClass
    public static void deleteProject() {
        TmfProjectElement project = sfProject;
        if (project != null) {
            Display.getDefault().syncExec(() -> ProjectModelTestData.deleteProject(project));
        }
    }

    /**
     * Benchmark the java runnable
     */
    @Test
    public void javaTest() {

        Performance perf = Performance.getDefault();
        PerformanceMeter pmJava = perf.createPerformanceMeter(JAVA_PREFIX + fName);
        perf.tagAsSummary(pmJava, JAVA_PREFIX + fName, Dimension.CPU_TIME);

        for (int i = 0; i < LOOP_COUNT; i++) {
            pmJava.start();
            fJavaMethod.run();
            pmJava.stop();
        }
        pmJava.commit();

    }

    /**
     * Benchmark the javascript rhino engine
     */
    @Test
    public void javaScriptRhinoTest() {

        IPath absoluteFilePath = ActivatorTest.getAbsoluteFilePath(JAVASCRIPT_PATH + fJs);

        Performance perf = Performance.getDefault();
        PerformanceMeter pmJavaScript = perf.createPerformanceMeter(RHINO_PREFIX + fName);
        perf.tagAsSummary(pmJavaScript, RHINO_PREFIX + fName, Dimension.CPU_TIME);

        for (int i = 0; i < LOOP_COUNT; i++) {
            pmJavaScript.start();
            ScriptExecutionHelper.executeScript(Objects.requireNonNull(absoluteFilePath.toOSString()), RHINO_ENGINE, fArguments);
            pmJavaScript.stop();
        }
        pmJavaScript.commit();
    }

    /**
     * Benchmark the javascript nashorn engine
     */
    @Test
    public void javaScriptNashornTest() {
        IPath absoluteFilePath = ActivatorTest.getAbsoluteFilePath(JAVASCRIPT_PATH + fJs);

        Performance perf = Performance.getDefault();
        PerformanceMeter pmJavaScript = perf.createPerformanceMeter(NASHORN_PREFIX + fName);
        perf.tagAsSummary(pmJavaScript, NASHORN_PREFIX + fName, Dimension.CPU_TIME);

        for (int i = 0; i < LOOP_COUNT; i++) {
            pmJavaScript.start();
            ScriptExecutionHelper.executeScript(Objects.requireNonNull(absoluteFilePath.toOSString()), NASHORN_ENGINE, fArguments);
            pmJavaScript.stop();
        }
        pmJavaScript.commit();
    }

    /**
     * Benchmark the python py4j engine
     */
    @Test
    public void py4jTest() {
        // See if a specific file for py4j exists, otherwise, use the python
        // script
        IPath absoluteFilePath;
        try {
            absoluteFilePath = ActivatorTest.getAbsoluteFilePath(PYTHON_PATH + "py4j_" + fPy);
        } catch (NullPointerException e) {
            absoluteFilePath = ActivatorTest.getAbsoluteFilePath(PYTHON_PATH + fPy);
        }

        Performance perf = Performance.getDefault();
        PerformanceMeter pmPython = perf.createPerformanceMeter(PY4J_PREFIX + fName);
        perf.tagAsSummary(pmPython, PY4J_PREFIX + fName, Dimension.CPU_TIME);

        for (int i = 0; i < LOOP_COUNT; i++) {
            pmPython.start();
            ScriptExecutionHelper.executeScript(Objects.requireNonNull(absoluteFilePath.toOSString()), PY4J_ENGINE, fArguments);
            pmPython.stop();
        }
        pmPython.commit();
    }

    /**
     * Benchmark the python jython engine
     */
    @Test
    public void jythonTest() {
        // See if a specific file for py4j exists, otherwise, use the python
        // script
        IPath absoluteFilePath;
        try {
            absoluteFilePath = ActivatorTest.getAbsoluteFilePath(PYTHON_PATH + "jython_" + fPy);
        } catch (NullPointerException e) {
            absoluteFilePath = ActivatorTest.getAbsoluteFilePath(PYTHON_PATH + fPy);
        }

        Performance perf = Performance.getDefault();
        PerformanceMeter pmPython = perf.createPerformanceMeter(JYTHON_PREFIX + fName);
        perf.tagAsSummary(pmPython, JYTHON_PREFIX + fName, Dimension.CPU_TIME);

        for (int i = 0; i < LOOP_COUNT; i++) {
            pmPython.start();
            ScriptExecutionHelper.executeScript(Objects.requireNonNull(absoluteFilePath.toOSString()), JYTHON_ENGINE, fArguments);
            pmPython.stop();
        }
        pmPython.commit();
    }

}
