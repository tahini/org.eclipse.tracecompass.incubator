/*******************************************************************************
 * Copyright (c) 2019 École Polytechnique de Montréal
 *
 * All rights reserved. This program and the accompanying materials are
 * made available under the terms of the Eclipse Public License v1.0 which
 * accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package org.eclipse.tracecompass.incubator.internal.scripting.core.data.provider;

import java.util.concurrent.atomic.AtomicLong;

import org.eclipse.tracecompass.tmf.core.model.timegraph.TimeGraphEntryModel;

/**
 * The entry model for scripted analyses.
 *
 * @author Geneviève Bastien
 */
public class ScriptedEntryDataModel extends TimeGraphEntryModel {

    private static AtomicLong sfId = new AtomicLong();
    private final int fQuark;

    /**
     * Constructor
     *
     * @param name
     *            Name of the entry model
     * @param parentId
     *            The ID of the parent entry, or negative for no parent
     * @param quark
     *            The quark in the state system containing the data to display
     */
    public ScriptedEntryDataModel(String name, long parentId, int quark) {
        super(sfId.getAndIncrement(), parentId, name, 0L, Long.MAX_VALUE);
        fQuark = quark;
    }

    /**
     * Get the quark in the state system containing the data to display
     *
     * @return The quark to display
     */
    public int getQuark() {
        return fQuark;
    }

}
