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
package org.rhq.core.domain.resource;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EntityResult;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.JoinTable;
import javax.persistence.ManyToMany;
import javax.persistence.ManyToOne;
import javax.persistence.NamedNativeQueries;
import javax.persistence.NamedNativeQuery;
import javax.persistence.NamedQueries;
import javax.persistence.NamedQuery;
import javax.persistence.OneToMany;
import javax.persistence.OrderBy;
import javax.persistence.PrePersist;
import javax.persistence.PreUpdate;
import javax.persistence.QueryHint;
import javax.persistence.SequenceGenerator;
import javax.persistence.SqlResultSetMapping;
import javax.persistence.Table;
import javax.persistence.Transient;
import javax.xml.bind.annotation.XmlTransient;

import org.rhq.core.domain.configuration.definition.ConfigurationDefinition;
import org.rhq.core.domain.content.PackageType;
import org.rhq.core.domain.event.EventDefinition;
import org.rhq.core.domain.measurement.MeasurementDefinition;
import org.rhq.core.domain.operation.OperationDefinition;
import org.rhq.core.domain.util.EntitySerializer;
import org.rhq.core.domain.util.Summary;
import org.rhq.core.domain.util.serial.ExternalizableStrategy;

/**
 * Defines a type of {@link Resource} (e.g. a Linux platform, a JBossAS server, or a Datasource service).
 * Unique business key (and therefore equals/hashCode basis) is the (String name, String plugin) combination.
 * This will keep plugin writers from stepping on each other's toes.
 *
 * @author Greg Hinkle
 * @author Ian Springer
 */
@Entity
@Table(name = "RHQ_RESOURCE_TYPE")
@SequenceGenerator(name = "SEQ", sequenceName = "RHQ_RESOURCE_TYPE_ID_SEQ")
@NamedQueries( {
    @NamedQuery(name = ResourceType.QUERY_FIND_BY_NAME, // TODO: QUERY: This breaks rules, names may not be unique between plugins
    query = "SELECT rt FROM ResourceType AS rt WHERE LOWER(rt.name) = LOWER(:name)"),
    @NamedQuery(name = ResourceType.QUERY_FIND_BY_PLUGIN, query = "SELECT rt FROM ResourceType AS rt WHERE rt.plugin = :plugin"),
    @NamedQuery(name = ResourceType.QUERY_FIND_BY_NAME_AND_PLUGIN, // TODO: QUERY: names are case-sensitive
    hints = { @QueryHint(name = "org.hibernate.cacheable", value = "true"),
        @QueryHint(name = "org.hibernate.cacheRegion", value = "metadata") }, query = "SELECT rt FROM ResourceType AS rt WHERE LOWER(rt.name) = LOWER(:name) AND rt.plugin = :plugin"),
    @NamedQuery(name = ResourceType.QUERY_FIND_ALL, query = "SELECT rt FROM ResourceType AS rt"),
    @NamedQuery(name = ResourceType.QUERY_FIND_BY_PARENT_AND_NAME, // TODO: QUERY: Not looking up by the full key, get rid of this query
    query = "SELECT rt FROM ResourceType AS rt WHERE :parent MEMBER OF rt.parentResourceTypes AND rt.name = :name"),

    /* authz'ed queries for ResourceTypeManagerBean */
    @NamedQuery(name = ResourceType.QUERY_FIND_CHILDREN, query = "SELECT res.resourceType "
        + "FROM Resource res, IN (res.implicitGroups) g, IN (g.roles) r, IN (r.subjects) s " + "WHERE s = :subject "
        + "AND res.parentResource = :parent"),
    @NamedQuery(name = ResourceType.QUERY_FIND_CHILDREN_admin, query = "SELECT res.resourceType "
        + "FROM Resource res " + "WHERE res.parentResource = :parent"),
    @NamedQuery(name = ResourceType.FIND_CHILDREN_BY_PARENT, query = "SELECT DISTINCT rt FROM ResourceType AS rt "
        + "JOIN FETCH rt.parentResourceTypes AS pa " + // also fetch parents, as we need them later
        "WHERE pa IN (:resourceType)"),
    @NamedQuery(name = ResourceType.FIND_ALL_TEMPLATE_COUNT_COMPOSITES, query = "" //
        + "SELECT new org.rhq.core.domain.resource.composite.ResourceTypeTemplateCountComposite" //
        + "(" //
        + "  rt," //
        + "  (SELECT COUNT(md) FROM MeasurementDefinition AS md WHERE md.resourceType = rt AND md.defaultOn = TRUE), "//
        + "  (SELECT COUNT(md) FROM MeasurementDefinition AS md WHERE md.resourceType = rt AND md.defaultOn = FALSE), "//
        + "  (SELECT COUNT(ad) FROM AlertDefinition AS ad WHERE ad.resourceType = rt AND ad.deleted = FALSE AND ad.enabled = TRUE), "//
        + "  (SELECT COUNT(ad) FROM AlertDefinition AS ad WHERE ad.resourceType = rt AND ad.deleted = FALSE AND ad.enabled = FALSE) "//
        + ")" //
        + "FROM ResourceType AS rt"),
    @NamedQuery(name = ResourceType.QUERY_FIND_BY_CATEGORY, query = "SELECT rt FROM ResourceType AS rt "
        + "WHERE rt.category = :category"),
    @NamedQuery(name = ResourceType.QUERY_FIND_UTILIZED_BY_CATEGORY, query = "SELECT DISTINCT res.resourceType "
        + "FROM Resource res, IN (res.implicitGroups) g, IN (g.roles) r, IN (r.subjects) s " //
        + "WHERE s = :subject " //
        + "AND res.resourceType.category = :category "
        + "AND (UPPER(res.name) LIKE :nameFilter OR :nameFilter is null) "
        + "AND (res.resourceType.plugin = :pluginName OR :pluginName is null) "
        + "AND (:inventoryStatus = res.inventoryStatus OR :inventoryStatus is null) "
        + "ORDER BY res.resourceType.name "),
    @NamedQuery(name = ResourceType.QUERY_FIND_UTILIZED_BY_CATEGORY_admin, query = "SELECT DISTINCT res.resourceType "
        + "FROM Resource res " //
        + "WHERE res.resourceType.category = :category "
        + "AND (UPPER(res.name) LIKE :nameFilter OR :nameFilter is null) "
        + "AND (res.resourceType.plugin = :pluginName OR :pluginName is null) "
        + "AND (:inventoryStatus = res.inventoryStatus OR :inventoryStatus is null) "
        + "ORDER BY res.resourceType.name "

    ),
    @NamedQuery(name = ResourceType.QUERY_FIND_UTILIZED_CHILDREN_BY_CATEGORY, query = "SELECT DISTINCT res.resourceType "
        + "FROM Resource res, IN (res.implicitGroups) g, IN (g.roles) r, IN (r.subjects) s "
        + "WHERE s = :subject "
        + "AND res.parentResource = :parentResource "
        + "AND res.resourceType.category = :category "
        + "AND (:inventoryStatus = res.inventoryStatus OR :inventoryStatus is null) "),
    @NamedQuery(name = ResourceType.QUERY_FIND_UTILIZED_CHILDREN_BY_CATEGORY_admin, query = "SELECT DISTINCT res.resourceType "
        + "FROM Resource res "
        + "WHERE res.parentResource = :parentResource "
        + "AND res.resourceType.category = :category "
        + "AND (:inventoryStatus = res.inventoryStatus OR :inventoryStatus is null) "),
    @NamedQuery(name = ResourceType.QUERY_FIND_BY_RESOURCE_GROUP, query = "" //
        + "SELECT DISTINCT rt " //
        + "  FROM ResourceGroup rg " //
        + "  JOIN rg.resourceType rt" //
        + "  JOIN rg.roles r JOIN r.subjects s " //
        + " WHERE s = :subject " //
        + "   AND ( rt.plugin = :pluginName OR :pluginName is null ) "),
    @NamedQuery(name = ResourceType.QUERY_FIND_BY_RESOURCE_GROUP_admin, query = "" //
        + "SELECT DISTINCT rt " //
        + "  FROM ResourceGroup rg " //
        + "  JOIN rg.resourceType rt" //
        + " WHERE ( rt.plugin = :pluginName OR :pluginName is null ) "),
    @NamedQuery(name = ResourceType.QUERY_GET_EXPLICIT_RESOURCE_TYPE_COUNTS_BY_GROUP, query = "SELECT type.id, type.name, COUNT(type.id) "
        + "FROM ResourceGroup rg JOIN rg.explicitResources res JOIN res.resourceType type "
        + "WHERE rg.id = :groupId "
        + "GROUP BY type.id, type.name "),
    @NamedQuery(name = ResourceType.QUERY_GET_IMPLICIT_RESOURCE_TYPE_COUNTS_BY_GROUP, query = "SELECT type.id, type.name, COUNT(type.id) "
        + "FROM ResourceGroup rg JOIN rg.implicitResources res JOIN res.resourceType type "
        + "WHERE rg.id = :groupId "
        + "GROUP BY type.id, type.name "),
    @NamedQuery(name = ResourceType.QUERY_FIND_BY_SUBCATEGORY, query = "SELECT rt " + "FROM ResourceType rt "
        + "WHERE rt.subCategory = :subCategory"),
    @NamedQuery(name = ResourceType.QUERY_FIND_BY_ID_WITH_ALL_OPERATIONS, query = "SELECT DISTINCT rt "
        + "FROM ResourceType rt " + "LEFT JOIN FETCH rt.operationDefinitions def "
        + "LEFT JOIN FETCH def.parametersConfigurationDefinition "
        + "LEFT JOIN FETCH def.resultsConfigurationDefinition " + "WHERE rt.id = :id"),
    @NamedQuery(name = ResourceType.QUERY_FIND_RESOURCE_FACETS, query = "" //
        + "SELECT new org.rhq.core.domain.resource.composite.ResourceFacets " //
        + "       ( " //
        + "         rt.id," // the resourceTypeId
        + "         (SELECT COUNT(metricDef) FROM rt.metricDefinitions metricDef)," // measurement
        + "         (SELECT COUNT(eventDef) FROM rt.eventDefinitions eventDef)," // event
        + "         (SELECT COUNT(pluginConfig) FROM rt.pluginConfigurationDefinition pluginConfig)," // pluginConfiguration
        + "         (SELECT COUNT(resConfig) FROM rt.resourceConfigurationDefinition resConfig)," // configuration
        + "         (SELECT COUNT(operationDef) FROM rt.operationDefinitions operationDef)," // operation
        + "         (SELECT COUNT(packageType) FROM rt.packageTypes packageType)," // content
        + "         (SELECT COUNT(metricDef) FROM rt.metricDefinitions metricDef WHERE metricDef.dataType = 3)" // calltime
        + "       ) " //
        + "  FROM ResourceType rt " //
        + " WHERE ( rt.id = :resourceTypeId OR :resourceTypeId IS NULL )"),
    @NamedQuery(name = ResourceType.QUERY_FIND_DUPLICATE_TYPE_NAMES, query = "" //
        + "  SELECT rt.name " //
        + "    FROM ResourceType rt " //
        + "GROUP BY rt.name " //
        + "  HAVING COUNT(rt.name) > 1") })
@NamedNativeQueries( {
    // TODO: Add authz conditions to the below query.
    @NamedNativeQuery(name = ResourceType.QUERY_FIND_CHILDREN_BY_CATEGORY, query = "" //
        + "(SELECT crt.id, crt.name, crt.category, crt.creation_data_type, crt.create_delete_policy, crt.singleton, crt.supports_manual_add, crt.description, crt.plugin, crt.ctime, crt.mtime, crt.subcategory_id, crt.plugin_config_def_id, crt.res_config_def_id "
        + "FROM RHQ_resource_type crt, RHQ_resource res, RHQ_resource_type rt, RHQ_resource_type_parents rtp "
        + "WHERE res.id = ? "
        + "AND res.resource_type_id = rt.id "
        + "AND rt.id = rtp.parent_resource_type_id "
        + "AND rtp.resource_type_id = crt.id "
        + "AND crt.category = ? "
        +
        //               "ORDER BY crt.name " +
        "UNION "
        + "SELECT DISTINCT crt2.id, crt2.name, crt2.category, crt2.creation_data_type, crt2.create_delete_policy, crt2.singleton, crt2.supports_manual_add, crt2.description, crt2.plugin, crt2.ctime, crt2.mtime, crt2.subcategory_id, crt2.plugin_config_def_id, crt2.res_config_def_id "
        + "FROM RHQ_resource_type crt2 " + "WHERE 1 = "
        + "(SELECT COUNT(res2.id) "
        + "FROM RHQ_resource res2, RHQ_resource_type rt2 "
        + "WHERE res2.id = ? "
        + "AND res2.resource_type_id = rt2.id " + "AND rt2.category = 'PLATFORM') "
        + "AND 0 = "
        + "(SELECT COUNT(rtp2.resource_type_id) "
        + "FROM RHQ_resource_type_parents rtp2 "
        + "WHERE rtp2.resource_type_id = crt2.id) " + "AND crt2.category = ? " + " ) ORDER BY name", resultSetMapping = ResourceType.MAPPING_FIND_CHILDREN_BY_CATEGORY),
    @NamedNativeQuery(name = ResourceType.QUERY_FIND_CHILDREN_BY_CATEGORY_admin, query = "" //
        + "(SELECT crt.id, crt.name, crt.category, crt.creation_data_type, crt.create_delete_policy, crt.singleton, crt.supports_manual_add, crt.description, crt.plugin, crt.ctime, crt.mtime, crt.subcategory_id, crt.plugin_config_def_id, crt.res_config_def_id "
        + "FROM RHQ_resource_type crt, RHQ_resource res, RHQ_resource_type rt, RHQ_resource_type_parents rtp "
        + "WHERE res.id = ? "
        + "AND res.resource_type_id = rt.id "
        + "AND rt.id = rtp.parent_resource_type_id "
        + "AND rtp.resource_type_id = crt.id "
        + "AND crt.category = ? "
        +
        //               "ORDER BY crt.name " +
        "UNION "
        + "(SELECT DISTINCT crt2.id, crt2.name, crt2.category, crt2.creation_data_type, crt2.create_delete_policy, crt2.singleton, crt2.supports_manual_add, crt2.description, crt2.plugin, crt2.ctime, crt2.mtime, crt2.subcategory_id, crt2.plugin_config_def_id, crt2.res_config_def_id "
        + "FROM RHQ_resource_type crt2 " + "WHERE 1 = "
        + "(SELECT COUNT(res2.id) "
        + "FROM RHQ_resource res2, RHQ_resource_type rt2 "
        + "WHERE res2.id = ? "
        + "AND res2.resource_type_id = rt2.id " + "AND rt2.category = 'PLATFORM') "
        + "AND 0 = "
        + "(SELECT COUNT(rtp2.resource_type_id) "
        + "FROM RHQ_resource_type_parents rtp2 "
        + "WHERE rtp2.resource_type_id = crt2.id) " + "AND crt2.category = ? " +
        //               "ORDER BY crt2.name" +
        ")) ORDER BY name", resultSetMapping = ResourceType.MAPPING_FIND_CHILDREN_BY_CATEGORY) })
@SqlResultSetMapping(name = ResourceType.MAPPING_FIND_CHILDREN_BY_CATEGORY, entities = { @EntityResult(entityClass = ResourceType.class) })
// @Cache(usage = CacheConcurrencyStrategy.TRANSACTIONAL)
public class ResourceType implements Externalizable, Comparable<ResourceType> {
    private static final long serialVersionUID = 1L;

    public static final ResourceType ANY_PLATFORM_TYPE = null;

    public static final String QUERY_FIND_BY_NAME = "ResourceType.findByName";
    public static final String QUERY_GET_EXPLICIT_RESOURCE_TYPE_COUNTS_BY_GROUP = "ResourceType.getExplicitResourceTypeCountsByGroup";
    public static final String QUERY_GET_IMPLICIT_RESOURCE_TYPE_COUNTS_BY_GROUP = "ResourceType.getImplicitResourceTypeCountsByGroup";
    public static final String QUERY_FIND_BY_NAME_AND_PLUGIN = "ResourceType.findByNameAndPlugin";
    public static final String QUERY_FIND_BY_PLUGIN = "ResourceType.findByPlugin";
    public static final String QUERY_FIND_BY_PARENT_AND_NAME = "ResourceType.findByParentAndName";
    public static final String QUERY_FIND_ALL = "ResourceType.findAll";
    public static final String QUERY_FIND_BY_ID_WITH_ALL_OPERATIONS = "ResourceType.findByIdWithAllOperations";
    public static final String QUERY_FIND_BY_CATEGORY = "ResourceType.findByCategory";
    public static final String QUERY_FIND_CHILDREN = "ResourceType.findChildren";
    public static final String QUERY_FIND_CHILDREN_admin = "ResourceType.findChildren_admin";
    /** find child resource types for resource :parentResource and category :category */
    public static final String QUERY_FIND_CHILDREN_BY_CATEGORY = "ResourceType.findChildrenByCategory";
    public static final String QUERY_FIND_CHILDREN_BY_CATEGORY_admin = "ResourceType.findChildrenByCategory_admin";
    /** find utilized (i.e. represented in inventory) child resource types for resource :parentResource and category :category */
    public static final String QUERY_FIND_UTILIZED_CHILDREN_BY_CATEGORY = "ResourceType.findUtilizedChildrenByCategory";
    public static final String QUERY_FIND_UTILIZED_CHILDREN_BY_CATEGORY_admin = "ResourceType.findUtilizedChildrenByCategory_admin";
    /** find child resource types for the resource type passed in :resourceType */
    public static final String FIND_CHILDREN_BY_PARENT = "ResourceType.findChildrenByParent";
    public static final String FIND_ALL_TEMPLATE_COUNT_COMPOSITES = "ResourceType.findAllTemplateCountComposites";
    public static final String QUERY_FIND_BY_SUBCATEGORY = "ResourceType.findBySubCategory";
    public static final String QUERY_FIND_UTILIZED_BY_CATEGORY = "ResourceType.findUtilizedByCategory";
    public static final String QUERY_FIND_UTILIZED_BY_CATEGORY_admin = "ResourceType.findUtilizedByCategory_admin";
    public static final String QUERY_FIND_BY_RESOURCE_GROUP = "ResourceType.findByResourceGroup";
    public static final String QUERY_FIND_BY_RESOURCE_GROUP_admin = "ResourceType.findByResourceGroup_admin";

    public static final String MAPPING_FIND_CHILDREN_BY_CATEGORY = "ResourceType.findChildrenByCategoryMapping";
    public static final String QUERY_FIND_RESOURCE_FACETS = "ResourceType.findResourceFacets";
    public static final String QUERY_FIND_DUPLICATE_TYPE_NAMES = "ResourceType.findDuplicateTypeNames";

    @Id
    @Column(name = "ID", nullable = false)
    @GeneratedValue(strategy = GenerationType.AUTO, generator = "SEQ")
    @Summary(index = 0)
    private int id;

    @Column(name = "NAME", nullable = false)
    @Summary(index = 1)
    private String name;

    @Column(name = "DESCRIPTION")
    private String description;

    @Column(name = "CATEGORY", nullable = false)
    @Enumerated(EnumType.STRING)
    @Summary(index = 2)
    private ResourceCategory category;

    @Column(name = "CREATION_DATA_TYPE", nullable = false)
    @Enumerated(EnumType.STRING)
    private ResourceCreationDataType creationDataType = ResourceCreationDataType.CONFIGURATION;

    @Column(name = "CREATE_DELETE_POLICY", nullable = false)
    @Enumerated(EnumType.STRING)
    private CreateDeletePolicy createDeletePolicy = CreateDeletePolicy.BOTH;

    @Column(name = "SUPPORTS_MANUAL_ADD", nullable = false)
    private boolean supportsManualAdd;

    @Column(name = "SINGLETON", nullable = false)
    private boolean singleton;

    @Column(name = "PLUGIN", nullable = false)
    @Summary(index = 3)
    private String plugin;

    @Column(name = "CTIME")
    private Long ctime;

    @Column(name = "MTIME")
    private Long mtime;

    @ManyToMany(mappedBy = "parentResourceTypes", cascade = CascadeType.ALL)
    @OrderBy
    //@Cache(usage = CacheConcurrencyStrategy.TRANSACTIONAL)
    private Set<ResourceType> childResourceTypes;

    @ManyToMany(cascade = CascadeType.PERSIST)
    // persist so self-injecting plugins work
    @JoinTable(name = "RHQ_RESOURCE_TYPE_PARENTS", joinColumns = { @JoinColumn(name = "RESOURCE_TYPE_ID") }, inverseJoinColumns = { @JoinColumn(name = "PARENT_RESOURCE_TYPE_ID") })
    @OrderBy
    //@Cache(usage = CacheConcurrencyStrategy.TRANSACTIONAL)
    private Set<ResourceType> parentResourceTypes;

    @JoinColumn(name = "PLUGIN_CONFIG_DEF_ID")
    @ManyToOne(cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    //@Cache(usage = CacheConcurrencyStrategy.TRANSACTIONAL)
    private ConfigurationDefinition pluginConfigurationDefinition;

    @JoinColumn(name = "RES_CONFIG_DEF_ID")
    @ManyToOne(cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    //@Cache(usage = CacheConcurrencyStrategy.TRANSACTIONAL)
    private ConfigurationDefinition resourceConfigurationDefinition;

    @JoinColumn(name = "SUBCATEGORY_ID")
    @ManyToOne(fetch = FetchType.LAZY)
    private ResourceSubCategory subCategory;

    @OneToMany(mappedBy = "resourceType", cascade = CascadeType.ALL)
    @OrderBy
    // primary key
    //@Cache(usage = CacheConcurrencyStrategy.TRANSACTIONAL)
    private Set<MeasurementDefinition> metricDefinitions;

    @OneToMany(mappedBy = "resourceType", cascade = CascadeType.ALL)
    @OrderBy
    // primary key
    //@Cache(usage = CacheConcurrencyStrategy.TRANSACTIONAL)
    private Set<EventDefinition> eventDefinitions;

    @OneToMany(mappedBy = "resourceType", cascade = CascadeType.ALL)
    @OrderBy
    // primary key
    private Set<OperationDefinition> operationDefinitions;

    @JoinColumn(name = "RESOURCE_TYPE_ID")
    @OneToMany(cascade = CascadeType.ALL)
    private Set<ProcessScan> processScans;

    @OneToMany(mappedBy = "resourceType", cascade = CascadeType.ALL)
    private Set<PackageType> packageTypes;

    @OneToMany(mappedBy = "resourceType", cascade = CascadeType.ALL)
    private List<ResourceSubCategory> subCategories;

    @OneToMany(mappedBy = "resourceType", cascade = CascadeType.REMOVE)
    private List<Resource> resources;

    @OneToMany(mappedBy = "resourceType", cascade = CascadeType.ALL)
    private Set<ProductVersion> productVersions;

    @Transient
    private transient String helpText;

    @Transient
    private transient ClassLoaderType classLoaderType;

    /* no-arg constructor required by EJB spec and Externalizable (Externalizable also requires it to be public) */
    public ResourceType() {
        // Intentionally left blank
    }

    public ResourceType(String name, String plugin, ResourceCategory category, ResourceType parentResourceType) {
        if (name == null) {
            throw new IllegalArgumentException("name==null");
        }
        if (plugin == null) {
            throw new IllegalArgumentException("plugin==null");
        }

        // Initialize empty ordered lists...
        this.childResourceTypes = new LinkedHashSet<ResourceType>();
        this.parentResourceTypes = new LinkedHashSet<ResourceType>();
        this.metricDefinitions = new LinkedHashSet<MeasurementDefinition>();
        this.eventDefinitions = new LinkedHashSet<EventDefinition>();
        this.operationDefinitions = new LinkedHashSet<OperationDefinition>();
        this.processScans = new HashSet<ProcessScan>();
        this.packageTypes = new HashSet<PackageType>();
        this.subCategories = new ArrayList<ResourceSubCategory>();
        this.productVersions = new HashSet<ProductVersion>();

        this.name = name;
        this.category = category;
        this.plugin = plugin;
        this.mtime = this.ctime = System.currentTimeMillis();

        if (parentResourceType != null) {
            parentResourceType.addChildResourceType(this);
        }
    }

    public int getId() {
        return this.id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getName() {
        return this.name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public ResourceCategory getCategory() {
        return this.category;
    }

    public void setCategory(ResourceCategory category) {
        this.category = category;
    }

    public ResourceCreationDataType getCreationDataType() {
        return creationDataType;
    }

    public void setCreationDataType(ResourceCreationDataType creationDataType) {
        if (creationDataType == null)
            throw new IllegalArgumentException("creationDataType cannot be null");

        this.creationDataType = creationDataType;
    }

    public CreateDeletePolicy getCreateDeletePolicy() {
        return createDeletePolicy;
    }

    public void setCreateDeletePolicy(CreateDeletePolicy createDeletePolicy) {
        if (createDeletePolicy == null)
            throw new IllegalArgumentException("createDeletePolicy cannot be null");

        this.createDeletePolicy = createDeletePolicy;
    }

    public boolean isCreatable() {
        return (createDeletePolicy == CreateDeletePolicy.BOTH || createDeletePolicy == CreateDeletePolicy.CREATE_ONLY);
    }

    public boolean isDeletable() {
        return (createDeletePolicy == CreateDeletePolicy.BOTH || createDeletePolicy == CreateDeletePolicy.DELETE_ONLY);
    }

    /**
     * Returns the ResourceSubCategory, if any, which this ResourceType
     * has been tagged with. If the ResourceType has not been tagged with
     * a subcategory, <code>null</code> is returned.
     */
    public ResourceSubCategory getSubCategory() {
        return this.subCategory;
    }

    /**
     * Tags this ResourceType as being part of the specified ResourceSubCategory
     */
    public void setSubCategory(ResourceSubCategory subcategory) {
        this.subCategory = subcategory;
    }

    /**
     * If true, this resource may only ever have one discovered instance per parent resource.
     * @return true if this is a singleton resource
     */
    public boolean isSingleton() {
        return singleton;
    }

    /**
     * @param singleton true if there is only ever one discovered instance per parent resource
     */
    public void setSingleton(boolean singleton) {
        this.singleton = singleton;
    }

    public boolean isSupportsManualAdd() {
        return supportsManualAdd;
    }

    public void setSupportsManualAdd(boolean supportsManualAdd) {
        this.supportsManualAdd = supportsManualAdd;
    }

    public String getDescription() {
        return this.description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getPlugin() {
        return this.plugin;
    }

    public void setPlugin(String plugin) {
        this.plugin = plugin;
    }

    public long getCtime() {
        return this.ctime;
    }

    @PrePersist
    void onPersist() {
        this.mtime = this.ctime = System.currentTimeMillis();
    }

    public long getMtime() {
        return this.mtime;
    }

    @PreUpdate
    void onUpdate() {
        this.mtime = System.currentTimeMillis();
    }

    public List<Resource> getResources() {
        return resources;
    }

    public void setResources(List<Resource> resources) {
        this.resources = resources;
    }

    public Set<ResourceType> getParentResourceTypes() {
        return this.parentResourceTypes;
    }

    public void setParentResourceTypes(Set<ResourceType> parentResourceTypes) {
        this.parentResourceTypes = parentResourceTypes;
    }

    /**
     * Makes this resource type a child of the given parent resource type.
     * @param parentResourceType
     */
    public void addParentResourceType(ResourceType parentResourceType) {
        parentResourceType.childResourceTypes.add(this);
        this.parentResourceTypes.add(parentResourceType);
    }

    /**
     * Removes the given resource type as a parent of this resource type.
     * @param oldParentResourceType
     */
    public void removeParentResourceType(ResourceType oldParentResourceType) {
        oldParentResourceType.childResourceTypes.remove(this);
        this.parentResourceTypes.remove(oldParentResourceType);
    }

    public Set<ResourceType> getChildResourceTypes() {
        return this.childResourceTypes;
    }

    /**
     * Makes this resource type a parent of the given child resource type.
     * @param childResourceType
     */
    public void addChildResourceType(ResourceType childResourceType) {
        childResourceType.parentResourceTypes.add(this);
        this.childResourceTypes.add(childResourceType);
    }

    /**
     * Removes the given resource type as a child of this resource type.
     * @param oldChildResourceType
     */
    public void removeChildResourceType(ResourceType oldChildResourceType) {
        oldChildResourceType.parentResourceTypes.remove(this);
        this.childResourceTypes.remove(oldChildResourceType);
    }

    public void setChildResourceTypes(Set<ResourceType> childResourceTypes) {
        this.childResourceTypes = childResourceTypes;
    }

    public ConfigurationDefinition getPluginConfigurationDefinition() {
        return pluginConfigurationDefinition;
    }

    public void setPluginConfigurationDefinition(ConfigurationDefinition pluginConfigurationDefinition) {
        this.pluginConfigurationDefinition = pluginConfigurationDefinition;
    }

    public ConfigurationDefinition getResourceConfigurationDefinition() {
        return resourceConfigurationDefinition;
    }

    public void setResourceConfigurationDefinition(ConfigurationDefinition resourceConfigurationDefinition) {
        this.resourceConfigurationDefinition = resourceConfigurationDefinition;
    }

    @XmlTransient
    public Set<MeasurementDefinition> getMetricDefinitions() {
        return metricDefinitions;
    }

    public void setMetricDefinitions(Set<MeasurementDefinition> metricDefinitions) {
        this.metricDefinitions = metricDefinitions;
    }

    public boolean addMetricDefinition(MeasurementDefinition metricDef) {
        metricDef.setResourceType(this);
        return this.metricDefinitions.add(metricDef);
    }

    @XmlTransient
    public Set<EventDefinition> getEventDefinitions() {
        return eventDefinitions;
    }

    public void setEventDefinitions(Set<EventDefinition> eventDefinitions) {
        this.eventDefinitions = eventDefinitions;
    }

    public void addEventDefinition(EventDefinition eventDefinition) {
        this.eventDefinitions.add(eventDefinition);
    }

    public Set<OperationDefinition> getOperationDefinitions() {
        return operationDefinitions;
    }

    public boolean addOperationDefinition(OperationDefinition operationDefinition) {
        operationDefinition.setResourceType(this);
        return this.operationDefinitions.add(operationDefinition);
    }

    public Set<ProcessScan> getProcessScans() {
        return this.processScans;
    }

    public void setProcessScans(Set<ProcessScan> processScans) {
        this.processScans = processScans;
    }

    public boolean addProcessScan(ProcessScan processMatch) {
        // this is unidirection - no need to set this resource this on process match

        if (this.processScans == null) {
            this.processScans = new HashSet<ProcessScan>();
        }
        return this.processScans.add(processMatch);
    }

    public Set<PackageType> getPackageTypes() {
        return packageTypes;
    }

    public void setPackageTypes(Set<PackageType> packageTypes) {
        this.packageTypes = packageTypes;
    }

    public void addPackageType(PackageType packageType) {
        if (this.packageTypes == null) {
            this.packageTypes = new HashSet<PackageType>();
        }
        packageType.setResourceType(this);
        this.packageTypes.add(packageType);
    }

    public void removePackageType(PackageType packageType) {
        if (this.packageTypes != null) {
            this.packageTypes.remove(packageType);
        }
        packageType.setResourceType(null);
    }

    /**
     * Returns the List of child ResourceSubCategorys which have been defined
     * on this ResourceType. These ResourceSubCategory's are available to
     * tag any child ResourceTypes of this ResourceType.
     *
     * @return the list of ResourceSubCategory's which have been defined
     *         on this ResourceType
     */
    public List<ResourceSubCategory> getChildSubCategories() {
        return this.subCategories;
    }

    /**
     * Sets the List of child ResourceSubCategorys for this ResourceType.
     *
     * @param subCategories the List of ResourceSubCategory's for this ResourceType
     */
    public void setChildSubCategories(List<ResourceSubCategory> subCategories) {
        this.subCategories = subCategories;
    }

    /**
     * Adds a child ResourceSubCategory to the List which has been defined
     * on this ResourceType.
     */
    public void addChildSubCategory(ResourceSubCategory subCategory) {
        if (this.subCategories == null) {
            this.subCategories = new ArrayList<ResourceSubCategory>();
        }
        subCategory.setResourceType(this);
        this.subCategories.add(subCategory);
    }

    public int compareTo(ResourceType that) {
        return this.name.compareTo(that.getName());
        // TODO: Order by category too?
    }

    public String getHelpText() {
        return helpText;
    }

    public void setHelpText(String helpText) {
        this.helpText = helpText;
    }

    public ClassLoaderType getClassLoaderType() {
        return classLoaderType;
    }

    public void setClassLoaderType(ClassLoaderType classLoaderType) {
        this.classLoaderType = classLoaderType;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null || !(obj instanceof ResourceType))
            return false;
        ResourceType that = (ResourceType) obj;
        if (!this.name.equals(that.name))
            return false;
        if (this.plugin != null ? !this.plugin.equals(that.plugin) : that.plugin != null)
            return false;
        return true;
    }

    @Override
    public int hashCode() {
        int result;
        if (name != null && this.plugin != null) {
            result = this.name.hashCode();
            result = 31 * result + (this.plugin != null ? plugin.hashCode() : 0);
        } else {
            result = 31 * id;
        }
        return result;
    }

    @Override
    public String toString() {
        /* TODO GH: Not safe, may not have been loaded (lazy initialization exceptions)
        StringBuffer parents = new StringBuffer();
         for (ResourceType parent : parentResourceTypes)
         {
            if (parents.length() > 0)
               parents.append(',');
            parents.append(parent.getName());
         }*/

        return "ResourceType[id=" + this.id + ", category=" + this.category + ", name=" + this.name + ", plugin="
            + this.plugin + /*", parents=" + parents +*/"]";
    }

    public void writeExternal(ObjectOutput out) throws IOException {
        ExternalizableStrategy.Subsystem strategy = ExternalizableStrategy.getStrategy();
        out.writeChar(strategy.id());

        if (ExternalizableStrategy.Subsystem.REMOTEAPI == strategy) {
            writeExternalRemote(out);
        } else if (ExternalizableStrategy.Subsystem.REFLECTIVE_SERIALIZATION == strategy) {
            EntitySerializer.writeExternalRemote(this, out);
        } else {
            writeExternalAgent(out);
        }
    }

    public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        char c = in.readChar();
        if (ExternalizableStrategy.Subsystem.REMOTEAPI.id() == c) {
            readExternalRemote(in);
        } else if (ExternalizableStrategy.Subsystem.REFLECTIVE_SERIALIZATION.id() == c) {
            EntitySerializer.readExternalRemote(this, in);
        } else {
            readExternalAgent(in);
        }
    }

    public void writeExternalAgent(ObjectOutput out) throws IOException {
        out.writeUTF(this.name);
        out.writeUTF(this.plugin);
    }

    public void readExternalAgent(ObjectInput in) throws IOException, ClassNotFoundException {
        this.name = in.readUTF();
        this.plugin = in.readUTF();
    }

    public void writeExternalRemote(ObjectOutput out) throws IOException {
        out.writeInt(this.id);
        out.writeUTF(this.name);
        out.writeUTF((null == this.description) ? "" : this.description);
        out.writeObject(this.category);
        out.writeObject(this.creationDataType);
        out.writeObject(this.createDeletePolicy);
        out.writeBoolean(this.supportsManualAdd);
        out.writeBoolean(this.singleton);
        out.writeUTF(this.plugin);
        out.writeLong(this.ctime);
        out.writeLong(this.mtime);
        out.writeObject(this.subCategory);
        out.writeObject((null == childResourceTypes) ? null : new LinkedHashSet<ResourceType>(childResourceTypes));
        out.writeObject((null == parentResourceTypes) ? null : new LinkedHashSet<ResourceType>(parentResourceTypes));
        out.writeObject(pluginConfigurationDefinition);
        out.writeObject(resourceConfigurationDefinition);
        out.writeObject((null == metricDefinitions) ? null
            : new LinkedHashSet<MeasurementDefinition>(metricDefinitions));
        out.writeObject((null == eventDefinitions) ? null : new LinkedHashSet<EventDefinition>(eventDefinitions));
        out.writeObject((null == operationDefinitions) ? null : new LinkedHashSet<OperationDefinition>(
            operationDefinitions));
        out.writeObject((null == processScans) ? null : new LinkedHashSet<ProcessScan>(processScans));
        out.writeObject((null == packageTypes) ? null : new LinkedHashSet<PackageType>(packageTypes));
        out.writeObject((null == subCategories) ? null : new LinkedHashSet<ResourceSubCategory>(subCategories));
        out.writeObject((null == resources) ? null : new LinkedHashSet<Resource>(resources));
        out.writeObject((null == productVersions) ? null : new LinkedHashSet<ProductVersion>(productVersions));
        // not supplied by remote: helpText
    }

    public void readExternalRemote(ObjectInput in) throws IOException, ClassNotFoundException {
        this.id = in.readInt();
        this.name = in.readUTF();
        this.description = in.readUTF();
        this.category = (ResourceCategory) in.readObject();
        this.creationDataType = (ResourceCreationDataType) in.readObject();
        this.createDeletePolicy = (CreateDeletePolicy) in.readObject();
        this.supportsManualAdd = in.readBoolean();
        this.singleton = in.readBoolean();
        this.plugin = in.readUTF();
        this.ctime = in.readLong();
        this.mtime = in.readLong();
    }

}