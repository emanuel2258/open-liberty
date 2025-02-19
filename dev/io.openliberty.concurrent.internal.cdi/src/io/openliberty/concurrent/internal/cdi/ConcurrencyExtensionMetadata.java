/*******************************************************************************
 * Copyright (c) 2021,2024 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package io.openliberty.concurrent.internal.cdi;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;

import org.osgi.framework.ServiceReference;
import org.osgi.framework.Version;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ConfigurationPolicy;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferencePolicyOption;

import com.ibm.ws.cdi.extension.CDIExtensionMetadataInternal;
import com.ibm.ws.javaee.version.JavaEEVersion;

import io.openliberty.cdi.spi.CDIExtensionMetadata;
import jakarta.enterprise.concurrent.ContextService;
import jakarta.enterprise.concurrent.ManagedExecutorService;
import jakarta.enterprise.concurrent.ManagedScheduledExecutorService;
import jakarta.enterprise.inject.spi.Extension;

@Component(configurationPolicy = ConfigurationPolicy.IGNORE,
           service = CDIExtensionMetadata.class)
public class ConcurrencyExtensionMetadata implements CDIExtensionMetadata, CDIExtensionMetadataInternal {
    private static final Set<Class<?>> beanClasses = Set.of(ContextService.class,
                                                            ManagedExecutorService.class,
                                                            ManagedScheduledExecutorService.class // TODO ManagedThreadFactory.class ?
    );

    /**
     * Jakarta EE version.
     */
    public static Version eeVersion;

    /**
     * Liberty Scheduled Executor.
     */
    public static ScheduledExecutorService scheduledExecutor;

    @Override
    public boolean applicationBeansVisible() {
        return true;
    }

    @Override
    public Set<Class<?>> getBeanClasses() {
        return beanClasses;
    }

    @Override
    public Set<Class<? extends Extension>> getExtensions() {
        return Collections.singleton(ConcurrencyExtension.class);
    }

    /**
     * The service ranking of JavaEEVersion ensures we get the highest
     * Jakarta EE version for the configured features.
     */
    @Reference(policyOption = ReferencePolicyOption.GREEDY)
    protected void setEEVersion(ServiceReference<JavaEEVersion> ref) {
        String version = (String) ref.getProperty("version");
        eeVersion = Version.parseVersion(version);
    }

    @Reference(target = "(deferrable=false)")
    protected void setScheduledExecutor(ScheduledExecutorService svc) {
        scheduledExecutor = svc;
    }

    protected void unsetScheduledExecutor(ScheduledExecutorService svc) {
    }

    protected void unsetEEVersion(ServiceReference<JavaEEVersion> ref) {
    }
}