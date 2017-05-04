/*******************************************************************************
 * Copyright (c) 2016 École Polytechnique de Montréal
 *
 * All rights reserved. This program and the accompanying materials are
 * made available under the terms of the Eclipse Public License v1.0 which
 * accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package org.eclipse.tracecompass.incubator.internal.analysis.core.model;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.tracecompass.analysis.os.linux.core.tid.TidAnalysisModule;
import org.eclipse.tracecompass.incubator.analysis.core.concepts.ICpuTimeProvider;
import org.eclipse.tracecompass.incubator.analysis.core.concepts.IThreadOnCpuProvider;
import org.eclipse.tracecompass.incubator.analysis.core.model.IHostModel;
import org.eclipse.tracecompass.incubator.analysis.core.model.ModelManager;
import org.eclipse.tracecompass.tmf.core.analysis.IAnalysisModule;
import org.eclipse.tracecompass.tmf.core.analysis.ITmfNewAnalysisModuleListener;
import org.eclipse.tracecompass.tmf.core.trace.ITmfTrace;

/**
 * Listener for the {@link CompositeHostModel} that waits for new
 * modules and adds them to the appropriate model concept.
 *
 * @author Geneviève Bastien
 */
public class ModelListener implements ITmfNewAnalysisModuleListener {

    private Map<TidAnalysisModule, TidAnalysisWrapper> fTidModules = new WeakHashMap<>();

    @Override
    public void moduleCreated(@Nullable IAnalysisModule module) {
        if (module instanceof ICpuTimeProvider) {
            ICpuTimeProvider provider = (ICpuTimeProvider) module;
            for (String hostId : provider.getHostIds()) {
                IHostModel model = ModelManager.getModelFor(hostId);
                if (model instanceof CompositeHostModel) {
                    ((CompositeHostModel) model).setCpuTimeProvider(provider);
                }
            }
        }

        if (module instanceof IThreadOnCpuProvider) {
            IThreadOnCpuProvider provider = (IThreadOnCpuProvider) module;
            for (String hostId : provider.getHostIds()) {
                IHostModel model = ModelManager.getModelFor(hostId);
                if (model instanceof CompositeHostModel) {
                    ((CompositeHostModel) model).setThreadOnCpuProvider(provider);
                }
            }
        }

        if (module instanceof TidAnalysisModule) {
            TidAnalysisModule provider = (TidAnalysisModule) module;
            ITmfTrace trace = provider.getTrace();
            if (trace != null) {
                IHostModel model = ModelManager.getModelFor(trace.getHostId());
                TidAnalysisWrapper tidAnalysisWrapper = new TidAnalysisWrapper(provider, trace.getHostId());
                fTidModules.put(provider, tidAnalysisWrapper);
                ((CompositeHostModel) model).setThreadOnCpuProvider(tidAnalysisWrapper);
            }
        }
    }

    private static class TidAnalysisWrapper implements IThreadOnCpuProvider {

        private final TidAnalysisModule fModule;
        private final Collection<String> fHostIds;

        public TidAnalysisWrapper(TidAnalysisModule module, String hostId) {
            fHostIds = Collections.singleton(hostId);
            fModule = module;
        }

        @Override
        public @Nullable Integer getThreadOnCpuAtTime(int cpu, long time) {
            return fModule.getThreadOnCpuAtTime(cpu, time);
        }

        @Override
        public @NonNull Collection<@NonNull String> getHostIds() {
            return fHostIds;
        }

    }

}
