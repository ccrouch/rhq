/*
 * RHQ Management Platform
 * Copyright (C) 2005-2008 Red Hat, Inc.
 * All rights reserved.
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation version 2 of the License.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
 */
package org.rhq.enterprise.gui.discovery;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.faces.application.FacesMessage;
import javax.faces.model.DataModel;
import javax.faces.model.SelectItem;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.rhq.core.domain.auth.Subject;
import org.rhq.core.domain.resource.InventoryStatus;
import org.rhq.core.domain.resource.Resource;
import org.rhq.core.domain.util.PageControl;
import org.rhq.core.domain.util.PageList;
import org.rhq.core.gui.util.FacesContextUtility;
import org.rhq.enterprise.gui.common.framework.PagedDataTableUIBean;
import org.rhq.enterprise.gui.common.paging.PageControlView;
import org.rhq.enterprise.gui.common.paging.PagedListDataModel;
import org.rhq.enterprise.gui.util.EnterpriseFacesContextUtility;
import org.rhq.enterprise.server.discovery.DiscoveryBossLocal;
import org.rhq.enterprise.server.util.LookupUtil;

public class AutoDiscoveryQueueUIBean extends PagedDataTableUIBean {
    private final Log log = LogFactory.getLog(AutoDiscoveryQueueUIBean.class);

    public static final String MANAGED_BEAN_NAME = "AutoDiscoveryQueueUIBean";

    private DiscoveryBossLocal discoveryBoss = LookupUtil.getDiscoveryBoss();
    private Map<Resource, List<Resource>> platformsAndServers = new HashMap<Resource, List<Resource>>();

    public AutoDiscoveryQueueUIBean() {
    }

    @Override
    public DataModel getDataModel() {
        if (dataModel == null) {
            dataModel = new AutoDiscoveryQueueDataModel(PageControlView.AutoDiscoveryPlatformList, MANAGED_BEAN_NAME);
        }

        return dataModel;
    }

    @Override
    public SelectItem[] getPageSizes() {
        return new SelectItem[] { new SelectItem("10", "10"), new SelectItem("25", "25"), new SelectItem("50", "50"),
            new SelectItem("75", "75"), new SelectItem("100", "100") };
    }

    @Override
    public int getMinimumPageSize() {
        return 10;
    }

    public Map<Resource, List<Resource>> getPlatformsAndServers() {
        return platformsAndServers;
    }

    public String rebuildTable() {
        // this method is called when the table needs to be rebuilt - clear out our session to start anew
        getExpandedPlatforms().clear();
        getSelectedResources().clear();
        return "sort";
    }

    public String importResources() {
        Subject subject = EnterpriseFacesContextUtility.getSubject();
        int platformCount = 0;
        int serverCount = 0;

        try {
            Map<Integer, Boolean> selectedResources = getSelectedResources();

            // do it one platform at a time so we give each its own transaction
            List<Resource> platform = new ArrayList<Resource>(1);

            for (Map.Entry<Integer, Boolean> selected : selectedResources.entrySet()) {
                // if current 'selected' is a platform, import the selected things for that platform
                Resource resource = findResource(selected.getKey());
                if ((resource != null) && selected.getValue().booleanValue()
                    && platformsAndServers.containsKey(resource)) {
                    platform.clear();
                    platform.add(resource);
                    if (resource.getInventoryStatus() == InventoryStatus.NEW) {
                        platformCount++;
                    }

                    List<Resource> allServers = platformsAndServers.get(resource);
                    List<Resource> servers = new ArrayList<Resource>();
                    for (Resource server : allServers) {
                        if (selectedResources.containsKey(server.getId())
                            && selectedResources.get(server.getId()).booleanValue()
                            && (server.getInventoryStatus() == InventoryStatus.NEW)) {
                            serverCount++;
                            servers.add(server);
                        }
                    }

                    log.debug("AIQueue import: platform=" + platform + "| servers=" + servers);
                    // TODO: Why do we update the platform's status even when it was already COMMITTED?? (ips, 07/10/08)
                    discoveryBoss.updateInventoryStatus(subject, platform, servers, InventoryStatus.COMMITTED);
                }
            }

            FacesContextUtility.addMessage(FacesMessage.SEVERITY_INFO, "Imported [" + platformCount
                + "] platforms and [" + serverCount + "] servers.");

            getSelectedResources().clear();
        } catch (Exception e) {
            FacesContextUtility.addMessage(FacesMessage.SEVERITY_ERROR, "Import failed.", e);
            log.error("Import failed", e);
        }

        return "success";
    }

    public String ignoreResources() {
        Subject subject = EnterpriseFacesContextUtility.getSubject();
        int serverCount = 0;

        try {
            // do it one platform at a time so we give each its own transaction
            List<Resource> platform = new ArrayList<Resource>(1);

            Map<Integer, Boolean> selectedResources = getSelectedResources();
            for (Map.Entry<Integer, Boolean> selected : selectedResources.entrySet()) {
                // if current 'selected' is a platform, ignore the selected things for that platform
                Resource resource = findResource(selected.getKey());
                if ((resource != null) && selected.getValue().booleanValue()
                    && platformsAndServers.containsKey(resource)) {
                    List<Resource> allServers = platformsAndServers.get(resource);
                    List<Resource> servers = new ArrayList<Resource>();
                    for (Resource server : allServers) {
                        if (selectedResources.containsKey(server.getId())
                            && selectedResources.get(server.getId()).booleanValue()
                            && (server.getInventoryStatus() == InventoryStatus.NEW)) {
                            serverCount++;
                            servers.add(server);
                        }
                    }

                    log.debug("AIQueue ignore: platform=" + platform + "| servers=" + servers);
                    discoveryBoss.updateInventoryStatus(subject, platform, servers, InventoryStatus.IGNORED);
                }
            }

            FacesContextUtility.addMessage(FacesMessage.SEVERITY_INFO, "Ignored [" + serverCount + "] servers.");

            getSelectedResources().clear();
        } catch (Exception e) {
            FacesContextUtility.addMessage(FacesMessage.SEVERITY_ERROR, "Ignore failed.", e);
            log.error("Ignore failed", e);
        }

        return "success";
    }

    public String unignoreResources() {
        Subject subject = EnterpriseFacesContextUtility.getSubject();
        int serverCount = 0;

        try {
            // do it one platform at a time so we give each its own transaction
            List<Resource> platform = new ArrayList<Resource>(1);

            Map<Integer, Boolean> selectedResources = getSelectedResources();
            for (Map.Entry<Integer, Boolean> selected : selectedResources.entrySet()) {
                // if current 'selected' is a platform, unignore its servers; otherwise, just go on to the next
                Resource resource = findResource(selected.getKey());
                if ((resource != null) && selected.getValue().booleanValue()
                    && platformsAndServers.containsKey(resource)) {
                    if (resource.getInventoryStatus() != InventoryStatus.COMMITTED) {
                        FacesContextUtility.addMessage(FacesMessage.SEVERITY_WARN,
                            "Cannot un-ignore servers from an uncommitted platform [" + resource.getName()
                                + "]. Aborting.");

                        break;
                    }

                    List<Resource> allServers = platformsAndServers.get(resource);
                    List<Resource> servers = new ArrayList<Resource>();
                    for (Resource server : allServers) {
                        if (selectedResources.containsKey(server.getId())
                            && selectedResources.get(server.getId()).booleanValue()
                            && (server.getInventoryStatus() == InventoryStatus.IGNORED)) {
                            serverCount++;
                            servers.add(server);
                        }
                    }

                    log.debug("AIQueue unignore: platform=" + platform + "| servers=" + servers);
                    discoveryBoss.updateInventoryStatus(subject, platform, servers, InventoryStatus.NEW);
                }
            }

            FacesContextUtility.addMessage(FacesMessage.SEVERITY_INFO, "Un-ignored [" + serverCount + "] servers.");

            getSelectedResources().clear();
        } catch (Exception e) {
            FacesContextUtility.addMessage(FacesMessage.SEVERITY_ERROR, "Un-ignore failed.", e);
            log.error("Unignore failed", e);
        }

        return "success";
    }

    private Map<Integer, Boolean> getSelectedResources() {
        return FacesContextUtility.getManagedBean(AutoDiscoverySessionUIBean.class).getSelectedResources();
    }

    private Map<Integer, Boolean> getExpandedPlatforms() {
        return FacesContextUtility.getManagedBean(AutoDiscoverySessionUIBean.class).getExpandedMap();
    }

    private Resource findResource(Integer id) {
        for (Map.Entry<Resource, List<Resource>> entry : platformsAndServers.entrySet()) {
            Resource platform = entry.getKey();
            List<Resource> servers = entry.getValue();

            if (platform.getId() == id.intValue()) {
                return platform;
            }

            for (Resource server : servers) {
                if (server.getId() == id.intValue()) {
                    return server;
                }
            }
        }

        return null;
    }

    private EnumSet<InventoryStatus> getShowNewIgnoreEnumSet() {
        AutoDiscoverySessionUIBean bean = FacesContextUtility.getManagedBean(AutoDiscoverySessionUIBean.class);
        String showNewIgnore = bean.getShowNewIgnore();

        if ("BOTH".equals(showNewIgnore)) {
            return EnumSet.of(InventoryStatus.NEW, InventoryStatus.IGNORED);
        } else if (InventoryStatus.NEW.name().equals(showNewIgnore)) {
            return EnumSet.of(InventoryStatus.NEW);
        } else if (InventoryStatus.IGNORED.name().equals(showNewIgnore)) {
            return EnumSet.of(InventoryStatus.IGNORED);
        }

        return EnumSet.of(InventoryStatus.NEW);
    }

    private class AutoDiscoveryQueueDataModel extends PagedListDataModel<Resource> {
        public AutoDiscoveryQueueDataModel(PageControlView view, String beanName) {
            super(view, beanName);
        }

        @Override
        public PageList<Resource> fetchPage(PageControl pc) {
            Subject subject = EnterpriseFacesContextUtility.getSubject();
            EnumSet<InventoryStatus> newIgnoredSet = getShowNewIgnoreEnumSet();

            platformsAndServers.clear();

            PageList<Resource> queuedPlatforms = discoveryBoss.getQueuedPlatforms(subject, newIgnoredSet, pc);

            for (Resource platform : queuedPlatforms) {
                List<Resource> queuedServers = new ArrayList<Resource>();
                for (InventoryStatus status : newIgnoredSet) {
                    queuedServers.addAll(discoveryBoss.getQueuedPlatformChildServers(subject, status, platform));
                }

                platformsAndServers.put(platform, queuedServers);
            }

            // In order to be able to expandAll/collapseAll, the expandedPlatforms map must have all platforms in it.
            // Prepare the expandedPlatforms, maintaining prior state for platforms that were already in the map.
            Map<Integer, Boolean> expandedPlatforms = getExpandedPlatforms();
            Map<Integer, Boolean> newExpandedPlatforms = new HashMap<Integer, Boolean>();
            for (Resource platform : platformsAndServers.keySet()) {
                Boolean expanded = expandedPlatforms.get(platform.getId());
                newExpandedPlatforms.put(platform.getId(), (expanded != null) ? expanded : Boolean.FALSE);
            }
            expandedPlatforms.clear();
            expandedPlatforms.putAll(newExpandedPlatforms);

            return queuedPlatforms;
        }
    }
}