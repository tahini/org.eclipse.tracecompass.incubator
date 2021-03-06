/**********************************************************************
 * Copyright (c) 2020 École Polytechnique de Montréal
 *
 * All rights reserved. This program and the accompanying materials are
 * made available under the terms of the Eclipse Public License 2.0 which
 * accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 **********************************************************************/
package org.eclipse.tracecompass.incubator.trace.server.jersey.rest.core.tests.stubs;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * A stub class for the response to a tree request for xy charts and data trees.
 * It contains the generic response, as well as an {@link TgEntryModelStub}
 *
 * @author Geneviève Bastien
 */
public class TgTreeOutputResponseStub extends OutputResponseStub {

    private static final long serialVersionUID = 1672268267585841928L;

    private final TgEntryModelStub fModel;

    /**
     * {@link JsonCreator} Constructor from json
     *
     * @param model
     *            The model for this response
     * @param status
     *            The status of the response
     * @param statusMessage
     *            The custom status message of the response
     */
    public TgTreeOutputResponseStub(@JsonProperty("model") TgEntryModelStub model,
            @JsonProperty("status") String status,
            @JsonProperty("statusMessage") String statusMessage) {
        super(status, statusMessage);
        fModel = model;
    }

    /**
     * Get the model for this response
     *
     * @return The model for the response
     */
    public TgEntryModelStub getModel() {
        return fModel;
    }

}
