/*
 * File   : $Source: /alkacon/cvs/opencms/src/org/opencms/db/generic/CmsHistoryDriver.java,v $
 * Date   : $Date: 2007/04/26 15:26:28 $
 * Version: $Revision: 1.1.2.2 $
 *
 * This library is part of OpenCms -
 * the Open Source Content Mananagement System
 *
 * Copyright (c) 2005 Alkacon Software GmbH (http://www.alkacon.com)
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * For further information about Alkacon Software GmbH, please see the
 * company website: http://www.alkacon.com
 *
 * For further information about OpenCms, please see the
 * project website: http://www.opencms.org
 * 
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */

package org.opencms.db.generic;

import org.opencms.configuration.CmsConfigurationManager;
import org.opencms.db.CmsDbConsistencyException;
import org.opencms.db.CmsDbContext;
import org.opencms.db.CmsDbEntryNotFoundException;
import org.opencms.db.CmsDbSqlException;
import org.opencms.db.CmsDriverManager;
import org.opencms.db.CmsResourceState;
import org.opencms.db.I_CmsDriver;
import org.opencms.db.I_CmsHistoryDriver;
import org.opencms.file.CmsDataAccessException;
import org.opencms.file.CmsFile;
import org.opencms.file.CmsFolder;
import org.opencms.file.CmsProject;
import org.opencms.file.CmsProperty;
import org.opencms.file.CmsPropertyDefinition;
import org.opencms.file.CmsResource;
import org.opencms.file.CmsUser;
import org.opencms.file.CmsVfsResourceNotFoundException;
import org.opencms.file.history.CmsHistoryFile;
import org.opencms.file.history.CmsHistoryProject;
import org.opencms.file.history.I_CmsHistoryResource;
import org.opencms.main.CmsLog;
import org.opencms.security.CmsOrganizationalUnit;
import org.opencms.util.CmsStringUtil;
import org.opencms.util.CmsUUID;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;

/**
 * Generic (ANSI-SQL) database server implementation of the hitory driver methods.<p>
 * 
 * @author Thomas Weckert 
 * @author Michael Emmerich 
 * @author Carsten Weinholz  
 * @author Michael Moossen
 * 
 * @version $Revision: 1.1.2.2 $
 * 
 * @since 6.9.1
 */
public class CmsHistoryDriver implements I_CmsDriver, I_CmsHistoryDriver {

    /** The log object for this class. */
    private static final Log LOG = CmsLog.getLog(org.opencms.db.generic.CmsHistoryDriver.class);

    /** The driver manager instance. */
    protected CmsDriverManager m_driverManager;

    /** The SQL manager instance. */
    protected org.opencms.db.generic.CmsSqlManager m_sqlManager;

    /**
     * @see org.opencms.db.I_CmsHistoryDriver#createPropertyDefinition(org.opencms.db.CmsDbContext, java.lang.String, org.opencms.file.CmsPropertyDefinition.CmsPropertyType)
     */
    public CmsPropertyDefinition createPropertyDefinition(
        CmsDbContext dbc,
        String name,
        CmsPropertyDefinition.CmsPropertyType type) throws CmsDataAccessException {

        Connection conn = null;
        PreparedStatement stmt = null;

        try {
            conn = m_sqlManager.getConnection(dbc);
            stmt = m_sqlManager.getPreparedStatement(conn, "C_PROPERTYDEF_CREATE_HISTORY");
            stmt.setString(1, new CmsUUID().toString());
            stmt.setString(2, name);
            stmt.setInt(3, type.getMode());
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new CmsDbSqlException(Messages.get().container(
                Messages.ERR_GENERIC_SQL_1,
                CmsDbSqlException.getErrorQuery(stmt)), e);

        } finally {
            m_sqlManager.closeAll(dbc, conn, stmt, null);
        }

        return readPropertyDefinition(dbc, name);
    }

    /**
     * @see org.opencms.db.I_CmsHistoryDriver#createResource(java.sql.ResultSet, boolean)
     */
    public I_CmsHistoryResource createResource(ResultSet res, boolean hasContent) throws SQLException {

        byte[] content = null;

        int version = res.getInt(m_sqlManager.readQuery("C_RESOURCES_HISTORY_VERSION"));
        int tagId = res.getInt(m_sqlManager.readQuery("C_RESOURCES_PUBLISH_TAG"));
        CmsUUID structureId = new CmsUUID(res.getString(m_sqlManager.readQuery("C_RESOURCES_STRUCTURE_ID")));
        CmsUUID resourceId = new CmsUUID(res.getString(m_sqlManager.readQuery("C_RESOURCES_RESOURCE_ID")));
        String resourcePath = res.getString(m_sqlManager.readQuery("C_RESOURCES_RESOURCE_PATH"));
        int resourceType = res.getInt(m_sqlManager.readQuery("C_RESOURCES_RESOURCE_TYPE"));
        int resourceFlags = res.getInt(m_sqlManager.readQuery("C_RESOURCES_RESOURCE_FLAGS"));
        CmsUUID projectLastModified = new CmsUUID(
            res.getString(m_sqlManager.readQuery("C_RESOURCES_PROJECT_LASTMODIFIED")));
        int state = res.getInt(m_sqlManager.readQuery("C_RESOURCES_STATE"));
        long dateCreated = res.getLong(m_sqlManager.readQuery("C_RESOURCES_DATE_CREATED"));
        long dateLastModified = res.getLong(m_sqlManager.readQuery("C_RESOURCES_DATE_LASTMODIFIED"));
        long dateReleased = res.getLong(m_sqlManager.readQuery("C_RESOURCES_DATE_RELEASED"));
        long dateExpired = res.getLong(m_sqlManager.readQuery("C_RESOURCES_DATE_EXPIRED"));
        int resourceSize = res.getInt(m_sqlManager.readQuery("C_RESOURCES_SIZE"));
        CmsUUID userLastModified = new CmsUUID(res.getString(m_sqlManager.readQuery("C_RESOURCES_USER_LASTMODIFIED")));
        String userLastModifiedName = res.getString(m_sqlManager.readQuery("C_RESOURCES_LASTMODIFIED_BY_NAME"));
        CmsUUID userCreated = new CmsUUID(res.getString(m_sqlManager.readQuery("C_RESOURCES_USER_CREATED")));
        String userCreatedName = res.getString(m_sqlManager.readQuery("C_RESOURCES_USER_CREATED_NAME"));

        if (hasContent) {
            content = m_sqlManager.getBytes(res, m_sqlManager.readQuery("C_RESOURCES_FILE_CONTENT"));
        }
        long dateContent = res.getLong(m_sqlManager.readQuery("C_RESOURCES_DATE_CONTENT"));
        return new CmsHistoryFile(
            tagId,
            structureId,
            resourceId,
            resourcePath,
            resourceType,
            resourceFlags,
            projectLastModified,
            CmsResourceState.valueOf(state),
            dateCreated,
            userCreated,
            userCreatedName,
            dateLastModified,
            userLastModified,
            userLastModifiedName,
            dateReleased,
            dateExpired,
            resourceSize,
            dateContent,
            version,
            content);
    }

    /**
     * @see org.opencms.db.I_CmsHistoryDriver#deleteEntries(CmsDbContext, CmsUUID, CmsUUID, int)
     */
    public void deleteEntries(CmsDbContext dbc, CmsUUID structureId, CmsUUID resourceId, int versionsToKeep)
    throws CmsDataAccessException {

        Connection conn = null;
        PreparedStatement stmt = null;
        ResultSet res = null;

        try {
            conn = m_sqlManager.getConnection(dbc);

            // get the maximal version number for this resource
            int maxVersion = -1;
            stmt = m_sqlManager.getPreparedStatement(conn, "C_STRUCTURE_HISTORY_MAXVER");
            stmt.setString(1, structureId.toString());
            res = stmt.executeQuery();
            if (res.next()) {
                maxVersion = res.getInt(1);
            } else {
                // nothing to delete
                return;
            }
            m_sqlManager.closeAll(dbc, null, stmt, res);

            if (maxVersion - versionsToKeep < 0) {
                // nothing to delete
                return;
            }
            
            // get the minimal structure publish tag to keep, 
            // all entries with publish tag less than this will be deleted
            int minStrPublishTagToKeep = -1;
            stmt = m_sqlManager.getPreparedStatement(conn, "C_HISTORY_READ_MAXTAG_FOR_VERSION");
            stmt.setString(1, structureId.toString());
            stmt.setInt(2, 1 + maxVersion - versionsToKeep);
            res = stmt.executeQuery();
            if (res.next()) {
                minStrPublishTagToKeep = res.getInt(1);
            } else {
                // nothing to delete
                return;
            }
            m_sqlManager.closeAll(dbc, null, stmt, res);
            minStrPublishTagToKeep++;

            // delete the properties
            stmt = m_sqlManager.getPreparedStatement(conn, "C_PROPERTIES_HISTORY_DELETE");
            stmt.setString(1, structureId.toString());
            stmt.setInt(2, minStrPublishTagToKeep);
            stmt.executeUpdate();
            m_sqlManager.closeAll(dbc, null, stmt, null);

            // delete the structure entries
            stmt = m_sqlManager.getPreparedStatement(conn, "C_STRUCTURE_HISTORY_DELETE");
            stmt.setString(1, structureId.toString());
            stmt.setInt(2, minStrPublishTagToKeep);
            stmt.executeUpdate();
            m_sqlManager.closeAll(dbc, null, stmt, null);

            // get the minimal resource publish tag to keep, 
            // all entries with publish tag less than this will be deleted
            int minResPublishTagToKeep = -1;
            stmt = m_sqlManager.getPreparedStatement(conn, "C_HISTORY_READ_MIN_USED_TAG");
            stmt.setString(1, resourceId.toString());
            res = stmt.executeQuery();
            if (res.next()) {
                minResPublishTagToKeep = res.getInt(1);
            } else {
                // nothing to delete
                return;
            }
            m_sqlManager.closeAll(dbc, null, stmt, res);

            // delete the resource entries
            stmt = m_sqlManager.getPreparedStatement(conn, "C_RESOURCES_HISTORY_DELETE");
            stmt.setString(1, resourceId.toString());
            stmt.setInt(2, minResPublishTagToKeep);
            stmt.executeUpdate();
            m_sqlManager.closeAll(dbc, null, stmt, null);

            // delete the content entries
            stmt = m_sqlManager.getPreparedStatement(conn, "C_CONTENT_HISTORY_DELETE");
            stmt.setString(1, resourceId.toString());
            stmt.setInt(2, minResPublishTagToKeep);
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new CmsDbSqlException(Messages.get().container(
                Messages.ERR_GENERIC_SQL_1,
                CmsDbSqlException.getErrorQuery(stmt)), e);
        } finally {
            m_sqlManager.closeAll(dbc, conn, stmt, res);
        }
    }

    /**
     * @see org.opencms.db.I_CmsHistoryDriver#deleteEntries(org.opencms.db.CmsDbContext, int)
     */
    public void deleteEntries(CmsDbContext dbc, int versionsToKeep) throws CmsDataAccessException {

        Connection conn = null;
        PreparedStatement stmt = null;
        ResultSet res = null;

        Map entries = new HashMap();
        try {
            conn = m_sqlManager.getConnection(dbc);

            // get all historical entries that may come in question
            stmt = m_sqlManager.getPreparedStatement(conn, "C_STRUCTURE_HISTORY_READALL");
            res = stmt.executeQuery();
            while (res.next()) {
                CmsUUID structureId = new CmsUUID(res.getString(1));
                CmsUUID resourceId = new CmsUUID(res.getString(2));
                entries.put(structureId, resourceId);
            }
        } catch (SQLException e) {
            throw new CmsDbSqlException(Messages.get().container(
                Messages.ERR_GENERIC_SQL_1,
                CmsDbSqlException.getErrorQuery(stmt)), e);
        } finally {
            m_sqlManager.closeAll(dbc, conn, stmt, res);
        }
        Iterator it = entries.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry entry = (Map.Entry)it.next();
            deleteEntries(dbc, (CmsUUID)entry.getKey(), (CmsUUID)entry.getValue(), versionsToKeep);
        }
    }

    /**
     * @see org.opencms.db.I_CmsHistoryDriver#deleteOrphanEntries(org.opencms.db.CmsDbContext)
     */
    public void deleteOrphanEntries(CmsDbContext dbc) throws CmsDataAccessException {

        Connection conn = null;
        PreparedStatement stmt = null;
        ResultSet res = null;

        Map entries = new HashMap();
        try {
            conn = m_sqlManager.getConnection(dbc);

            // get all historical entries that may come in question
            stmt = m_sqlManager.getPreparedStatement(conn, "C_STRUCTURE_HISTORY_READ_DELETED");
            res = stmt.executeQuery();
            while (res.next()) {
                CmsUUID structureId = new CmsUUID(res.getString(1));
                CmsUUID resourceId = new CmsUUID(res.getString(2));
                entries.put(structureId, resourceId);
            }
        } catch (SQLException e) {
            throw new CmsDbSqlException(Messages.get().container(
                Messages.ERR_GENERIC_SQL_1,
                CmsDbSqlException.getErrorQuery(stmt)), e);
        } finally {
            m_sqlManager.closeAll(dbc, conn, stmt, res);
        }
        Iterator it = entries.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry entry = (Map.Entry)it.next();
            deleteEntries(dbc, (CmsUUID)entry.getKey(), (CmsUUID)entry.getValue(), 0);
        }
    }

    /**
     * @see org.opencms.db.I_CmsHistoryDriver#deletePropertyDefinition(org.opencms.db.CmsDbContext, org.opencms.file.CmsPropertyDefinition)
     */
    public void deletePropertyDefinition(CmsDbContext dbc, CmsPropertyDefinition metadef) throws CmsDataAccessException {

        Connection conn = null;
        PreparedStatement stmt = null;

        try {
            if ((internalCountProperties(dbc, metadef, CmsProject.ONLINE_PROJECT_ID) != 0)
                || (internalCountProperties(dbc, metadef, CmsUUID.getOpenCmsUUID()) != 0)) { // HACK: to get an offline project

                throw new CmsDbConsistencyException(Messages.get().container(
                    Messages.ERR_ERROR_DELETING_PROPERTYDEF_1,
                    metadef.getName()));
            }

            // delete the historical property definition
            conn = m_sqlManager.getConnection(dbc);
            stmt = m_sqlManager.getPreparedStatement(conn, "C_PROPERTYDEF_DELETE_HISTORY");
            stmt.setString(1, metadef.getId().toString());
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new CmsDbSqlException(Messages.get().container(
                Messages.ERR_GENERIC_SQL_1,
                CmsDbSqlException.getErrorQuery(stmt)), e);
        } finally {
            m_sqlManager.closeAll(dbc, conn, stmt, null);
        }
    }

    /**
     * @see org.opencms.db.I_CmsHistoryDriver#destroy()
     */
    public void destroy() throws Throwable {

        m_sqlManager = null;
        m_driverManager = null;

        if (CmsLog.INIT.isInfoEnabled()) {
            CmsLog.INIT.info(Messages.get().getBundle().key(Messages.INIT_SHUTDOWN_DRIVER_1, getClass().getName()));
        }
    }

    /**
     * @see org.opencms.db.I_CmsHistoryDriver#getSqlManager()
     */
    public CmsSqlManager getSqlManager() {

        return m_sqlManager;
    }

    /**
     * @see org.opencms.db.I_CmsDriver#init(org.opencms.db.CmsDbContext, org.opencms.configuration.CmsConfigurationManager, java.util.List, org.opencms.db.CmsDriverManager)
     */
    public void init(
        CmsDbContext dbc,
        CmsConfigurationManager configurationManager,
        List successiveDrivers,
        CmsDriverManager driverManager) {

        Map configuration = configurationManager.getConfiguration();

        String poolUrl;
        if (configuration.get("db.history.pool") != null) {
            poolUrl = configuration.get("db.history.pool").toString();
        } else {
            int todoDeprecated; // deprecated, remove as soon as possible
            poolUrl = configuration.get("db.backup.pool").toString();
        }

        String classname;
        if (configuration.get("db.history.sqlmanager") != null) {
            classname = configuration.get("db.history.sqlmanager").toString();
        } else {
            int todoDeprecated; // deprecated, remove as soon as possible
            classname = configuration.get("db.backup.sqlmanager").toString();
        }

        m_sqlManager = initSqlManager(classname);
        m_sqlManager.init(I_CmsHistoryDriver.DRIVER_TYPE_ID, poolUrl);

        m_driverManager = driverManager;

        if (CmsLog.INIT.isInfoEnabled()) {
            CmsLog.INIT.info(Messages.get().getBundle().key(Messages.INIT_ASSIGNED_POOL_1, poolUrl));
        }

        if ((successiveDrivers != null) && !successiveDrivers.isEmpty()) {
            if (LOG.isWarnEnabled()) {
                LOG.warn(Messages.get().getBundle().key(
                    Messages.LOG_SUCCESSIVE_DRIVERS_UNSUPPORTED_1,
                    getClass().getName()));
            }
        }
    }

    /**
     * @see org.opencms.db.I_CmsHistoryDriver#initSqlManager(String)
     */
    public org.opencms.db.generic.CmsSqlManager initSqlManager(String classname) {

        return CmsSqlManager.getInstance(classname);
    }

    /**
     * @see org.opencms.db.I_CmsHistoryDriver#readAllAvailableVersions(CmsDbContext, CmsUUID)
     */
    public List readAllAvailableVersions(CmsDbContext dbc, CmsUUID structureId) throws CmsDataAccessException {

        ResultSet res = null;
        List historyResources = new ArrayList();
        PreparedStatement stmt = null;
        Connection conn = null;

        try {
            conn = m_sqlManager.getConnection(dbc);

            int publishTag = -1;
            stmt = m_sqlManager.getPreparedStatement(conn, "C_STRUCTURE_HISTORY_READ_MIN_PUBLISH_TAG");
            stmt.setString(1, structureId.toString());
            res = stmt.executeQuery();
            if (res.next()) {
                publishTag = res.getInt(1);
            }
            m_sqlManager.closeAll(dbc, null, stmt, res);

            stmt = m_sqlManager.getPreparedStatement(conn, "C_RESOURCES_HISTORY_READ_ALL_VERSIONS");
            stmt.setString(1, structureId.toString());
            stmt.setString(2, structureId.toString());
            stmt.setInt(3, publishTag);
            res = stmt.executeQuery();
            while (res.next()) {
                historyResources.add(createResource(res, false));
            }
        } catch (SQLException e) {
            throw new CmsDbSqlException(Messages.get().container(
                Messages.ERR_GENERIC_SQL_1,
                CmsDbSqlException.getErrorQuery(stmt)), e);
        } finally {
            m_sqlManager.closeAll(dbc, conn, stmt, res);
        }

        return historyResources;
    }

    /**
     * @see org.opencms.db.I_CmsHistoryDriver#readContent(org.opencms.db.CmsDbContext, org.opencms.util.CmsUUID, int)
     */
    public byte[] readContent(CmsDbContext dbc, CmsUUID structureId, int version) throws CmsDataAccessException {

        Connection conn = null;
        PreparedStatement stmt = null;
        ResultSet res = null;
        byte[] content = null;

        try {
            conn = m_sqlManager.getConnection(dbc);
            stmt = m_sqlManager.getPreparedStatement(conn, "C_HISTORY_READ_CONTENT");
            stmt.setString(1, structureId.toString());
            stmt.setInt(2, version);
            res = stmt.executeQuery();

            if (res.next()) {
                content = m_sqlManager.getBytes(res, m_sqlManager.readQuery("C_RESOURCES_FILE_CONTENT"));
            }
        } catch (SQLException e) {
            throw new CmsDbSqlException(Messages.get().container(
                Messages.ERR_GENERIC_SQL_1,
                CmsDbSqlException.getErrorQuery(stmt)), e);
        } finally {
            m_sqlManager.closeAll(dbc, conn, stmt, res);
        }
        return content;
    }

    /**
     * @see org.opencms.db.I_CmsHistoryDriver#readDeletedResources(CmsDbContext, CmsUUID)
     */
    public List readDeletedResources(CmsDbContext dbc, CmsUUID structureId) throws CmsDataAccessException {

        Connection conn = null;
        PreparedStatement stmt = null;
        ResultSet res = null;
        List result = new ArrayList();

        try {
            conn = m_sqlManager.getConnection(dbc);
            stmt = m_sqlManager.getPreparedStatement(conn, "C_STRUCTURE_HISTORY_READ_DELETED");
            stmt.setString(1, structureId.toString());
            res = stmt.executeQuery();
            while (res.next()) {
                result.add(createResource(res, false));
            }
        } catch (SQLException e) {
            throw new CmsDbSqlException(Messages.get().container(
                Messages.ERR_GENERIC_SQL_1,
                CmsDbSqlException.getErrorQuery(stmt)), e);
        } finally {
            m_sqlManager.closeAll(dbc, conn, stmt, res);
        }
        return result;
    }

    /**
     * @see org.opencms.db.I_CmsHistoryDriver#readFile(CmsDbContext, CmsUUID, int)
     * @deprecated
     */
    public I_CmsHistoryResource readFile(CmsDbContext dbc, CmsUUID structureId, int tagId)
    throws CmsDataAccessException {

        I_CmsHistoryResource file = null;
        PreparedStatement stmt = null;
        ResultSet res = null;
        Connection conn = null;

        try {
            conn = m_sqlManager.getConnection(dbc);
            stmt = m_sqlManager.getPreparedStatement(conn, "C_FILES_READ_HISTORY_BYID");
            stmt.setString(1, structureId.toString());
            stmt.setInt(2, tagId);
            res = stmt.executeQuery();
            if (res.next()) {
                file = createResource(res, true);
                while (res.next()) {
                    // do nothing only move through all rows because of mssql odbc driver
                }
            } else {
                throw new CmsVfsResourceNotFoundException(Messages.get().container(
                    Messages.ERR_HISTORY_FILE_NOT_FOUND_1,
                    structureId));
            }
        } catch (SQLException e) {
            throw new CmsDbSqlException(Messages.get().container(
                Messages.ERR_GENERIC_SQL_1,
                CmsDbSqlException.getErrorQuery(stmt)), e);
        } finally {
            m_sqlManager.closeAll(dbc, conn, stmt, res);
        }

        return file;
    }

    /**
     * @see org.opencms.db.I_CmsHistoryDriver#readLastVersion(org.opencms.db.CmsDbContext, org.opencms.util.CmsUUID)
     */
    public int readLastVersion(CmsDbContext dbc, CmsUUID structureId) throws CmsDataAccessException {

        PreparedStatement stmt = null;
        Connection conn = null;
        ResultSet res = null;
        int lastVersion = 0;

        try {
            conn = m_sqlManager.getConnection(dbc);
            stmt = m_sqlManager.getPreparedStatement(conn, "C_STRUCTURE_HISTORY_MAXVER");
            stmt.setString(1, structureId.toString());
            res = stmt.executeQuery();

            if (res.next()) {
                lastVersion = res.getInt(1);
            } else {
                lastVersion = 0;
            }
        } catch (SQLException e) {
            throw new CmsDbSqlException(Messages.get().container(
                Messages.ERR_GENERIC_SQL_1,
                CmsDbSqlException.getErrorQuery(stmt)), e);
        } finally {
            m_sqlManager.closeAll(dbc, conn, stmt, res);
        }

        return lastVersion;
    }

    /**
     * @see org.opencms.db.I_CmsHistoryDriver#readMaxPublishTag(org.opencms.db.CmsDbContext, org.opencms.file.CmsResource)
     */
    public int readMaxPublishTag(CmsDbContext dbc, CmsResource resource) throws CmsDataAccessException {

        PreparedStatement stmt = null;
        Connection conn = null;
        ResultSet res = null;
        int result = 0;

        try {
            conn = m_sqlManager.getConnection(dbc);
            stmt = m_sqlManager.getPreparedStatement(conn, "C_RESOURCES_READ_MAX_PUBLISH_TAG");
            stmt.setString(1, resource.getResourceId().toString());
            res = stmt.executeQuery();

            if (res.next()) {
                result = res.getInt(1);
            }
        } catch (SQLException e) {
            throw new CmsDbSqlException(Messages.get().container(
                Messages.ERR_GENERIC_SQL_1,
                CmsDbSqlException.getErrorQuery(stmt)), e);
        } finally {
            m_sqlManager.closeAll(dbc, conn, stmt, res);
        }

        return result;
    }

    /**
     * @see org.opencms.db.I_CmsHistoryDriver#readNextPublishTag(org.opencms.db.CmsDbContext)
     */
    public int readNextPublishTag(CmsDbContext dbc) {

        PreparedStatement stmt = null;
        Connection conn = null;
        ResultSet res = null;
        int projectPublishTag = 1;
        int resourcePublishTag = 1;

        try {
            // get the max version id
            conn = m_sqlManager.getConnection(dbc);
            stmt = m_sqlManager.getPreparedStatement(conn, "C_PROJECTS_HISTORY_MAXTAG");
            res = stmt.executeQuery();

            if (res.next()) {
                projectPublishTag = res.getInt(1) + 1;
            }

            m_sqlManager.closeAll(dbc, null, stmt, res);

            stmt = m_sqlManager.getPreparedStatement(conn, "C_RESOURCES_HISTORY_MAXTAG");
            res = stmt.executeQuery();

            if (res.next()) {
                resourcePublishTag = res.getInt(1) + 1;
            }

            if (resourcePublishTag > projectPublishTag) {
                projectPublishTag = resourcePublishTag;
            }
        } catch (SQLException exc) {
            return 1;
        } finally {
            m_sqlManager.closeAll(dbc, conn, stmt, res);
        }

        return projectPublishTag;
    }

    /**
     * @see org.opencms.db.I_CmsHistoryDriver#readProject(org.opencms.db.CmsDbContext, int)
     */
    public CmsHistoryProject readProject(CmsDbContext dbc, int publishTag) throws CmsDataAccessException {

        PreparedStatement stmt = null;
        CmsHistoryProject project = null;
        ResultSet res = null;
        Connection conn = null;
        try {
            conn = m_sqlManager.getConnection(dbc);
            stmt = m_sqlManager.getPreparedStatement(conn, "C_PROJECTS_HISTORY_READ");

            stmt.setInt(1, publishTag);
            res = stmt.executeQuery();

            if (res.next()) {
                List projectresources = readProjectResources(dbc, publishTag);
                project = internalCreateProject(res, projectresources);
            } else {
                throw new CmsDbEntryNotFoundException(Messages.get().container(
                    Messages.ERR_NO_HISTORY_PROJECT_WITH_TAG_ID_1,
                    new Integer(publishTag)));
            }
        } catch (SQLException e) {
            throw new CmsDbSqlException(Messages.get().container(
                Messages.ERR_GENERIC_SQL_1,
                CmsDbSqlException.getErrorQuery(stmt)), e);
        } finally {
            m_sqlManager.closeAll(dbc, conn, stmt, res);
        }
        return project;
    }

    /**
     * @see org.opencms.db.I_CmsHistoryDriver#readProjectResources(org.opencms.db.CmsDbContext, int)
     */
    public List readProjectResources(CmsDbContext dbc, int publishTag) throws CmsDataAccessException {

        PreparedStatement stmt = null;
        Connection conn = null;
        ResultSet res = null;
        List projectResources = new ArrayList();

        try {
            conn = m_sqlManager.getConnection(dbc);
            stmt = m_sqlManager.getPreparedStatement(conn, "C_PROJECTRESOURCES_HISTORY_READ");
            stmt.setInt(1, publishTag);
            res = stmt.executeQuery();
            while (res.next()) {
                projectResources.add(res.getString("RESOURCE_PATH"));
            }
        } catch (SQLException e) {
            throw new CmsDbSqlException(Messages.get().container(
                Messages.ERR_GENERIC_SQL_1,
                CmsDbSqlException.getErrorQuery(stmt)), e);
        } finally {
            m_sqlManager.closeAll(dbc, conn, stmt, res);
        }

        return projectResources;
    }

    /**
     * @see org.opencms.db.I_CmsHistoryDriver#readProjects(org.opencms.db.CmsDbContext)
     */
    public List readProjects(CmsDbContext dbc) throws CmsDataAccessException {

        List projects = new ArrayList();
        ResultSet res = null;
        PreparedStatement stmt = null;
        Connection conn = null;

        try {
            // create the statement
            conn = m_sqlManager.getConnection(dbc);
            stmt = m_sqlManager.getPreparedStatement(conn, "C_PROJECTS_HISTORY_READ_ALL");
            res = stmt.executeQuery();

            // TODO: this is not really efficient!!
            int i = 0;
            int max = 300;

            while (res.next() && (i < max)) {
                List resources = readProjectResources(dbc, res.getInt("PUBLISH_TAG"));
                projects.add(internalCreateProject(res, resources));
                i++;
            }
        } catch (SQLException e) {
            throw new CmsDbSqlException(Messages.get().container(
                Messages.ERR_GENERIC_SQL_1,
                CmsDbSqlException.getErrorQuery(stmt)), e);
        } finally {
            m_sqlManager.closeAll(dbc, conn, stmt, res);
        }
        return (projects);
    }

    /** 
     * @see org.opencms.db.I_CmsHistoryDriver#readProperties(org.opencms.db.CmsDbContext, org.opencms.file.history.I_CmsHistoryResource)
     */
    public List readProperties(CmsDbContext dbc, I_CmsHistoryResource resource) throws CmsDataAccessException {

        ResultSet res = null;
        PreparedStatement stmt = null;
        Connection conn = null;

        Map propertyMap = new HashMap();

        try {
            conn = m_sqlManager.getConnection(dbc);
            stmt = m_sqlManager.getPreparedStatement(conn, "C_PROPERTIES_HISTORY_READALL");
            stmt.setString(1, resource.getResource().getStructureId().toString());
            stmt.setString(2, resource.getResource().getResourceId().toString());
            stmt.setInt(3, resource.getPublishTag());
            res = stmt.executeQuery();

            while (res.next()) {
                String propertyKey = res.getString(1);
                String propertyValue = res.getString(2);
                int mappingType = res.getInt(3);

                CmsProperty property = (CmsProperty)propertyMap.get(propertyKey);
                if (property != null) {
                    // there exists already a property for this key in the result
                    switch (mappingType) {
                        case CmsProperty.STRUCTURE_RECORD_MAPPING:
                            // this property value is mapped to a structure record
                            property.setStructureValue(propertyValue);
                            break;
                        case CmsProperty.RESOURCE_RECORD_MAPPING:
                            // this property value is mapped to a resource record
                            property.setResourceValue(propertyValue);
                            break;
                        default:
                            throw new CmsDbConsistencyException(Messages.get().container(
                                Messages.ERR_UNKNOWN_PROPERTY_VALUE_MAPPING_3,
                                resource.getResource().getRootPath(),
                                new Integer(mappingType),
                                propertyKey));
                    }
                } else {
                    // there doesn't exist a property for this key yet
                    property = new CmsProperty();
                    property.setName(propertyKey);

                    switch (mappingType) {
                        case CmsProperty.STRUCTURE_RECORD_MAPPING:
                            // this property value is mapped to a structure record
                            property.setStructureValue(propertyValue);
                            property.setResourceValue(null);
                            break;
                        case CmsProperty.RESOURCE_RECORD_MAPPING:
                            // this property value is mapped to a resource record
                            property.setStructureValue(null);
                            property.setResourceValue(propertyValue);
                            break;
                        default:
                            throw new CmsDbConsistencyException(Messages.get().container(
                                Messages.ERR_UNKNOWN_PROPERTY_VALUE_MAPPING_3,
                                resource.getResource().getRootPath(),
                                new Integer(mappingType),
                                propertyKey));
                    }
                    propertyMap.put(propertyKey, property);
                }
            }
        } catch (SQLException e) {
            throw new CmsDbSqlException(Messages.get().container(
                Messages.ERR_GENERIC_SQL_1,
                CmsDbSqlException.getErrorQuery(stmt)), e);
        } finally {
            m_sqlManager.closeAll(dbc, conn, stmt, res);
        }

        return new ArrayList(propertyMap.values());
    }

    /**
     * @see org.opencms.db.I_CmsHistoryDriver#readPropertyDefinition(org.opencms.db.CmsDbContext, java.lang.String)
     */
    public CmsPropertyDefinition readPropertyDefinition(CmsDbContext dbc, String name) throws CmsDataAccessException {

        CmsPropertyDefinition propDef = null;
        ResultSet res = null;
        PreparedStatement stmt = null;
        Connection conn = null;

        try {
            conn = m_sqlManager.getConnection(dbc);
            stmt = m_sqlManager.getPreparedStatement(conn, "C_PROPERTYDEF_READ_HISTORY");
            stmt.setString(1, name);
            res = stmt.executeQuery();

            if (res.next()) {
                propDef = new CmsPropertyDefinition(
                    new CmsUUID(res.getString(m_sqlManager.readQuery("C_PROPERTYDEF_ID"))),
                    res.getString(m_sqlManager.readQuery("C_PROPERTYDEF_NAME")),
                    CmsPropertyDefinition.CmsPropertyType.valueOf(res.getInt(m_sqlManager.readQuery("C_PROPERTYDEF_TYPE"))));
            } else {
                throw new CmsDbEntryNotFoundException(Messages.get().container(
                    Messages.ERR_NO_PROPERTYDEF_WITH_NAME_1,
                    name));
            }
        } catch (SQLException e) {
            throw new CmsDbSqlException(Messages.get().container(
                Messages.ERR_GENERIC_SQL_1,
                CmsDbSqlException.getErrorQuery(stmt)), e);
        } finally {
            m_sqlManager.closeAll(dbc, conn, stmt, res);
        }

        return propDef;
    }

    /**
     * @see org.opencms.db.I_CmsHistoryDriver#readPublishTag(org.opencms.db.CmsDbContext, long)
     */
    public int readPublishTag(CmsDbContext dbc, long maxdate) throws CmsDataAccessException {

        ResultSet res = null;
        PreparedStatement stmt = null;
        Connection conn = null;
        int maxVersion = 0;

        try {
            conn = m_sqlManager.getConnection(dbc);
            stmt = m_sqlManager.getPreparedStatement(conn, "C_PROJECTS_HISTORY_READ_TAG_FOR_DATE");
            stmt.setLong(1, maxdate);
            res = stmt.executeQuery();
            if (res.next()) {
                maxVersion = res.getInt(1);
            }
        } catch (SQLException e) {
            throw new CmsDbSqlException(Messages.get().container(
                Messages.ERR_GENERIC_SQL_1,
                CmsDbSqlException.getErrorQuery(stmt)), e);
        } finally {
            m_sqlManager.closeAll(dbc, conn, stmt, res);
        }
        return maxVersion;
    }

    /**
     * @see org.opencms.db.I_CmsHistoryDriver#readResource(CmsDbContext, CmsUUID, int)
     */
    public I_CmsHistoryResource readResource(CmsDbContext dbc, CmsUUID structureId, int version)
    throws CmsDataAccessException {

        I_CmsHistoryResource resource = null;
        PreparedStatement stmt = null;
        ResultSet res = null;
        Connection conn = null;

        try {
            conn = m_sqlManager.getConnection(dbc);
            stmt = m_sqlManager.getPreparedStatement(conn, "C_RESOURCES_HISTORY_READ_VERSION");
            stmt.setString(1, structureId.toString());
            stmt.setInt(2, version);
            res = stmt.executeQuery();
            if (res.next()) {
                resource = createResource(res, false);
                while (res.next()) {
                    // do nothing only move through all rows because of mssql odbc driver
                }
            } else {
                throw new CmsVfsResourceNotFoundException(Messages.get().container(
                    Messages.ERR_HISTORY_FILE_NOT_FOUND_1,
                    structureId));
            }
        } catch (SQLException e) {
            throw new CmsDbSqlException(Messages.get().container(
                Messages.ERR_GENERIC_SQL_1,
                CmsDbSqlException.getErrorQuery(stmt)), e);
        } finally {
            m_sqlManager.closeAll(dbc, conn, stmt, res);
        }
        return resource;
    }

    /**
     * @see org.opencms.db.I_CmsHistoryDriver#writeProject(org.opencms.db.CmsDbContext, int, long)
     */
    public void writeProject(CmsDbContext dbc, int publishTag, long publishDate) throws CmsDataAccessException {

        Connection conn = null;
        PreparedStatement stmt = null;
        String group = null;
        String managerGroup = null;

        CmsProject currentProject = dbc.currentProject();
        CmsUser currentUser = dbc.currentUser();

        CmsUser owner = m_driverManager.getUserDriver().readUser(dbc, currentProject.getOwnerId());

        StringBuffer buf = new StringBuffer();
        buf.append(owner.getName()).append(" ").append(owner.getFirstname()).append(" ").append(owner.getLastname());
        String ownerName = buf.toString();

        try {
            group = m_driverManager.getUserDriver().readGroup(dbc, currentProject.getGroupId()).getName();
        } catch (CmsDbEntryNotFoundException e) {
            // the group could not be read
            group = "";
        }

        try {
            managerGroup = m_driverManager.getUserDriver().readGroup(dbc, currentProject.getManagerGroupId()).getName();
        } catch (CmsDbEntryNotFoundException e) {
            // the group could not be read
            managerGroup = "";
        }

        List projectresources = m_driverManager.getProjectDriver().readProjectResources(dbc, currentProject);

        // write historical project to the database
        try {
            conn = m_sqlManager.getConnection(dbc);

            stmt = m_sqlManager.getPreparedStatement(conn, "C_PROJECTS_HISTORY_CREATE");
            // first write the project
            stmt.setInt(1, publishTag);
            stmt.setString(2, currentProject.getUuid().toString());
            stmt.setString(3, currentProject.getSimpleName());
            stmt.setLong(4, publishDate);
            stmt.setString(5, currentUser.getId().toString());
            stmt.setString(6, currentUser.getName()
                + " "
                + currentUser.getFirstname()
                + " "
                + currentUser.getLastname());
            stmt.setString(7, currentProject.getOwnerId().toString());
            stmt.setString(8, ownerName);
            stmt.setString(9, currentProject.getGroupId().toString());
            stmt.setString(10, group);
            stmt.setString(11, currentProject.getManagerGroupId().toString());
            stmt.setString(12, managerGroup);
            stmt.setString(13, currentProject.getDescription());
            stmt.setLong(14, currentProject.getDateCreated());
            stmt.setInt(15, currentProject.getType().getMode());
            stmt.setString(16, CmsOrganizationalUnit.SEPARATOR + currentProject.getOuFqn());
            stmt.executeUpdate();

            m_sqlManager.closeAll(dbc, null, stmt, null);

            // now write the projectresources
            stmt = m_sqlManager.getPreparedStatement(conn, "C_PROJECTRESOURCES_HISTORY_CREATE");
            Iterator i = projectresources.iterator();
            while (i.hasNext()) {
                stmt.setInt(1, publishTag);
                stmt.setString(2, currentProject.getUuid().toString());
                stmt.setString(3, (String)i.next());
                stmt.executeUpdate();
                stmt.clearParameters();
            }
        } catch (SQLException e) {
            throw new CmsDbSqlException(Messages.get().container(
                Messages.ERR_GENERIC_SQL_1,
                CmsDbSqlException.getErrorQuery(stmt)), e);
        } finally {
            m_sqlManager.closeAll(dbc, conn, stmt, null);
        }
    }

    /**
     * @see org.opencms.db.I_CmsHistoryDriver#writeProperties(org.opencms.db.CmsDbContext, org.opencms.file.CmsResource, java.util.List, int)
     */
    public void writeProperties(CmsDbContext dbc, CmsResource resource, List properties, int publishTag)
    throws CmsDataAccessException {

        Connection conn = null;
        PreparedStatement stmt = null;

        try {
            conn = m_sqlManager.getConnection(dbc);

            Iterator dummy = properties.iterator();
            while (dummy.hasNext()) {
                CmsProperty property = (CmsProperty)dummy.next();
                CmsPropertyDefinition propDef = readPropertyDefinition(dbc, property.getName());

                for (int i = 0; i < 2; i++) {
                    int mappingType;
                    String value;
                    CmsUUID id;
                    if (i == 0) {
                        // write the structure value on the first cycle
                        value = property.getStructureValue();
                        mappingType = CmsProperty.STRUCTURE_RECORD_MAPPING;
                        id = resource.getStructureId();
                        if (CmsStringUtil.isEmpty(value)) {
                            continue;
                        }
                    } else {
                        // write the resource value on the second cycle
                        value = property.getResourceValue();
                        mappingType = CmsProperty.RESOURCE_RECORD_MAPPING;
                        id = resource.getResourceId();
                        if (CmsStringUtil.isEmpty(value)) {
                            break;
                        }
                    }

                    stmt = m_sqlManager.getPreparedStatement(conn, "C_PROPERTIES_HISTORY_CREATE");

                    stmt.setString(1, resource.getStructureId().toString());
                    stmt.setString(2, propDef.getId().toString());
                    stmt.setString(3, id.toString());
                    stmt.setInt(4, mappingType);
                    stmt.setString(5, m_sqlManager.validateEmpty(value));
                    stmt.setInt(6, publishTag);

                    stmt.executeUpdate();
                    m_sqlManager.closeAll(dbc, null, stmt, null);
                }
            }
        } catch (SQLException e) {
            throw new CmsDbSqlException(Messages.get().container(
                Messages.ERR_GENERIC_SQL_1,
                CmsDbSqlException.getErrorQuery(stmt)), e);
        } finally {
            m_sqlManager.closeAll(dbc, conn, stmt, null);
        }
    }

    /**
     * @see org.opencms.db.I_CmsHistoryDriver#writeResource(org.opencms.db.CmsDbContext, org.opencms.file.CmsResource, java.util.List, int, long, int)
     */
    public void writeResource(
        CmsDbContext dbc,
        CmsResource resource,
        List properties,
        int publishTag,
        long publishDate,
        int maxVersions) throws CmsDataAccessException {

        Connection conn = null;
        PreparedStatement stmt = null;
        ResultSet res = null;

        String lastModifiedName = "";
        String createdName = "";
        try {
            CmsUser lastModified = m_driverManager.getUserDriver().readUser(dbc, resource.getUserLastModified());
            lastModifiedName = lastModified.getName();
            CmsUser created = m_driverManager.getUserDriver().readUser(dbc, resource.getUserCreated());
            createdName = created.getName();
        } catch (CmsDataAccessException e) {
            lastModifiedName = resource.getUserCreated().toString();
            createdName = resource.getUserLastModified().toString();
        }

        int resourceVersion = -1;
        int structureVersion = -1;
        try {
            conn = m_sqlManager.getConnection(dbc);

            stmt = m_sqlManager.getPreparedStatement(conn, CmsProject.ONLINE_PROJECT_ID, "C_RESOURCES_READ_VERSIONS");
            stmt.setString(1, resource.getStructureId().toString());
            res = stmt.executeQuery();
            if (res.next()) {
                resourceVersion = res.getInt(1);
                structureVersion = res.getInt(2);
            }
            m_sqlManager.closeAll(dbc, null, stmt, res);

            if (!internalValidateResource(dbc, resource, publishTag)) {
                // write the resource
                stmt = m_sqlManager.getPreparedStatement(conn, "C_RESOURCES_HISTORY_WRITE");
                stmt.setString(1, resource.getResourceId().toString());
                stmt.setInt(2, resource.getTypeId());
                stmt.setInt(3, resource.getFlags());
                stmt.setLong(4, publishDate);
                stmt.setString(5, resource.getUserCreated().toString());
                stmt.setLong(6, resource.getDateLastModified());
                stmt.setString(7, resource.getUserLastModified().toString());
                stmt.setInt(8, resource.getState().getState());
                stmt.setInt(9, resource.getLength());
                stmt.setLong(10, resource.getDateContent());
                stmt.setString(11, dbc.currentProject().getUuid().toString());
                stmt.setInt(12, resource.getSiblingCount());
                stmt.setInt(13, resourceVersion);
                stmt.setInt(14, publishTag);
                stmt.setString(15, createdName);
                stmt.setString(16, lastModifiedName);
                stmt.executeUpdate();

                m_sqlManager.closeAll(dbc, null, stmt, null);

                // if it is a file
                if (resource instanceof CmsFile) {
                    if (resource.getState().isDeleted()) {
                        // if deleted, first copy content from offline to online content tables
                        m_driverManager.getVfsDriver().createContent(
                            dbc,
                            CmsProject.ONLINE_PROJECT_ID,
                            resource.getResourceId(),
                            ((CmsFile)resource).getContents());
                    }
                    // check if the last version has an content entry
                    // try to update the publish tag
                    stmt = m_sqlManager.getPreparedStatement(conn, "C_HISTORY_CONTENTS_CREATE");
                    stmt.setInt(1, publishTag);
                    stmt.setInt(2, publishTag);
                    stmt.setString(3, resource.getResourceId().toString());

                    int result = stmt.executeUpdate();
                    m_sqlManager.closeAll(dbc, null, stmt, null);

                    int lastPublishTag = readMaxPublishTag(dbc, resource);
                    // if no new content entry has been written to db
                    if ((result == 0) && (lastPublishTag != 0)) {
                        // update old content entry                        
                        stmt = m_sqlManager.getPreparedStatement(conn, "C_HISTORY_CONTENTS_UPDATE");
                        stmt.setInt(1, publishTag);
                        stmt.setString(2, resource.getResourceId().toString());
                        stmt.setInt(3, lastPublishTag);

                        stmt.executeUpdate();
                        m_sqlManager.closeAll(dbc, null, stmt, null);
                    }

                    if (resource.getState().isDeleted()) {
                        // put the online content in the history
                        stmt = m_sqlManager.getPreparedStatement(conn, "C_ONLINE_CONTENTS_HISTORY");
                        stmt.setString(1, resource.getResourceId().toString());

                        stmt.executeUpdate();
                        m_sqlManager.closeAll(dbc, null, stmt, null);
                    }
                }
            }

            CmsUUID parentId = CmsUUID.getNullUUID();
            CmsFolder parent = m_driverManager.getVfsDriver().readParentFolder(
                dbc,
                CmsProject.ONLINE_PROJECT_ID,
                resource.getStructureId());
            if (parent != null) {
                parentId = parent.getStructureId();
            }
            // write the structure
            stmt = m_sqlManager.getPreparedStatement(conn, "C_STRUCTURE_HISTORY_WRITE");
            stmt.setString(1, resource.getStructureId().toString());
            stmt.setString(2, resource.getResourceId().toString());
            stmt.setString(3, resource.getRootPath());
            stmt.setInt(4, resource.getState().getState());
            stmt.setLong(5, resource.getDateReleased());
            stmt.setLong(6, resource.getDateExpired());
            stmt.setInt(7, structureVersion);
            stmt.setString(8, parentId.toString());
            stmt.setInt(9, publishTag);
            stmt.setInt(10, resource.getVersion());
            stmt.executeUpdate();

            writeProperties(dbc, resource, properties, publishTag);

            // delete old historical entries
            deleteEntries(dbc, resource.getStructureId(), resource.getResourceId(), maxVersions);
        } catch (SQLException e) {
            throw new CmsDbSqlException(Messages.get().container(
                Messages.ERR_GENERIC_SQL_1,
                CmsDbSqlException.getErrorQuery(stmt)), e);
        } finally {
            m_sqlManager.closeAll(dbc, conn, stmt, null);
        }
    }

    /**
     * Releases any allocated resources during garbage collection.<p>
     * 
     * @see java.lang.Object#finalize()
     */
    protected void finalize() throws Throwable {

        destroy();
        super.finalize();
    }

    /**
     * Returns the amount of properties for a propertydefinition.<p>
     * 
     * @param dbc the current database context
     * @param metadef the propertydefinition to test
     * @param projectId the ID of the current project
     * 
     * @return the amount of properties for a propertydefinition
     * @throws CmsDataAccessException if something goes wrong
     */
    protected int internalCountProperties(CmsDbContext dbc, CmsPropertyDefinition metadef, CmsUUID projectId)
    throws CmsDataAccessException {

        ResultSet res = null;
        PreparedStatement stmt = null;
        Connection conn = null;

        int returnValue;
        try {
            // create statement
            conn = m_sqlManager.getConnection(dbc);
            stmt = m_sqlManager.getPreparedStatement(conn, projectId, "C_PROPERTIES_READALL_COUNT");
            stmt.setString(1, metadef.getId().toString());
            res = stmt.executeQuery();

            if (res.next()) {
                returnValue = res.getInt(1);
            } else {
                throw new CmsDbConsistencyException(Messages.get().container(
                    Messages.ERR_NO_PROPERTIES_FOR_PROPERTYDEF_1,
                    metadef.getName()));
            }
        } catch (SQLException e) {
            throw new CmsDbSqlException(Messages.get().container(
                Messages.ERR_GENERIC_SQL_1,
                CmsDbSqlException.getErrorQuery(stmt)), e);
        } finally {
            m_sqlManager.closeAll(dbc, conn, stmt, res);
        }
        return returnValue;
    }

    /**
     * Creates a historical project from the given result set and resources.<p>
     * 
     * @param res the resource set
     * @param resources the hsitorical resources
     * 
     * @return the historical project
     *  
     * @throws SQLException if something goes wrong
     */
    protected CmsHistoryProject internalCreateProject(ResultSet res, List resources) throws SQLException {

        String ou = CmsOrganizationalUnit.removeLeadingSeparator(res.getString(m_sqlManager.readQuery("C_PROJECTS_PROJECT_OU_0")));
        return new CmsHistoryProject(
            res.getInt(m_sqlManager.readQuery("C_PROJECTS_PUBLISH_TAG_0")),
            new CmsUUID(res.getString(m_sqlManager.readQuery("C_PROJECTS_PROJECT_ID_0"))),
            ou + res.getString(m_sqlManager.readQuery("C_PROJECTS_PROJECT_NAME_0")),
            res.getString(m_sqlManager.readQuery("C_PROJECTS_PROJECT_DESCRIPTION_0")),
            new CmsUUID(res.getString(m_sqlManager.readQuery("C_PROJECTS_USER_ID_0"))),
            new CmsUUID(res.getString(m_sqlManager.readQuery("C_PROJECTS_GROUP_ID_0"))),
            new CmsUUID(res.getString(m_sqlManager.readQuery("C_PROJECTS_MANAGERGROUP_ID_0"))),
            res.getLong(m_sqlManager.readQuery("C_PROJECTS_DATE_CREATED_0")),
            CmsProject.CmsProjectType.valueOf(res.getInt(m_sqlManager.readQuery("C_PROJECTS_PROJECT_TYPE_0"))),
            res.getLong(m_sqlManager.readQuery("C_PROJECT_PUBLISHDATE_0")),
            new CmsUUID(res.getString(m_sqlManager.readQuery("C_PROJECT_PUBLISHED_BY_0"))),
            res.getString(m_sqlManager.readQuery("C_PROJECT_PUBLISHED_BY_NAME_0")),
            res.getString(m_sqlManager.readQuery("C_PROJECT_USER_NAME_0")),
            res.getString(m_sqlManager.readQuery("C_PROJECT_GROUP_NAME_0")),
            res.getString(m_sqlManager.readQuery("C_PROJECT_MANAGERGROUP_NAME_0")),
            resources);
    }

    /**
     * Deletes a list of historical resources identified by its history IDs.<p>
     * 
     * @param dbc the current database context
     * @param historyIds the list of history IDd (as String or {@link CmsUUID})
     * 
     * @throws CmsDataAccessException if something goes wrong
     */
    protected void internalDeleteVersion(CmsDbContext dbc, List historyIds) throws CmsDataAccessException {

        PreparedStatement stmt1 = null;
        PreparedStatement stmt2 = null;
        PreparedStatement stmt3 = null;
        PreparedStatement stmt4 = null;
        Connection conn = null;

        // first get all history ids of the entries which should be deleted
        try {
            conn = m_sqlManager.getConnection(dbc);
            // we have all the nescessary information, so we can delete the old historical entries
            stmt1 = m_sqlManager.getPreparedStatement(conn, "C_HISTORY_DELETE_STRUCTURE_BYHISTORYID");
            stmt2 = m_sqlManager.getPreparedStatement(conn, "C_HISTORY_DELETE_RESOURCES_BYHISTORYID");
            stmt3 = m_sqlManager.getPreparedStatement(conn, "C_HISTORY_DELETE_CONTENTS_BYHISTORYID");
            stmt4 = m_sqlManager.getPreparedStatement(conn, "C_HISTORY_DELETE_PROPERTIES_BYHISTORYID");
            Iterator i = historyIds.iterator();
            while (i.hasNext()) {
                String historyId = i.next().toString();
                //delete the structure              
                stmt1.setString(1, historyId);
                stmt1.addBatch();
                //delete the resource
                stmt2.setString(1, historyId);
                stmt2.addBatch();
                //delete the file
                stmt3.setString(1, historyId);
                stmt3.addBatch();
                //delete the properties
                stmt4.setString(1, historyId);
                stmt4.addBatch();
            }
            // excecute them
            stmt1.executeBatch();
            stmt2.executeBatch();
            stmt3.executeBatch();
            stmt4.executeBatch();
        } catch (SQLException e) {
            throw new CmsDbSqlException(Messages.get().container(
                Messages.ERR_GENERIC_SQL_1,
                CmsDbSqlException.getErrorQuery(stmt1)), e);
        } finally {
            m_sqlManager.closeAll(dbc, null, stmt1, null);
            m_sqlManager.closeAll(dbc, null, stmt2, null);
            m_sqlManager.closeAll(dbc, null, stmt3, null);
            m_sqlManager.closeAll(dbc, null, stmt4, null);
        }
    }

    /**
     * Tests if a history resource does exist.<p>
     * 
     * @param dbc the current database context
     * @param resource the resource to test
     * @param publishTag the publish tag of the resource to test
     * 
     * @return <code>true</code> if the resource already exists, <code>false</code> otherweise
     * 
     * @throws CmsDataAccessException if something goes wrong
     */
    protected boolean internalValidateResource(CmsDbContext dbc, CmsResource resource, int publishTag)
    throws CmsDataAccessException {

        Connection conn = null;
        PreparedStatement stmt = null;
        ResultSet res = null;
        boolean exists = false;

        try {
            conn = m_sqlManager.getConnection(dbc);
            stmt = m_sqlManager.getPreparedStatement(conn, "C_HISTORY_EXISTS_RESOURCE");
            stmt.setString(1, resource.getResourceId().toString());
            stmt.setInt(2, publishTag);
            res = stmt.executeQuery();

            exists = res.next();
        } catch (SQLException e) {
            throw new CmsDbSqlException(Messages.get().container(
                Messages.ERR_GENERIC_SQL_1,
                CmsDbSqlException.getErrorQuery(stmt)), e);
        } finally {
            m_sqlManager.closeAll(dbc, conn, stmt, res);
        }
        return exists;
    }
}
