/*******************************************************************************
 * Copyright (c) 2019 École Polytechnique de Montréal
 *
 * All rights reserved. This program and the accompanying materials are
 * made available under the terms of the Eclipse Public License v1.0 which
 * accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package org.eclipse.tracecompass.incubator.analysis.core.weighted.tree.diff;

import java.util.Map;

import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.tracecompass.incubator.analysis.core.weighted.tree.IDataPalette;
import org.eclipse.tracecompass.tmf.core.model.ITimeEventStyleStrings;
import org.eclipse.tracecompass.tmf.core.model.OutputElementStyle;
import org.eclipse.tracecompass.tmf.core.presentation.DefaultColorPaletteProvider;
import org.eclipse.tracecompass.tmf.core.presentation.IPaletteProvider;
import org.eclipse.tracecompass.tmf.core.presentation.RGBAColor;
import org.eclipse.tracecompass.tmf.core.presentation.SequentialPaletteProvider;

import com.google.common.collect.ImmutableMap;

/**
 * @author gbastien
 *
 */
public class DifferentialPalette implements IDataPalette {

    private static @Nullable DifferentialPalette fInstance = null;
    private static final int NB_COLORS = 10;
    private static final OutputElementStyle WHITE_STYLE;
    private static final String LESS_STYLES = "less"; //$NON-NLS-1$
    private static final String MORE_STYLES = "more"; //$NON-NLS-1$
    private static final Map<String, OutputElementStyle> STYLES;

    static {
        WHITE_STYLE = new OutputElementStyle(null, ImmutableMap.of(ITimeEventStyleStrings.fillColor(), new RGBAColor(255,255,255).toInt()));

        // Create the green palette (for less)
        IPaletteProvider palette = SequentialPaletteProvider.create(DefaultColorPaletteProvider.GREEN, NB_COLORS);
        int i = 10;
        ImmutableMap.Builder<String, OutputElementStyle> builder = new ImmutableMap.Builder<>();
        for (RGBAColor color : palette.get()) {
            builder.put(LESS_STYLES + String.valueOf(i), new OutputElementStyle(null, ImmutableMap.of(ITimeEventStyleStrings.fillColor(), color.toInt())));
            i--;
        }

        // Create the red palette (for more)
        palette = SequentialPaletteProvider.create(DefaultColorPaletteProvider.RED, NB_COLORS);
        i = 10;
        for (RGBAColor color : palette.get()) {
            builder.put(MORE_STYLES + String.valueOf(i), new OutputElementStyle(null, ImmutableMap.of(ITimeEventStyleStrings.fillColor(), color.toInt())));
            i--;
        }
        STYLES = builder.build();
    }

    private DifferentialPalette() {
        // Private constructor
    }

    /**
     * Get the instance of this palette
     *
     * @return The instance of the palette
     */
    public static DifferentialPalette getInstance() {
        DifferentialPalette instance = fInstance;
        if (instance == null) {
            instance = new DifferentialPalette();
            fInstance = instance;
        }
        return instance;
    }

    @Override
    public OutputElementStyle getStyleFor(Object callsite) {
        if (callsite instanceof DifferentialWeightedTree) {
            DifferentialWeightedTree<?> tree = (DifferentialWeightedTree<?>) callsite;
            double difference = tree.getDifference();
            if (difference == Double.NaN) {
                return STYLES.getOrDefault(MORE_STYLES + NB_COLORS, WHITE_STYLE);
            }
            if (difference == 0) {
                return WHITE_STYLE;
            }
            if (difference < 0) {
                // The heat will be between 1 and NB_COLORS
                int diffHeat = Math.max(1, Math.min(NB_COLORS, (int) difference * 100));
                return STYLES.getOrDefault(LESS_STYLES + diffHeat, WHITE_STYLE);
            }
            int diffHeat = Math.max(1, Math.min(NB_COLORS, (int) difference * 100));
            return STYLES.getOrDefault(MORE_STYLES + diffHeat, WHITE_STYLE);
        }
        return WHITE_STYLE;
    }

    @Override
    public Map<String, OutputElementStyle> getStyles() {
        return STYLES;
    }

}
