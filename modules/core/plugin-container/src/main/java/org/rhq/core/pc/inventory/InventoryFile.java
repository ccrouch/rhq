/*
 * RHQ Management Platform
 * Copyright (C) 2005-2008 Red Hat, Inc.
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
package org.rhq.core.pc.inventory;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Map;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.rhq.core.clientapi.agent.PluginContainerException;
import org.rhq.core.clientapi.agent.metadata.PluginMetadataManager;
import org.rhq.core.domain.resource.Resource;
import org.rhq.core.domain.resource.ResourceType;
import org.rhq.core.pc.PluginContainer;

/**
 * Provides methods to read and write inventory data to a file.
 *
 * @author John Mazzitelli
 */
public class InventoryFile {
    private Log log = LogFactory.getLog(InventoryFile.class);

    private final File inventoryFile;
    private Resource platform;
    private Map<String, ResourceContainer> resourceContainers;

    /**
     * Constructor for {@link InventoryFile} that will read and write inventory data to the given file.
     *
     * @param inventoryFile the path to the inventory.dat file
     */
    public InventoryFile(File inventoryFile) {
        this.inventoryFile = inventoryFile;
    }

    public File getInventoryFile() {
        return inventoryFile;
    }

    /**
     * Returns the platform resource found in the inventory file.
     *
     * <p>This will return <code>null</code> if the file has not yet been {@link #loadInventory() loaded}, initially
     * {@link #storeInventory(Resource, Map) written to} or if an error occurred that did not allow the inventory file
     * to be fully loaded successfully.</p>
     *
     * @return platform resource in inventory file
     */
    public Resource getPlatform() {
        return platform;
    }

    /**
     * Returns the map of {@link ResourceContainer resource containers} (keyed on their UUIDs) found in the inventory
     * file.
     *
     * <p>This will return <code>null</code> if the file has not yet been {@link #loadInventory() loaded}, initially
     * {@link #storeInventory(Resource, Map) written to} or if an error occurred that did not allow the inventory file
     * to be fully loaded successfully.</p>
     *
     * @return platform resource in inventory file
     */

    public Map<String, ResourceContainer> getResourceContainers() {
        return resourceContainers;
    }

    /**
     * Reads in the inventory found in the file. Once this returns, {@link #getPlatform()} and
     * {@link #getResourceContainers()} will return non-<code>null</code> objects as found in the file.
     *
     * @throws PluginContainerException if some error occurred that did not allow this method to fully load the
     *                                  inventory
     */
    public void loadInventory() throws PluginContainerException {
        try {
            FileInputStream fis = new FileInputStream(inventoryFile);
            ObjectInputStream ois = new ObjectInputStream(fis);

            this.platform = (Resource) ois.readObject();
            connectTypes(this.platform);
            this.resourceContainers = (Map<String, ResourceContainer>) ois.readObject();
            for (ResourceContainer resourceContainer : this.resourceContainers.values()) {
                connectTypes(resourceContainer.getResource());
            }
        } catch (Exception e) {
            throw new PluginContainerException("Cannot load inventory file: " + inventoryFile, e);
        }
    }

    private void connectTypes(Resource resource) {
        PluginMetadataManager metadataManager = PluginContainer.getInstance().getPluginManager().getMetadataManager();
        ResourceType resourceType = resource.getResourceType();

        if (resourceType != null) {
            ResourceType fullResourceType = metadataManager.getType(resourceType);
            resource.setResourceType(fullResourceType);
        } else {
            log.error("Resource [" + resource + "] has a null resource type - cannot reconnect the type");
            // I guess keep going, reconnect the children's types - but we are probably no good now
        }

        Set<Resource> children = resource.getChildResources();
        if (children != null) {
            for (Resource child : children) {
                connectTypes(child);
            }
        }
    }

    /**
     * Given a platform and map of resource containers (keyed on UUID strings), this persists that inventory to the
     * {@link #getInventoryFile() inventory file}. This object's {@link #getPlatform() platform} and
     * {@link #getResourceContainers() resource containers} will be set to those passed to this method.
     *
     * @param  platformResource
     * @param  containers
     *
     * @throws IOException
     */
    public void storeInventory(Resource platformResource, Map<String, ResourceContainer> containers) throws IOException {
        FileOutputStream fos = new FileOutputStream(inventoryFile);

        try {
            ObjectOutputStream oos = new ObjectOutputStream(fos);

            oos.writeObject(platformResource);
            oos.writeObject(containers);

            this.platform = platformResource;
            this.resourceContainers = containers;
        } finally {
            if (fos != null) {
                try {
                    fos.close();
                } catch (Exception e) {
                }
            }
        }
    }
}