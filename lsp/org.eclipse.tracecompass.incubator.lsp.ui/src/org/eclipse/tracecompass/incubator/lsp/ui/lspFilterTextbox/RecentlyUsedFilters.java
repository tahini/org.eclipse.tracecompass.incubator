/*******************************************************************************
 * Copyright (c) 2019 École Polytechnique de Montréal
 *
 * All rights reserved. This program and the accompanying materials are
 * made available under the terms of the Eclipse Public License v1.0 which
 * accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package org.eclipse.tracecompass.incubator.lsp.ui.lspFilterTextbox;

import java.util.ArrayList;
import java.util.List;
import java.util.prefs.Preferences;

/**
 * Class to save and get the recently used filters
 *
 * @author Jeremy Dube
 *
 */
public class RecentlyUsedFilters {

    private final Preferences fPreferences;
    private final int fSize;
    private final List<String> fFilters;

    /**
     * Constructor
     *
     * @param size
     *            size of the filters to save
     */
    public RecentlyUsedFilters(int size, String listName) {
        fSize = size;
        fPreferences = Preferences.userRoot().node(listName);
        fFilters = new ArrayList<>(fSize);
        getFilters();
    }

    /**
     * Add a filter to the recently used filters
     *
     * @param filter
     *            filter to add
     */
    public void addFilter(String filter) {
        if (fFilters.size() >= fSize) {
            fFilters.remove(0);
        }
        fFilters.add(filter);
        saveFilters();
    }

    /**
     * Get the recently used filters
     *
     * @return an array starting with the latest used filter at first index
     */
    public List<String> getRecently() {
        List<String> filters = new ArrayList<>(fSize);
        for (int i = fFilters.size() - 1; i >= 0; i--) {
            filters.add(fFilters.get(i));
        }
        return filters;
    }

    /**
     * Clear the filters saved
     */
    public void clearFilters() {
        for (int i = 0; i < fFilters.size(); i++) {
            fPreferences.remove("filter" + i);
        }
        fFilters.clear();
    }

    /**
     * Get filters from the preferences
     */
    private void getFilters() {
        for (int i = 0; i < fSize; i++) {
            String filter = fPreferences.get("filter" + i, null);
            if (filter != null) {
                fFilters.add(filter);
            }
        }
    }

    /**
     * Save filters to the preferences
     */
    private void saveFilters() {
        for (int i = 0; i < fFilters.size(); i++) {
            fPreferences.put("filter" + i, fFilters.get(i));
        }
    }
}
