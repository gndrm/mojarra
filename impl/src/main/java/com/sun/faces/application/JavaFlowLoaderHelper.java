/*
 * Copyright (c) 1997, 2018 Oracle and/or its affiliates. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0, which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * This Source Code may also be made available under the following Secondary
 * Licenses when the conditions for such availability set forth in the
 * Eclipse Public License v. 2.0 are satisfied: GNU General Public License,
 * version 2 with the GNU Classpath Exception, which is available at
 * https://www.gnu.org/software/classpath/license.html.
 *
 * SPDX-License-Identifier: EPL-2.0 OR GPL-2.0 WITH Classpath-exception-2.0
 */

package com.sun.faces.application;

import static com.sun.faces.config.WebConfiguration.WebContextInitParameter.ClientWindowMode;
import static com.sun.faces.util.Util.getCdiBeanManager;
import static java.util.logging.Level.SEVERE;
import static java.util.logging.Level.WARNING;

import java.io.IOException;
import java.util.List;
import java.util.logging.Logger;

import javax.enterprise.context.spi.CreationalContext;
import javax.enterprise.inject.spi.Bean;
import javax.enterprise.inject.spi.BeanManager;
import javax.enterprise.inject.spi.Producer;
import javax.faces.context.FacesContext;
import javax.faces.flow.Flow;
import javax.faces.flow.FlowHandler;
import javax.faces.flow.builder.FlowDefinition;

import com.sun.faces.config.WebConfiguration;
import com.sun.faces.flow.FlowDiscoveryCDIExtension;
import com.sun.faces.util.FacesLogger;

class JavaFlowLoaderHelper {

    private static final Logger LOGGER = FacesLogger.APPLICATION.getLogger();

    synchronized void loadFlows(FacesContext context, FlowHandler flowHandler) throws IOException {
        BeanManager beanManager = getCdiBeanManager(context);

        Bean<?> extensionImpl = beanManager.resolve(beanManager.getBeans(FlowDiscoveryCDIExtension.class));

        if (extensionImpl == null) {
            if (LOGGER.isLoggable(SEVERE)) {
                LOGGER.log(SEVERE, "Unable to obtain {0} from CDI implementation.  Flows described with {1} are unavailable.",
                        new String[] { FlowDiscoveryCDIExtension.class.getName(), FlowDefinition.class.getName() });
            }
            return;
        }

        CreationalContext<?> creationalContext = beanManager.createCreationalContext(extensionImpl);
        FlowDiscoveryCDIExtension myExtension = (FlowDiscoveryCDIExtension) beanManager.getReference(extensionImpl, FlowDiscoveryCDIExtension.class,
                creationalContext);

        List<Producer<Flow>> flowProducers = myExtension.getFlowProducers();
        WebConfiguration config = WebConfiguration.getInstance();
        if (!flowProducers.isEmpty()) {
            enableClientWindowModeIfNecessary(context);
        }

        for (Producer<Flow> flowProducer : flowProducers) {
            Flow toAdd = flowProducer.produce(beanManager.<Flow>createCreationalContext(null));
            if (null == toAdd) {
                LOGGER.log(SEVERE, "Flow producer method {0}() returned null.  Ignoring.", flowProducer.toString());
            } else {
                flowHandler.addFlow(context, toAdd);
                config.setHasFlows(true);
            }
        }
    }

    private void enableClientWindowModeIfNecessary(FacesContext context) {

        WebConfiguration config = WebConfiguration.getInstance(context.getExternalContext());

        String optionValue = config.getOptionValue(ClientWindowMode);

        boolean clientWindowNeedsEnabling = false;
        if ("none".equals(optionValue)) {
            clientWindowNeedsEnabling = true;

            LOGGER.log(WARNING, "{0} was set to none, but Faces Flows requires {0} is enabled.  Setting to ''url''.",
                    new Object[] { ClientWindowMode.getQualifiedName() });

        } else if (optionValue == null) {
            clientWindowNeedsEnabling = true;
        }

        if (clientWindowNeedsEnabling) {
            config.setOptionValue(ClientWindowMode, "url");
        }
    }
}