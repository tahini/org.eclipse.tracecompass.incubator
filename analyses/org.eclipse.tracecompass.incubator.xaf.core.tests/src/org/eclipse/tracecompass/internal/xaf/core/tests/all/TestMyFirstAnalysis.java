package org.eclipse.tracecompass.internal.xaf.core.tests.all;

import static org.junit.Assert.assertTrue;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.tracecompass.common.core.NonNullUtils;
import org.eclipse.tracecompass.lttng2.kernel.core.trace.LttngKernelTrace;
import org.eclipse.tracecompass.lttng2.ust.core.trace.LttngUstTrace;
import org.eclipse.tracecompass.tmf.core.analysis.IAnalysisModule;
import org.eclipse.tracecompass.tmf.core.analysis.TmfAbstractAnalysisModule;
import org.eclipse.tracecompass.tmf.core.exceptions.TmfTraceException;
import org.eclipse.tracecompass.tmf.core.signal.TmfSignalManager;
import org.eclipse.tracecompass.tmf.core.signal.TmfTraceOpenedSignal;
import org.eclipse.tracecompass.tmf.core.trace.experiment.TmfExperiment;
import org.eclipse.tracecompass.tmf.ctf.core.event.CtfTmfEvent;
import org.eclipse.tracecompass.tmf.ctf.core.trace.CtfTmfTrace;
import org.eclipse.tracecompass.xaf.core.statemachine.StateMachineAnalysis;
import org.eclipse.tracecompass.xaf.core.statemachine.StateMachineBenchmark;
import org.junit.Test;

/**
 * @author xaf
 *
 */
public class TestMyFirstAnalysis {

    /**
     * @author Raphaël Beamonte
     *
     */
    public static class SearchTraces {
        /**
         * @param path
         * @return
         */
        public static List<String> ust(String path) {
            return all(path).get("ust");
        }

        /**
         * @param path
         * @return
         */
        public static List<String> kernel(String path) {
            return all(path).get("kernel");
        }

        /**
         * @param path
         * @return
         */
        public static Map<String, List<String>> all(String path) {
            return all(path, path);
        }

        private static Map<String, List<String>> all(String path, String origPath) {
            Map<String, List<String>> traces = new HashMap<>();

            List<String> tracesUST = new ArrayList<>();
            traces.put("ust", tracesUST);

            List<String> tracesKernel = new ArrayList<>();
            traces.put("kernel", tracesKernel);

            File root = new File(path);
            File[] list = root.listFiles();

            if (list == null) {
                return traces;
            }

            for (File f : list) {
                if (f.isDirectory()) {
                    Map<String, List<String>> newTraces = all(f.getAbsolutePath(), origPath);
                    tracesUST.addAll(newTraces.get("ust"));
                    tracesKernel.addAll(newTraces.get("kernel"));
                } else {
                    if (f.getName().equals("metadata")) {
                        if (f.getParent().startsWith(new File(origPath, "ust").getAbsolutePath())) {
                            tracesUST.add(f.getParent());
                        } else if (f.getParent().startsWith(new File(origPath, "kernel").getAbsolutePath())) {
                            tracesKernel.add(f.getParent());
                        }
                    }
                }
            }

            return traces;
        }
    }

    /**
     * @throws TmfTraceException
     */
    @Test
    public void testOfMyFirstAnalysis() throws TmfTraceException {

        // trace-ku-vtid
        // trace-ku-vtid-polling
        /*
         * String txtTrace = "trace-ku-vtid-polling";
         *
         * String txtTraceUST =
         * "/home/xaf/Dropbox/Recherche/MODELIZE/"+txtTrace+
         * "/ust/uid/1000/64-bit"; String txtTraceKnl =
         * "/home/xaf/Dropbox/Recherche/MODELIZE/"+txtTrace+"/kernel";
         */

        Map<String, String> env = System.getenv();
        String txtTrace = env.get("TRACE");
        if (txtTrace == null) {
            // txtTrace = "usecase-preempt-sysevents-20150506-213518"; //27MB
            // txtTrace = "usecase-preempt-20150525-022356"; // 509 MB
            txtTrace = "usecase-preempt-20150525-182316"; // 36 MB
        }
        // txtTrace =
        // "/home/xaf/Dropbox/Recherche/MODELIZE/tool_modelbench/modelbench_trace_1024_1";

        if (!new File(txtTrace).isDirectory()) {
            txtTrace = "/storage/RESOURCES_ARTICLE/CAE/" + txtTrace;
        }

        /*
         * String txtTraceUST = txtTrace+"/ust/uid/0/64-bit"; String txtTraceKnl
         * = txtTrace+"/kernel";
         *
         * LttngUstTrace traceUST = new LttngUstTrace();
         * traceUST.initTrace(null, txtTraceUST, CtfTmfEvent.class);
         *
         * LttngKernelTrace traceKernel = new LttngKernelTrace();
         * traceKernel.initTrace(null, txtTraceKnl, CtfTmfEvent.class);
         *
         * CtfTmfTrace[] traces = { traceUST, traceKernel };
         */
        // CtfTmfTrace[] traces = { traceUST };

        Map<String, List<String>> txtTracePaths = SearchTraces.all(txtTrace);
        List<CtfTmfTrace> tracesList = new ArrayList<>();
        for (String txtTraceKnl : txtTracePaths.get("kernel")) {
            LttngKernelTrace traceKernel = new LttngKernelTrace();
            traceKernel.initTrace(null, txtTraceKnl, CtfTmfEvent.class);
            tracesList.add(traceKernel);
        }
        for (String txtTraceUST : txtTracePaths.get("ust")) {
            LttngUstTrace traceUST = new LttngUstTrace();
            traceUST.initTrace(null, txtTraceUST, CtfTmfEvent.class);
            tracesList.add(traceUST);
        }

        if (tracesList.isEmpty()) {
            System.out.println("No valid trace provided");
            assertTrue(false);
        }

        CtfTmfTrace[] traces = tracesList.toArray(new CtfTmfTrace[tracesList.size()]);

        TmfExperiment exp = new TmfExperiment(CtfTmfEvent.class, "Test experiment " + txtTrace, traces, TmfExperiment.DEFAULT_INDEX_PAGE_SIZE, null);
        // exp.getTraces().

        /*
         * KernelAnalysis fKernelAnalysisModule = new KernelAnalysis(); try {
         * fKernelAnalysisModule.setTrace(exp); } catch (TmfAnalysisException e)
         * { fail(e.getMessage()); } if (fKernelAnalysisModule.isAutomatic()) {
         * fKernelAnalysisModule.waitForCompletion(); } else {
         * executeAnalysis(fKernelAnalysisModule, new NullProgressMonitor()); }
         * ITmfStateSystem ss = fKernelAnalysisModule.getStateSystem();
         * assertNotNull(ss); System.out.println(ss);
         */

        // TmfTraceOpenedSignal signalt = new TmfTraceOpenedSignal(this,
        // traceKernel, null);

        TmfTraceOpenedSignal signal = new TmfTraceOpenedSignal(this, exp, null);
        TmfSignalManager.dispatchSignal(signal);
        /*for (CtfTmfTrace trace : tracesList) {
            trace.traceOpened(signal);
        }
        exp.traceOpened(signal);*/

        /*
         * System.out.println(exp.getAnalysisModules()); for (IAnalysisModule m
         * : exp.getAnalysisModules()) { System.out.println(m.getId()); }
         */

        String[] modules = {
                // KernelAnalysis.ID,
                // CriticalPathModule.ANALYSIS_ID,
                // LttngKernelExecutionGraph.ANALYSIS_ID,
                StateMachineAnalysis.ANALYSIS_ID,
        };

        for (String moduleID : modules) {
            IAnalysisModule module = exp.getAnalysisModule(moduleID);
            if (module == null) {
                throw new RuntimeException("the analysis module is null and should not be");
            }

            if (module.isAutomatic()) {
                module.waitForCompletion();
            } else {
                StateMachineBenchmark benchmarkObject = new StateMachineBenchmark("Complete analysis");
                executeAnalysis(module, new NullProgressMonitor());
                benchmarkObject.stop();
            }
        }

        assertTrue(true);
    }

    /**
     * Calls the {@link TmfAbstractAnalysisModule#executeAnalysis} method of an
     * analysis module. This method does not return until the analysis is
     * completed and it returns the result of the method. It allows to execute
     * the analysis without requiring an Eclipse job and waiting for completion.
     *
     * @param module
     *            The analysis module to execute
     * @param monitor
     *            The progress monitor
     * @return The return value of the
     *         {@link TmfAbstractAnalysisModule#executeAnalysis} method
     */
    public static boolean executeAnalysis(IAnalysisModule module, IProgressMonitor monitor) {
        if (module instanceof TmfAbstractAnalysisModule) {
            try {
                Class<?>[] argTypes = new Class[] { IProgressMonitor.class };
                Method method = TmfAbstractAnalysisModule.class.getDeclaredMethod("executeAnalysis", argTypes);
                method.setAccessible(true);
                Object obj = method.invoke(module, monitor);
                return (Boolean) obj;
                // return method.getClass();
            } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException | NoSuchMethodException | SecurityException e) {
                e.printStackTrace();
                throw new RuntimeException(e);
            }
        }
        throw new RuntimeException("This analysis module does not have a protected method to execute. Maybe it can be executed differently? Or it is not supported yet in this method?");
    }

}
