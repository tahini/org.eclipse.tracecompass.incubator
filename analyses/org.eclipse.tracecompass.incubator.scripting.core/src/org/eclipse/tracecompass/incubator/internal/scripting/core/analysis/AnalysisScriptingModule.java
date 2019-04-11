/*******************************************************************************
 * Copyright (c) 2019 École Polytechnique de Montréal
 *
 * All rights reserved. This program and the accompanying materials are
 * made available under the terms of the Eclipse Public License v1.0 which
 * accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package org.eclipse.tracecompass.incubator.internal.scripting.core.analysis;

import java.util.Collections;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.Function;

import org.eclipse.ease.modules.WrapToScript;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.tracecompass.internal.tmf.analysis.xml.core.fsm.model.DataDrivenStateSystemPath;
import org.eclipse.tracecompass.internal.tmf.analysis.xml.core.fsm.model.values.DataDrivenValueConstant;
import org.eclipse.tracecompass.internal.tmf.analysis.xml.core.output.DataDrivenOutputEntry;
import org.eclipse.tracecompass.internal.tmf.analysis.xml.core.output.DataDrivenTimeGraphProviderFactory;
import org.eclipse.tracecompass.internal.tmf.analysis.xml.core.output.DataDrivenXYDataProvider.DisplayType;
import org.eclipse.tracecompass.internal.tmf.analysis.xml.core.output.XmlDataProviderManager;
import org.eclipse.tracecompass.statesystem.core.ITmfStateSystemBuilder;
import org.eclipse.tracecompass.statesystem.core.statevalue.ITmfStateValue;
import org.eclipse.tracecompass.tmf.core.event.ITmfEvent;
import org.eclipse.tracecompass.tmf.core.event.ITmfEventField;
import org.eclipse.tracecompass.tmf.core.model.timegraph.ITimeGraphDataProvider;
import org.eclipse.tracecompass.tmf.core.model.timegraph.TimeGraphEntryModel;
import org.eclipse.tracecompass.tmf.core.trace.ITmfTrace;
import org.eclipse.tracecompass.tmf.core.trace.TmfTraceManager;
import org.eclipse.tracecompass.tmf.core.trace.TmfTraceUtils;

/**
 * Provide an API to create an analysis
 *
 * @author Geneviève Bastien
 */
public class AnalysisScriptingModule {

    private static final String ENTRY_PATH = "path"; //$NON-NLS-1$
    private static final String ENTRY_DISPLAY = "display"; //$NON-NLS-1$
    private static final String ENTRY_NAME = "name"; //$NON-NLS-1$
    private static final String ENTRY_PARENT = "parent"; //$NON-NLS-1$
    private static final String ENTRY_ID = "id"; //$NON-NLS-1$

    /** Module identifier. */
    public static final String MODULE_ID = "/TraceCompass/Analysis"; //$NON-NLS-1$

    /**
     * Constructor
     */
    public AnalysisScriptingModule() {

    }

    @WrapToScript
    public @Nullable ScriptedAnalysis getAnalysis(String name) {
        ITmfTrace activeTrace = TmfTraceManager.getInstance().getActiveTrace();
        if (activeTrace == null) {
            return null;
        }
        return new ScriptedAnalysis(activeTrace, name);
    }

    @WrapToScript
    public @Nullable Object getFieldValue(ITmfEvent event, String fieldName) {

        final ITmfEventField field = event.getContent().getField(fieldName);

        /* If the field does not exist, see if it's a special case */
        if (field == null) {
            // This will allow to use any column as input
            return TmfTraceUtils.resolveAspectOfNameForEvent(event.getTrace(), fieldName, event);
        }
        return field.getValue();

    }

    @SuppressWarnings("restriction")
    @WrapToScript
    public @Nullable Object createTimeGraphProvider(ScriptedAnalysis analysis, Map<String, Object> data) {
        for (Entry<String, Object> entry : data.entrySet()) {
            System.out.println("entry: " + entry);
        }
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
        ITimeGraphDataProvider<TimeGraphEntryModel> provider = factory.create(analysis.getTrace(), Collections.singletonList(stateSystem), analysis.getName());
        XmlDataProviderManager.getInstance().registerDataProvider(analysis.getTrace(), analysis.getName(), provider);
        return provider;

    }

    @WrapToScript
    public Object test(Function<String, String> bla) {
        return bla.apply("a");
    }

}
