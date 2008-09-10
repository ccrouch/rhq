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
package org.rhq.enterprise.server.cluster;

import java.util.List;

import javax.ejb.Local;

import org.rhq.core.domain.auth.Subject;
import org.rhq.core.domain.cluster.FailoverListDetails;
import org.rhq.core.domain.cluster.Server;
import org.rhq.core.domain.cluster.composite.ServerWithAgentCountComposite;
import org.rhq.core.domain.resource.Agent;
import org.rhq.core.domain.util.PageControl;
import org.rhq.core.domain.util.PageList;

/**
 * @author Joseph Marques
 */

@Local
public interface ClusterManagerLocal {

    List<Agent> getAgentsByServerName(String serverName);

    Server getServerById(int serverId);

    Server getServerByName(String serverName);

    List<Server> getServersByOperationMode(Server.OperationMode mode);

    List<Server> getAllServers();

    PageList<ServerWithAgentCountComposite> getServerComposites(Subject subject, PageControl pc);

    int getServerCount();

    void updateServerMode(Integer[] serverIds, Server.OperationMode mode);

    Server updateServer(Subject subject, Server server);

    PageList<FailoverListDetails> getFailoverListDetailsByAgentId(int agentId, PageControl pc);
}
