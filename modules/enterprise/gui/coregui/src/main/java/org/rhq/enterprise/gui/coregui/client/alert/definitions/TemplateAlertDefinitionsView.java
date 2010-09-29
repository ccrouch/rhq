/*
 * RHQ Management Platform
 * Copyright (C) 2005-2010 Red Hat, Inc.
 * All rights reserved.
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License, version 2, as
 * published by the Free Software Foundation, and/or the GNU Lesser
 * General Public License, version 2.1, also as published by the Free
 * Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License and the GNU Lesser General Public License
 * for more details.
 *
 * You should have received a copy of the GNU General Public License
 * and the GNU Lesser General Public License along with this program;
 * if not, write to the Free Software Foundation, Inc.,
 * 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 */

package org.rhq.enterprise.gui.coregui.client.alert.definitions;

import java.util.EnumSet;

import com.smartgwt.client.data.Criteria;
import com.smartgwt.client.util.SC;
import com.smartgwt.client.widgets.grid.ListGridRecord;

import org.rhq.core.domain.alert.AlertDefinition;
import org.rhq.core.domain.resource.ResourceType;
import org.rhq.enterprise.gui.coregui.client.inventory.resource.type.ResourceTypeRepository;
import org.rhq.enterprise.gui.coregui.client.inventory.resource.type.ResourceTypeRepository.MetadataType;

/**
 * @author John Mazzitelli
 */
public class TemplateAlertDefinitionsView extends AbstractAlertDefinitionsView {

    public static final String CRITERIA_RESOURCE_TYPE_ID = "resourceTypeId";

    private ResourceType resourceType;

    public TemplateAlertDefinitionsView(String locatorId, ResourceType resourceType) {
        super(locatorId, "Alert Templates");
        this.resourceType = resourceType;

        // make sure we loaded all the type info we'll need. if one of these is null, either the type
        // doesn't have it or we haven't loaded it yet. since we can't know for sure if it was loaded, we have to ask.
        EnumSet<MetadataType> metadata = EnumSet.noneOf(MetadataType.class);
        if (resourceType.getEventDefinitions() == null)
            metadata.add(MetadataType.events);
        if (resourceType.getMetricDefinitions() == null)
            metadata.add(MetadataType.measurements);
        if (resourceType.getOperationDefinitions() == null)
            metadata.add(MetadataType.operations);
        if (resourceType.getResourceConfigurationDefinition() == null)
            metadata.add(MetadataType.resourceConfigurationDefinition);
        if (!metadata.isEmpty()) {
            ResourceTypeRepository.Cache.getInstance().getResourceTypes(resourceType.getId(), metadata,
                new ResourceTypeRepository.TypeLoadedCallback() {
                    @Override
                    public void onTypesLoaded(ResourceType type) {
                        TemplateAlertDefinitionsView.this.resourceType = type;
                    }
                });
        }
    }

    @Override
    protected ResourceType getResourceType() {
        return resourceType;
    }

    @Override
    protected Criteria getCriteria() {
        Criteria criteria = new Criteria();
        criteria.addCriteria(CRITERIA_RESOURCE_TYPE_ID, resourceType.getId());
        return criteria;
    }

    @Override
    protected AbstractAlertDefinitionsDataSource getAlertDefinitionDataSource() {
        return new TemplateAlertDefinitionsDataSource();
    }

    @Override
    protected boolean isAllowedToModifyAlertDefinitions() {
        // TODO: see if user can modify template alerts
        return true;
    }

    @Override
    protected void newButtonPressed(ListGridRecord[] selection) {
        // TODO Auto-generated method stub
        String str = "this is not implemented yet but you selected";
        for (ListGridRecord record : selection) {
            str += ": " + record.getAttribute("name");
        }
        SC.say(str);
    }

    @Override
    protected void enableButtonPressed(ListGridRecord[] selection) {
        // TODO Auto-generated method stub
        String str = "this is not implemented yet but you selected";
        for (ListGridRecord record : selection) {
            str += ": " + record.getAttribute("name");
        }
        SC.say(str);
    }

    @Override
    protected void disableButtonPressed(ListGridRecord[] selection) {
        // TODO Auto-generated method stub
        String str = "this is not implemented yet but you selected";
        for (ListGridRecord record : selection) {
            str += ": " + record.getAttribute("name");
        }
        SC.say(str);
    }

    @Override
    protected void deleteButtonPressed(ListGridRecord[] selection) {
        // TODO Auto-generated method stub
        String str = "this is not implemented yet but you selected";
        for (ListGridRecord record : selection) {
            str += ": " + record.getAttribute("name");
        }
        SC.say(str);
    }

    @Override
    protected void commitAlertDefinition(AlertDefinition alertDefinition) {
        // TODO call into server SLSB to store alert def
        //    AlertTemplateManagerLocal alertTemplateManager = LookupUtil.getAlertTemplateManager();
        //    alertTemplateManager.updateAlertTemplate(subject, alertDef, true);
    }
}