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

package org.rhq.plugins.snmptrapd;

import java.util.HashSet;
import java.util.Set;

import org.rhq.core.domain.configuration.Configuration;
import org.rhq.core.domain.resource.ResourceType;
import org.rhq.core.pluginapi.inventory.DiscoveredResourceDetails;
import org.rhq.core.pluginapi.inventory.InvalidPluginConfigurationException;
import org.rhq.core.pluginapi.inventory.ResourceDiscoveryComponent;
import org.rhq.core.pluginapi.inventory.ResourceDiscoveryContext;

/**
 * Discovery component for an SNMP trapd 
 * @author Heiko W. Rupp
 *
 */
public class SnmpTrapdDiscovery implements ResourceDiscoveryComponent<SnmpTrapdComponent> {

    /* (non-Javadoc)
     * @see org.rhq.core.pluginapi.inventory.ResourceDiscoveryComponent#discoverResources(org.rhq.core.pluginapi.inventory.ResourceDiscoveryContext)
     */
    public Set<DiscoveredResourceDetails> discoverResources(ResourceDiscoveryContext context)
        throws InvalidPluginConfigurationException, Exception {

        Set<DiscoveredResourceDetails> results = new HashSet<DiscoveredResourceDetails>();

        String pcName = context.getPluginContainerName();
        String key = "Trapd (" + pcName + ")";
        String name = key;
        String description = "SNMP Trap receiver on " + pcName;
        Configuration configuration = context.getDefaultPluginConfiguration();
        ResourceType resourceType = context.getResourceType();
        DiscoveredResourceDetails detail = new DiscoveredResourceDetails(resourceType, key, name, null, description,
            configuration, null);

        results.add(detail);

        return results;
    }

}