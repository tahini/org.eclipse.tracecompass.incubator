/*******************************************************************************
 * Copyright (c) 2019 École Polytechnique de Montréal
 *
 * All rights reserved. This program and the accompanying materials are
 * made available under the terms of the Eclipse Public License v1.0 which
 * accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package org.eclipse.tracecompass.incubator.internal.provisional.scripting.core.utils;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Geneviève Bastien
 *
 * @param <T> The type of object to go in the list
 */
public class ListWrapper<T> {

    private final List<T> fList = new ArrayList<>();

    /**
     * Constructor
     */
    public ListWrapper() {

    }

    public List<T> getList() {
        return fList;
    }

}
