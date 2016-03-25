/**
 * Copyright (c) 2014-2016, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.wso2.carbon.bpmn.core.internal;


import org.activiti.engine.ProcessEngine;
import org.activiti.engine.ProcessEngines;
import org.apache.tomcat.jdbc.pool.DataSource;
import org.osgi.framework.BundleContext;
import org.osgi.service.component.ComponentContext;

import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.component.annotations.ReferencePolicy;

import org.osgi.service.jndi.JNDIContextManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.wso2.carbon.bpmn.core.ActivitiEngineBuilder;
import org.wso2.carbon.bpmn.core.BPMNEngineService;
import org.wso2.carbon.bpmn.core.BPMNServerHolder;
import org.wso2.carbon.bpmn.core.BPSFault;
import org.wso2.carbon.bpmn.core.db.DataSourceHandler;
import org.wso2.carbon.bpmn.core.exception.BPMNMetaDataTableCreationException;
import org.wso2.carbon.bpmn.core.exception.DatabaseConfigurationException;
import org.wso2.carbon.datasource.core.api.DataSourceManagementService;
import org.wso2.carbon.datasource.core.api.DataSourceService;
import org.wso2.carbon.datasource.core.beans.DataSourceMetadata;
import org.wso2.carbon.datasource.core.beans.JNDIConfig;
import org.wso2.carbon.datasource.core.exception.DataSourceException;
import org.wso2.carbon.kernel.startupresolver.RequiredCapabilityListener;

import javax.naming.Context;
import javax.naming.NamingException;

/**
 *
 */

@Component(
        name = "org.wso2.carbon.bpmn.core.internal.BPMNServiceComponent",
        immediate = true,
        service = RequiredCapabilityListener.class,
        property = {
                "capability-name=org.wso2.carbon.datasource.core.api.DataSourceService," +
                        "org.wso2.carbon.datasource.jndi.JNDIContextManager",
                "component-key=carbon-bpmn-service"
        }
)
public class BPMNServiceComponent implements RequiredCapabilityListener {

    private static final Logger log = LoggerFactory.getLogger(BPMNServiceComponent.class);
    private DataSourceService datasourceService;
    private DataSourceManagementService datasourceManagementService;
    private JNDIContextManager jndiContextManager;
    private BundleContext bundleContext;

    @Reference(
            name = "org.wso2.carbon.datasource.jndi.JNDIContextManager",
            service = JNDIContextManager.class,
            cardinality = ReferenceCardinality.MANDATORY,
            policy = ReferencePolicy.DYNAMIC,
            unbind = "unRegisterJNDIContext"
    )

    public void registerJNDIContext(JNDIContextManager contextManager) {
        log.info("register JNDI Context");
        this.jndiContextManager = contextManager;
    }

    public void unRegisterJNDIContext(JNDIContextManager contextManager) {
        log.info("Unregister JNDI Context");
    }

    @Reference(
            name = "org.wso2.carbon.datasource.core.api.DataSourceService",
            service = DataSourceService.class,
            cardinality = ReferenceCardinality.MANDATORY,
            policy = ReferencePolicy.DYNAMIC,
            unbind = "unRegisterDataSourceService"
    )
    public void registerDataSourceService(DataSourceService datasource) {
        log.info("register Datasource service");
        this.datasourceService = datasource;
    }

    public void unRegisterDataSourceService(DataSourceService datasource) {
        log.info("unregister datasource service");
    }

    @Reference(
            name = "org.wso2.carbon.datasource.core.api.DataSourceManagementService",
            service = DataSourceManagementService.class,
            cardinality = ReferenceCardinality.MANDATORY,
            policy = ReferencePolicy.DYNAMIC,
            unbind = "unRegisterDataSourceManagementService"
    )

    public void registerDataSourceManagementService(DataSourceManagementService datasourceMgtService) {
        log.info("register Datasource Management service");
        this.datasourceManagementService = datasourceMgtService;
    }

    public void unRegisterDataSourceManagementService(DataSourceManagementService datasource) {
        log.info("unregister datasource service");
    }

    @Activate
    protected void activate(ComponentContext ctxt) {
        log.info("BPMN core component activator...");

        try {
            this.bundleContext = ctxt.getBundleContext();


        } catch (Throwable e) {
            log.error("Failed to initialize the BPMN core component.", e);
        }
    }

    @Deactivate
    protected void deactivate(ComponentContext ctxt) {
        log.info("Stopping the BPMN core component...");
        ProcessEngines.destroy();
    }

    @Override
    public void onAllRequiredCapabilitiesAvailable() {
        log.info("OnAllRequiredCapabilitiesAvailable -> Initialzing the bpmn engine  ....");
        try {

            registerJNDIContextForActiviti();
            BPMNServerHolder holder = BPMNServerHolder.getInstance();
            ActivitiEngineBuilder activitiEngineBuilder = new ActivitiEngineBuilder();
            holder.setEngine(activitiEngineBuilder.buildEngine());
            BPMNEngineServiceImpl bpmnEngineService = new BPMNEngineServiceImpl();
            bpmnEngineService.setProcessEngine(activitiEngineBuilder.getProcessEngine());
            bundleContext.registerService(BPMNEngineService.class.getName(), bpmnEngineService, null);


        } catch (DataSourceException | BPSFault | NamingException e ) {
                log.error("Error on initalizing the bpmn component " + e.getMessage() + " " + e);
        }

    }

    private void registerJNDIContextForActiviti() throws DataSourceException, NamingException {
        DataSourceMetadata activiti_db = datasourceManagementService.getDataSource("ACTIVITI_DB");
        JNDIConfig jndiConfig = activiti_db.getJndiConfig();
        Context context = jndiContextManager.newInitialContext();
        Context subcontext = context.createSubcontext("java:comp/jdbc");
        subcontext.bind("ActivitiDB", datasourceService.getDataSource("ACTIVITI_DB"));
    }

}

