/*******************************************************************************
 * Copyright (c) 2018 École Polytechnique de Montréal
 *
 * All rights reserved. This program and the accompanying materials are
 * made available under the terms of the Eclipse Public License v1.0 which
 * accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package org.eclipse.tracecompass.incubator.internal.callstack.core.palette;

import java.util.Map;
import java.util.Map.Entry;

import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.tracecompass.analysis.os.linux.core.model.ProcessStatus;
import org.eclipse.tracecompass.incubator.analysis.core.concepts.ICallStackSymbol;
import org.eclipse.tracecompass.incubator.analysis.core.weighted.tree.AggregatedCallSite;
import org.eclipse.tracecompass.incubator.analysis.core.weighted.tree.IDataPalette;
import org.eclipse.tracecompass.incubator.callstack.core.instrumented.ICalledFunction;
import org.eclipse.tracecompass.incubator.internal.callstack.core.instrumented.callgraph.AggregatedThreadStatus;
import org.eclipse.tracecompass.internal.analysis.os.linux.core.threadstatus.ThreadStatusDataProvider;
import org.eclipse.tracecompass.tmf.core.model.ITimeEventStyleStrings;
import org.eclipse.tracecompass.tmf.core.model.OutputElementStyle;
import org.eclipse.tracecompass.tmf.core.model.timegraph.TimeGraphState;
import org.eclipse.tracecompass.tmf.core.presentation.IPaletteProvider;
import org.eclipse.tracecompass.tmf.core.presentation.RGBAColor;
import org.eclipse.tracecompass.tmf.core.presentation.RotatingPaletteProvider;

import com.google.common.collect.ImmutableMap;

/**
 * Class to manage the colors of the flame chart and flame graph views
 *
 * @author Geneviève Bastien
 */
@SuppressWarnings("restriction")
public final class FlameDefaultPalette implements IDataPalette {

    /**
     * The state index for the multiple state
     */
    private static final int NUM_COLORS = 20;
    private static final String KERNEL_STATE_PREFIX = "k"; //$NON-NLS-1$

    private static final Map<String, OutputElementStyle> CONTROL_FLOW_STYLES = ThreadStatusDataProvider.getStyleMap();
    private static final OutputElementStyle DEFAULT_CF_STYLE;
    private static final Map<String, OutputElementStyle> STYLES;

    static {
        IPaletteProvider palette = new RotatingPaletteProvider.Builder().setNbColors(NUM_COLORS).build();
        int i = 0;
        ImmutableMap.Builder<String, OutputElementStyle> builder = new ImmutableMap.Builder<>();
        for (RGBAColor color : palette.get()) {
            builder.put(String.valueOf(i), new OutputElementStyle(null, ImmutableMap.of(ITimeEventStyleStrings.fillColor(), color.toInt())));
            i++;
        }
        // Add the kernel entries to the builder with a prefix
        for (Entry<String,OutputElementStyle> kernelEntry : CONTROL_FLOW_STYLES.entrySet()) {
            builder.put(KERNEL_STATE_PREFIX + kernelEntry.getKey(), kernelEntry.getValue());
        }
        STYLES = builder.build();
        OutputElementStyle defaultCfStyle = CONTROL_FLOW_STYLES.get(String.valueOf(ProcessStatus.UNKNOWN.getStateValue().unboxInt()));
        DEFAULT_CF_STYLE = defaultCfStyle != null ? defaultCfStyle : new OutputElementStyle(null, ImmutableMap.of());
    }

    private static @Nullable FlameDefaultPalette fInstance = null;

    private FlameDefaultPalette() {

    }

    /**
     * Get the instance of this palette
     *
     * @return The instance of the palette
     */
    public static FlameDefaultPalette getInstance() {
        FlameDefaultPalette instance = fInstance;
        if (instance == null) {
            instance = new FlameDefaultPalette();
            fInstance = instance;
        }
        return instance;
    }

    /**
     * Get the map of styles for this palette
     *
     * @return The styles
     */
    @Override
    public Map<String, OutputElementStyle> getStyles() {
        return STYLES;
    }

    /**
     * Get the style element for a given value
     *
     * FIXME Those instance check are not acceptable
     *
     * @param callsite
     *            The value to get an element for
     * @return The output style
     */
    @Override
    public OutputElementStyle getStyleFor(Object callsite) {
        if (callsite instanceof AggregatedThreadStatus) {
            int kernelStatus = ((AggregatedThreadStatus) callsite).getProcessStatus().getStateValue().unboxInt();
            return CONTROL_FLOW_STYLES.getOrDefault(String.valueOf(kernelStatus), DEFAULT_CF_STYLE);
        }
        if (callsite instanceof AggregatedCallSite) {
            ICallStackSymbol value = ((AggregatedCallSite) callsite).getObject();
            int hashCode = value.hashCode();
            return STYLES.getOrDefault(String.valueOf(Math.floorMod(hashCode, NUM_COLORS)), DEFAULT_CF_STYLE);
        }
        if (callsite instanceof ICalledFunction) {
            Object value = ((ICalledFunction) callsite).getSymbol();
            int hashCode = value.hashCode();
            return STYLES.getOrDefault(String.valueOf(Math.floorMod(hashCode, NUM_COLORS)), DEFAULT_CF_STYLE);
        }
        if (callsite instanceof TimeGraphState) {
            return CONTROL_FLOW_STYLES.getOrDefault(((TimeGraphState) callsite).getValue(), DEFAULT_CF_STYLE);
        }
        return DEFAULT_CF_STYLE;
    }

}
