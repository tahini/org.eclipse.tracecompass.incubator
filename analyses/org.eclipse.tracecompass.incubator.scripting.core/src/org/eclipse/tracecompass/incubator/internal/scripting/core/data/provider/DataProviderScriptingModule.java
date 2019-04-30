/*******************************************************************************
 * Copyright (c) 2019 École Polytechnique de Montréal
 *
 * All rights reserved. This program and the accompanying materials are
 * made available under the terms of the Eclipse Public License v1.0 which
 * accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package org.eclipse.tracecompass.incubator.internal.scripting.core.data.provider;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import org.eclipse.ease.modules.WrapToScript;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.tracecompass.incubator.internal.scripting.core.analysis.ScriptedAnalysis;
import org.eclipse.tracecompass.internal.tmf.analysis.xml.core.fsm.model.DataDrivenStateSystemPath;
import org.eclipse.tracecompass.internal.tmf.analysis.xml.core.fsm.model.values.DataDrivenValueConstant;
import org.eclipse.tracecompass.internal.tmf.analysis.xml.core.output.DataDrivenOutputEntry;
import org.eclipse.tracecompass.internal.tmf.analysis.xml.core.output.DataDrivenTimeGraphProviderFactory;
import org.eclipse.tracecompass.internal.tmf.analysis.xml.core.output.DataDrivenXYDataProvider.DisplayType;
import org.eclipse.tracecompass.statesystem.core.ITmfStateSystem;
import org.eclipse.tracecompass.statesystem.core.ITmfStateSystemBuilder;
import org.eclipse.tracecompass.statesystem.core.statevalue.ITmfStateValue;
import org.eclipse.tracecompass.tmf.core.model.filters.TimeQueryFilter;
import org.eclipse.tracecompass.tmf.core.model.timegraph.ITimeGraphDataProvider;
import org.eclipse.tracecompass.tmf.core.model.timegraph.ITimeGraphEntryModel;
import org.eclipse.tracecompass.tmf.core.model.timegraph.TimeGraphEntryModel;
import org.eclipse.tracecompass.tmf.core.model.timegraph.ITimeGraphRowModel;
import org.eclipse.tracecompass.tmf.core.model.timegraph.ITimeGraphArrow;
import org.eclipse.tracecompass.tmf.core.model.timegraph.TimeGraphArrow;
import org.eclipse.tracecompass.tmf.core.model.tree.ITmfTreeDataModel;

public class DataProviderScriptingModule {

    private static final String ENTRY_PATH = "path"; //$NON-NLS-1$
    private static final String ENTRY_DISPLAY = "display"; //$NON-NLS-1$
    private static final String ENTRY_NAME = "name"; //$NON-NLS-1$
    private static final String ENTRY_PARENT = "parent"; //$NON-NLS-1$
    private static final String ENTRY_ID = "id"; //$NON-NLS-1$

    private static final String ENTRY_FIELD_QUARK = "quark"; //$NON-NLS-1$
    private static final String ENTRY_FIELD_PARENT_ID = "parentId"; //$NON-NLS-1$

    @WrapToScript
    public @Nullable Object createTimeGraphProvider(ScriptedAnalysis analysis, Map<String, Object> data) {
        Object pathObj = data.get(ENTRY_PATH);
        if (pathObj == null) {
            return null;
        }
        String path = String.valueOf(pathObj);
        Object displayObj = data.get(ENTRY_DISPLAY);
        String display = (displayObj == null) ? null : String.valueOf(displayObj);

        Object nameObj = data.get(ENTRY_NAME);
        String name = (nameObj == null) ? null : String.valueOf(nameObj);

        Object parentObj = data.get(ENTRY_PARENT);
        String parent = (parentObj == null) ? null : String.valueOf(parentObj);

        Object idObj = data.get(ENTRY_ID);
        String id = (idObj == null) ? null : String.valueOf(idObj);

        DataDrivenOutputEntry entry = new DataDrivenOutputEntry(Collections.emptyList(), path, null, true,
                new DataDrivenStateSystemPath(display == null ? Collections.emptyList() : Collections.singletonList(new DataDrivenValueConstant(null, ITmfStateValue.Type.NULL, display))),
                id == null ? null : new DataDrivenStateSystemPath(Collections.singletonList(new DataDrivenValueConstant(null, ITmfStateValue.Type.NULL, id))),
                parent == null ? null : new DataDrivenStateSystemPath(Collections.singletonList(new DataDrivenValueConstant(null, ITmfStateValue.Type.NULL, parent))),
                name == null ? null : new DataDrivenStateSystemPath(Collections.singletonList(new DataDrivenValueConstant(null, ITmfStateValue.Type.NULL, name))),
                DisplayType.ABSOLUTE);
        DataDrivenTimeGraphProviderFactory factory = new DataDrivenTimeGraphProviderFactory(Collections.singletonList(entry), Collections.singleton(analysis.getName()), Collections.emptyList());
        ITmfStateSystemBuilder stateSystem = analysis.getStateSystem(true);
        if (stateSystem == null) {
            return null;
        }
        ITimeGraphDataProvider<TimeGraphEntryModel> provider = factory.create(analysis.getTrace(), Collections.singletonList(stateSystem), ScriptingDataProviderManager.PROVIDER_ID + ':' + analysis.getName());
        ScriptingDataProviderManager.getInstance().registerDataProvider(analysis.getTrace(), provider);
        return provider;
    }

    @WrapToScript
    public @Nullable ITmfTreeDataModel createEntry(String name, Map<String, Object> data) {
        Object quarkObj = data.get(ENTRY_FIELD_QUARK);
        int quark = (!(quarkObj instanceof Number)) ? ITmfStateSystem.INVALID_ATTRIBUTE : ((Number) quarkObj).intValue();
        Object parentObj = data.get(ENTRY_FIELD_PARENT_ID);
        int parent = (!(parentObj instanceof Number)) ? -1 : ((Number) parentObj).intValue();

        return new ScriptedEntryDataModel(name, parent, quark);
    }

    @WrapToScript
    public @Nullable ITimeGraphArrow createArrow(long sourceId, long destinationId, long time, long duration, int value) {
        return new TimeGraphArrow(sourceId, destinationId, time, duration, value);
    }

    @WrapToScript
    public ITimeGraphDataProvider<ITimeGraphEntryModel> createScriptedTimeGraphProvider(ScriptedAnalysis analysis,
            Function<TimeQueryFilter, @Nullable List<ITimeGraphEntryModel>> entryMethod,
            @Nullable Function<TimeQueryFilter, @Nullable List<ITimeGraphRowModel>> rowModelMethod,
            @Nullable Function<TimeQueryFilter, @Nullable List<ITimeGraphArrow>> arrowMethod) {
        ITimeGraphDataProvider<ITimeGraphEntryModel> provider = new ScriptedTimeGraphDataProvider(analysis, entryMethod, rowModelMethod, arrowMethod);
        ScriptingDataProviderManager.getInstance().registerDataProvider(analysis.getTrace(), provider);
        return provider;
    }

}
