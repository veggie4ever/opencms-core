/*
 * File   : $Source: /alkacon/cvs/opencms/src/org/opencms/xml/CmsXmlErrorHandler.java,v $
 * Date   : $Date: 2005/06/23 10:47:19 $
 * Version: $Revision: 1.6 $
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
 * For further information about Alkacon Software, please see the
 * company website: http://www.alkacon.com
 *
 * For further information about OpenCms, please see the
 * project website: http://www.opencms.org
 * 
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */

package org.opencms.xml;

import org.opencms.main.CmsLog;

import org.apache.commons.logging.Log;

import org.xml.sax.ErrorHandler;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

/**
 * Error hander for writing errors found during XML validation to the OpenCms log.<p>
 * 
 * Exceptions caused by warnings are suppressed (but written to the log if level is set to WARN).<p>
 * 
 * @author Michael Emmerich 
 * 
 * @version $Revision: 1.6 $ 
 * 
 * @since 6.0.0 
 */
public class CmsXmlErrorHandler implements ErrorHandler {

    /** The log object for this class. */
    private static final Log LOG = CmsLog.getLog(CmsXmlErrorHandler.class);

    /**
     * @see org.xml.sax.ErrorHandler#error(org.xml.sax.SAXParseException)
     */
    public void error(SAXParseException exception) throws SAXException {

        LOG.error(Messages.get().key(Messages.LOG_PARSING_XML_RESOURCE_ERROR_0), exception);
        throw exception;
    }

    /**
     * @see org.xml.sax.ErrorHandler#fatalError(org.xml.sax.SAXParseException)
     */
    public void fatalError(SAXParseException exception) throws SAXException {

        LOG.error(Messages.get().key(Messages.LOG_PARSING_XML_RESOURCE_FATAL_ERROR_0), exception);
        throw exception;
    }

    /**
     * @see org.xml.sax.ErrorHandler#warning(org.xml.sax.SAXParseException)
     */
    public void warning(SAXParseException exception) {

        if (LOG.isWarnEnabled()) {
            LOG.error(Messages.get().key(Messages.LOG_PARSING_XML_RESOURCE_WARNING_0), exception);
        }
    }
}
