/*******************************************************************************
 * Copyright (c) 2019 École Polytechnique de Montréal
 *
 * All rights reserved. This program and the accompanying materials are
 * made available under the terms of the Eclipse Public License v1.0 which
 * accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package org.eclipse.tracecompass.incubator.analysis.core.weighted.tree;

import java.util.Map;

import org.eclipse.tracecompass.tmf.core.model.OutputElementStyle;

/**
 * @author Geneviève Bastien
 */
public interface IDataPalette {

    /**
     * @param callsite
     * @return
     */
    OutputElementStyle getStyleFor(Object callsite);

    /**
     * @return
     */
    Map<String, OutputElementStyle> getStyles();

}
