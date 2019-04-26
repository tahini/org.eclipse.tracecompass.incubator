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
import java.util.Map;

import org.eclipse.ease.modules.WrapToScript;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.tracecompass.incubator.internal.scripting.core.analysis.ScriptedAnalysis;
import org.eclipse.tracecompass.internal.tmf.analysis.xml.core.fsm.model.DataDrivenStateSystemPath;
import org.eclipse.tracecompass.internal.tmf.analysis.xml.core.fsm.model.values.DataDrivenValueConstant;
import org.eclipse.tracecompass.internal.tmf.analysis.xml.core.output.DataDrivenOutputEntry;
import org.eclipse.tracecompass.internal.tmf.analysis.xml.core.output.DataDrivenTimeGraphProviderFactory;
import org.eclipse.tracecompass.internal.tmf.analysis.xml.core.output.DataDrivenXYDataProvider.DisplayType;
import org.eclipse.tracecompass.statesystem.core.ITmfStateSystemBuilder;
import org.eclipse.tracecompass.statesystem.core.statevalue.ITmfStateValue;
import org.eclipse.tracecompass.tmf.core.model.timegraph.ITimeGraphDataProvider;
import org.eclipse.tracecompass.tmf.core.model.timegraph.TimeGraphEntryModel;

/**
 * This ease module gives helpers to define various types of data provider from
 * EASE analyses.
 *
 * @author Geneviève Bastien
 */
@SuppressWarnings("restriction")
public class DataProviderScriptingModule {

    private static final String ENTRY_PATH = "path"; //$NON-NLS-1$
    private static final String ENTRY_DISPLAY = "display"; //$NON-NLS-1$
    private static final String ENTRY_NAME = "name"; //$NON-NLS-1$
    private static final String ENTRY_PARENT = "parent"; //$NON-NLS-1$
    private static final String ENTRY_ID = "id"; //$NON-NLS-1$

    /**
     * Create a basic data driven data provider from the attributes of the state
     * system associated with this analysis. It is an equivalent of writing an
     * XML file for this analysis. Ideal when the one only wishes to display the
     * values in a state system.
     *
     * The data to describe the view is similar to that of an XML view:
     *
     * path: The path in the state system of the entries to display
     *
     * display: The name in the state system, relative to the path, of the
     * attribute to display. Default is the path itself
     *
     * name: The name in the state system, relative to the path, of the
     * attribute containing the name of the entry. Default is the name of the
     * entry attribute
     *
     * parent: The name in the state system, relative to the path, of the
     * attribute whose value represents the parent, to add a parent/child
     * relationship between the entries. Default is none
     *
     * id: The name in the state system, relative to the path, of the attribute
     * whose value is the ID of this entry. It can be used with a parent to
     * arrange parent/child relationship. Default is none
     *
     *
     * @param analysis
     *            The analysis from which to create the data provider. Its state
     *            system will be used to get the data.
     * @param data
     *            Additional data to describe the views.
     * @return The time graph data provider or <code>null</code> if something
     *         was missing
     */
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

}
