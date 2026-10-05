/*******************************************************************************
 * Copyright (c) 2016, 2026 IBM Corporation and others.
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
package com.ibm.ws.webserver.plugin.runtime.listeners;

import java.io.File;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import javax.servlet.SessionCookieConfig;

import org.osgi.framework.BundleContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ConfigurationPolicy;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;

import com.ibm.websphere.ras.Tr;
import com.ibm.websphere.ras.TraceComponent;
import com.ibm.ws.container.service.app.deploy.ApplicationInfo;
import com.ibm.ws.container.service.app.deploy.ModuleInfo;
import com.ibm.ws.container.service.state.ApplicationStateListener;
import com.ibm.ws.container.service.state.ModuleStateListener;
import com.ibm.ws.container.service.state.StateChangeException;
import com.ibm.ws.runtime.update.RuntimeUpdateListener;
import com.ibm.ws.runtime.update.RuntimeUpdateManager;
import com.ibm.ws.runtime.update.RuntimeUpdateNotification;
import com.ibm.ws.webcontainer.httpsession.SessionManager;
import com.ibm.ws.webcontainer.webapp.WebApp;
import com.ibm.ws.webserver.plugin.runtime.interfaces.PluginUtilityConfigGenerator;
import com.ibm.wsspi.kernel.service.location.WsLocationAdmin;
import com.ibm.wsspi.kernel.service.location.WsResource;
import com.ibm.wsspi.kernel.service.utils.FrameworkState;
import com.ibm.wsspi.webcontainer.osgi.mbeans.GeneratePluginConfig;

import io.openliberty.checkpoint.spi.CheckpointPhase;

/**
 *
 */
@Component(service = { ApplicationStateListener.class,
                       RuntimeUpdateListener.class,
                       ModuleStateListener.class }, configurationPolicy = ConfigurationPolicy.IGNORE, immediate = true, property = { "service.vendor=IBM" })
public class GeneratePluginConfigListener implements RuntimeUpdateListener, ApplicationStateListener, ModuleStateListener {

    private static final TraceComponent tc = Tr.register(GeneratePluginConfigListener.class);

    /**
     * How long (in milliseconds) to wait after the last application-lifecycle event before
     * actually submitting the plugin generation task. This debounce window coalesces multiple
     * events (e.g. N apps starting sequentially on initial startup, or a hot-deploy wave that
     * stops and restarts several apps) into a single generation.
     *
     * The value is deliberately conservative: plugin generation is not time-critical and the
     * window must be large enough to cover the gap between consecutive sequential app-start
     * events on any supported hardware.
     */
    static final long DEBOUNCE_DELAY_MS = 500L;

    private int updatesInProgress = 0, appsInService = 0;
    private volatile ExecutorService executorSrvc;
    private volatile ScheduledExecutorService scheduledExec;

    /**
     * Holds the most recently scheduled (but not yet executed) generation task.
     * Each call to {@link #submitGeneratePluginTask()} cancels whatever is in here and
     * replaces it with a freshly scheduled task, implementing the debounce.
     */
    private final AtomicReference<ScheduledFuture<?>> pendingGeneration = new AtomicReference<>();

    private GeneratePluginConfig gpc;
    private ConcurrentHashMap<String, String> cookieNames = null;

    private SessionManager smgr;
    private WsLocationAdmin locationService;

    private static GeneratePluginConfigListener theListener = null;
    private static final String CHECKPOINT_GEN_PLUGIN = "io.openliberty.checkpoint.generate.plugin";
    private boolean enablePlugin;

    public static GeneratePluginConfigListener getGeneratePluginConfigListener() {
        return theListener;
    }

    public GeneratePluginConfigListener() {
        cookieNames = new ConcurrentHashMap<String, String>();
    }

    @Activate
    protected void activate(BundleContext bc) {
        if (TraceComponent.isAnyTracingEnabled() && tc.isDebugEnabled())
            Tr.debug(this, tc, "GPCL: activate called.");
        theListener = this;
        enablePlugin = (CheckpointPhase.getPhase() == CheckpointPhase.INACTIVE || Boolean.valueOf(bc.getProperty(CHECKPOINT_GEN_PLUGIN)));
    }

    @Deactivate
    protected void deactivate() {
        if (TraceComponent.isAnyTracingEnabled() && tc.isDebugEnabled())
            Tr.debug(this, tc, "GPCL: deactivate called.");
        // Cancel any pending debounced generation on shutdown.
        ScheduledFuture<?> pending = pendingGeneration.getAndSet(null);
        if (pending != null) {
            pending.cancel(false);
        }
    }

    @Reference(service = com.ibm.wsspi.webcontainer.osgi.mbeans.GeneratePluginConfig.class, cardinality = ReferenceCardinality.MANDATORY)
    protected void setGeneratePluginConfig(GeneratePluginConfig mb) {
        if (TraceComponent.isAnyTracingEnabled() && tc.isDebugEnabled())
            Tr.debug(this, tc, "GPCL: GPC set");
        gpc = mb;
    }

    protected void unsetGeneratePluginConfig(GeneratePluginConfig mb) {
        if (TraceComponent.isAnyTracingEnabled() && tc.isDebugEnabled())
            Tr.debug(this, tc, "GPCL: GPC unset");
        gpc = null;
    }

    @Reference(service = java.util.concurrent.ExecutorService.class, cardinality = ReferenceCardinality.MANDATORY)
    /** Required static reference: called before activate */
    protected void setExecutor(ExecutorService executorSrvc) {
        if (TraceComponent.isAnyTracingEnabled() && tc.isDebugEnabled())
            Tr.debug(this, tc, "GPCL: executorService set");
        this.executorSrvc = executorSrvc;
    }

    protected void unsetExecutor(ExecutorService executorSrvc) {
        this.executorSrvc = null;
    }

    @Reference(service = java.util.concurrent.ScheduledExecutorService.class, cardinality = ReferenceCardinality.MANDATORY)
    /** Required static reference: called before activate */
    protected void setScheduledExecutor(ScheduledExecutorService scheduledExec) {
        if (TraceComponent.isAnyTracingEnabled() && tc.isDebugEnabled())
            Tr.debug(this, tc, "GPCL: scheduledExecutorService set");
        this.scheduledExec = scheduledExec;
    }

    protected void unsetScheduledExecutor(ScheduledExecutorService scheduledExec) {
        this.scheduledExec = null;
    }

    @Reference(service = SessionManager.class, cardinality = ReferenceCardinality.MANDATORY)
    protected void setSessionManager(SessionManager ref) {
        if (TraceComponent.isAnyTracingEnabled() && tc.isDebugEnabled())
            Tr.debug(this, tc, "Session Manager set");
        this.smgr = ref;
    }

    /** Required static reference: will be called after deactivate. Avoid NPE */
    protected void unsetSessionManager(SessionManager ref) {
    }

    @Reference(service = WsLocationAdmin.class, cardinality = ReferenceCardinality.MANDATORY)
    protected void setLocationService(WsLocationAdmin ref) {
        locationService = ref;
    }

    protected void unsetLocationService(WsLocationAdmin ref) {
    }

    /*
     * (non-Javadoc
     *
     * @see com.ibm.ws.container.service.state.ApplicationStateListener#applicationStarting(com.ibm.ws.container.service.app.deploy.ApplicationInfo)
     */
    @Override
    public void applicationStarting(ApplicationInfo appInfo) throws StateChangeException {
        setFutureGeneratePluginTask();
        this.appsInService++;
        if (smgr != null)
            this.cookieNames.put(appInfo.getName(), smgr.getDefaultAffinityCookie());

        if (TraceComponent.isAnyTracingEnabled() && tc.isDebugEnabled())
            Tr.debug(this, tc, "application starting, add app to cookie map. app name : " + appInfo.getName() + ", cookie name : " + smgr.getDefaultAffinityCookie());
    }

    /*
     * (non-Javadoc)
     *
     * @see com.ibm.ws.container.service.state.ApplicationStateListener#applicationStarted(com.ibm.ws.container.service.app.deploy.ApplicationInfo)
     */
    @Override
    public void applicationStarted(ApplicationInfo appInfo) throws StateChangeException {
        if (TraceComponent.isAnyTracingEnabled() && tc.isDebugEnabled())
            Tr.debug(this, tc, "application started : " + appInfo.getName());

        runFutureGeneratePluginTask();
    }

    /*
     * (non-Javadoc)
     *
     * @see com.ibm.ws.container.service.state.ApplicationStateListener#applicationStopping(com.ibm.ws.container.service.app.deploy.ApplicationInfo)
     */
    @Override
    public void applicationStopping(ApplicationInfo appInfo) {
        if (TraceComponent.isAnyTracingEnabled() && tc.isDebugEnabled())
            Tr.debug(this, tc, "application stopping, remove stored cookie name : " + appInfo.getName() + ", cookie name : " + this.cookieNames.get(appInfo.getName()));

        setFutureGeneratePluginTask();
        this.cookieNames.remove(appInfo.getName());
    }

    /*
     * (non-Javadoc)
     *
     * @see com.ibm.ws.container.service.state.ApplicationStateListener#applicationStopped(com.ibm.ws.container.service.app.deploy.ApplicationInfo)
     */
    @Override
    public void applicationStopped(ApplicationInfo appInfo) {
        if (TraceComponent.isAnyTracingEnabled() && tc.isDebugEnabled())
            Tr.debug(this, tc, "application stopped : " + appInfo.getName());

        runFutureGeneratePluginTask();
        this.appsInService--;
    }

    /*
     * (non-Javadoc)
     *
     * @see com.ibm.ws.runtime.update.RuntimeUpdateListener#notificationCreated(com.ibm.ws.runtime.update.RuntimeUpdateManager, com.ibm.ws.runtime.update.RuntimeUpdateNotification)
     */
    @Override
    public void notificationCreated(RuntimeUpdateManager updateManager, RuntimeUpdateNotification notification) {
        if (TraceComponent.isAnyTracingEnabled() && tc.isDebugEnabled())
            Tr.debug(this, tc, "GPCL: RuntimeUpdate notification " + notification.getName() + ", apps in service = " + appsInService);

        // Plugin generation is now driven entirely by applicationStarted/applicationStopped
        // events via the debounced submitGeneratePluginTask(). No per-notification gating
        // is required because the debounce window coalesces all events from a single startup
        // or hot-deploy wave (including sequential app starts on initial server startup) into
        // exactly one plugin generation.
    }

    private synchronized void setFutureGeneratePluginTask() {
        this.updatesInProgress++;
        if (TraceComponent.isAnyTracingEnabled() && tc.isDebugEnabled())
            Tr.debug(this, tc, "setFutureGeneratePluginTask : future updates now = " + updatesInProgress);
    }

    private synchronized boolean unsetFutureGeneratePluginTask() {
        this.updatesInProgress--;

        // correct a bad value
        if (this.updatesInProgress < 0)
            this.updatesInProgress = 0;

        if (TraceComponent.isAnyTracingEnabled() && tc.isDebugEnabled())
            Tr.debug(this, tc, "unsetFutureGeneratePluginTask : future updates now = " + updatesInProgress);
        return updatesInProgress == 0;
    }

    private synchronized boolean isFuturePluginTask() {
        if (TraceComponent.isAnyTracingEnabled() && tc.isDebugEnabled())
            Tr.debug(this, tc, "isFuturePluginTask : updates in progress = " + updatesInProgress);
        return this.updatesInProgress == 0;
    }

    private synchronized void runFutureGeneratePluginTask() {
        if (TraceComponent.isAnyTracingEnabled() && tc.isDebugEnabled())
            Tr.debug(this, tc, "runFutureGeneratePluginTask : future updates = " + updatesInProgress);
        if (unsetFutureGeneratePluginTask()) {
            submitGeneratePluginTask();
        }
    }

    /**
     * Schedules a plugin generation task to run after {@link #DEBOUNCE_DELAY_MS} milliseconds,
     * cancelling any previously scheduled (but not yet started) task first.
     *
     * <p>This debounce ensures that a burst of application-lifecycle events — for example,
     * N applications starting sequentially during initial server startup, or a hot-deploy
     * config wave that stops and restarts multiple applications — results in exactly one
     * plugin config generation rather than one per application.
     *
     * <p>This method is called only when {@code updatesInProgress} has just reached zero,
     * meaning all currently-transitioning applications have finished their transition. If
     * another application begins transitioning within the debounce window, its
     * {@link #setFutureGeneratePluginTask()} call increments the counter and the next
     * {@link #runFutureGeneratePluginTask()} will cancel the pending task and schedule
     * a fresh one, further deferring generation until the activity fully settles.
     */
    private void submitGeneratePluginTask() {
        if (TraceComponent.isAnyTracingEnabled() && tc.isDebugEnabled())
            Tr.debug(this, tc, "submitGeneratePluginTask : FrameworkState.isStopping() = " + FrameworkState.isStopping());

        ScheduledExecutorService currentScheduledExec = scheduledExec;
        ExecutorService currentExecutor = executorSrvc;
        if (!FrameworkState.isStopping() && enablePlugin && gpc != null && currentExecutor != null && currentScheduledExec != null) {
            // Cancel any previous pending task; replace it with a fresh one.
            ScheduledFuture<?> previous = pendingGeneration.getAndSet(null);
            if (previous != null) {
                previous.cancel(false);
            }

            final ExecutorService execForTask = currentExecutor;
            Runnable generatePluginTask = new Runnable() {
                @Override
                public void run() {
                    if (TraceComponent.isAnyTracingEnabled() && tc.isDebugEnabled())
                        Tr.debug(this, tc, "generating webserver plugin");

                    // Pass the server.output.dir/logs/state/ directory to the generatePluginConfig
                    // method to ensure that plugin-cfg.xml is written to logs/state.
                    WsResource writeDirectory = locationService.getServerOutputResource("logs" + File.separatorChar + "state" + File.separatorChar);
                    ((PluginUtilityConfigGenerator) gpc).generatePluginConfig(null, writeDirectory.asFile());
                }
            };
            ScheduledFuture<?> scheduled = currentScheduledExec.schedule(
                    () -> CheckpointPhase.onRestore(() -> execForTask.submit(generatePluginTask)),
                    DEBOUNCE_DELAY_MS, TimeUnit.MILLISECONDS);

            pendingGeneration.set(scheduled);
        }
    }

    public void applicationInitialized(WebApp webApp, SessionCookieConfig sccfg) {
        String cookieName = this.cookieNames.get(webApp.getApplicationName());
        if (TraceComponent.isAnyTracingEnabled() && tc.isDebugEnabled())
            Tr.debug(this, tc, "application initialized, app : " + webApp.getApplicationName() + ", old cookie name : " + this.cookieNames.get(webApp.getApplicationName())
                               + ", new cookie name : " + sccfg == null ? null : sccfg.getName());

        if (cookieName != null && sccfg != null && !cookieName.equals(sccfg.getName())) {
            synchronized (this) {
                if (isFuturePluginTask()) {
                    submitGeneratePluginTask();
                }
            }
        }
    }

    /**
     * {@inheritDoc}
     *
     * Module events are always bracketed by applicationStarting/applicationStarted,
     * so they do not independently drive plugin generation. The application-level
     * events are sufficient to batch and trigger generation correctly.
     */
    @Override
    public void moduleStarting(ModuleInfo moduleInfo) throws StateChangeException {
        if (TraceComponent.isAnyTracingEnabled() && tc.isDebugEnabled()) {
            Tr.debug(this, tc, "module starting: " + moduleInfo.getName());
            Tr.debug(this, tc, "module starting application name: " + moduleInfo.getApplicationInfo().getName());
        }
    }

    /**
     * {@inheritDoc}
     *
     * Module events are always bracketed by applicationStarting/applicationStarted,
     * so they do not independently drive plugin generation. The application-level
     * events are sufficient to batch and trigger generation correctly.
     */
    @Override
    public void moduleStarted(ModuleInfo moduleInfo) throws StateChangeException {
        if (TraceComponent.isAnyTracingEnabled() && tc.isDebugEnabled()) {
            Tr.debug(this, tc, "module started: " + moduleInfo.getName());
            Tr.debug(this, tc, "module started application name: " + moduleInfo.getApplicationInfo().getName());
        }
    }

    /**
     * {@inheritDoc}
     *
     * Module events are always bracketed by applicationStopping/applicationStopped,
     * so they do not independently drive plugin generation. The application-level
     * events are sufficient to batch and trigger generation correctly.
     */
    @Override
    public void moduleStopping(ModuleInfo moduleInfo) {
        if (TraceComponent.isAnyTracingEnabled() && tc.isDebugEnabled()) {
            Tr.debug(this, tc, "module stopping: " + moduleInfo.getName());
            Tr.debug(this, tc, "module stopping application name: " + moduleInfo.getApplicationInfo().getName());
        }
    }

    /**
     * {@inheritDoc}
     *
     * Module events are always bracketed by applicationStopping/applicationStopped,
     * so they do not independently drive plugin generation. The application-level
     * events are sufficient to batch and trigger generation correctly.
     */
    @Override
    public void moduleStopped(ModuleInfo moduleInfo) {
        if (TraceComponent.isAnyTracingEnabled() && tc.isDebugEnabled()) {
            Tr.debug(this, tc, "module stopped: " + moduleInfo.getName());
            Tr.debug(this, tc, "module stopped application name: " + moduleInfo.getApplicationInfo().getName());
        }
    }

}
