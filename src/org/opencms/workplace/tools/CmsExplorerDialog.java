/*
 * File   : $Source: /alkacon/cvs/opencms/src/org/opencms/workplace/tools/CmsExplorerDialog.java,v $
 * Date   : $Date: 2005/06/23 10:47:26 $
 * Version: $Revision: 1.5 $
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

package org.opencms.workplace.tools;

import org.opencms.jsp.CmsJspActionElement;
import org.opencms.workplace.CmsWidgetDialog;

import java.util.ArrayList;
import java.util.List;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.jsp.JspWriter;
import javax.servlet.jsp.PageContext;

/**
 * Dialog for explorer views in the administration view.<p>
 * 
 * @author Michael Moossen 
 * @version $Revision: 1.5 $
 * @since 5.7.3
 */
public class CmsExplorerDialog extends CmsWidgetDialog {

    /** List of explorer tools. */
    public static final List C_EXPLORER_TOOLS = new ArrayList();

    /**
     * Public constructor with JSP action element.<p>
     * 
     * @param jsp an initialized JSP action element
     */
    public CmsExplorerDialog(CmsJspActionElement jsp) {

        super(jsp);
    }

    /**
     * Public constructor with JSP variables.<p>
     * 
     * @param context the JSP page context
     * @param req the JSP request
     * @param res the JSP response
     */
    public CmsExplorerDialog(PageContext context, HttpServletRequest req, HttpServletResponse res) {

        this(new CmsJspActionElement(context, req, res));
    }

    /* fill the explorer tools list, do not forget to add the message bundle also */
    static {
        C_EXPLORER_TOOLS.add("/galleryoverview/downloadgallery");
        C_EXPLORER_TOOLS.add("/galleryoverview/htmlgallery");
        C_EXPLORER_TOOLS.add("/galleryoverview/imagegallery");
        C_EXPLORER_TOOLS.add("/galleryoverview/linkgallery");
        C_EXPLORER_TOOLS.add("/galleryoverview/tablegallery");
    }
    
    /**
     * @see org.opencms.workplace.CmsWidgetDialog#actionCommit()
     */
    public void actionCommit() {

        // not used
    }

    /**
     * Generates the html code for the title frame.<p>
     * 
     * @throws Exception if writing to the JSP out fails
     */
    public void displayTitle() throws Exception {

        JspWriter out = getJsp().getJspContext().getOut();
        out.println(htmlStart());
        out.println(bodyStart(null));
        out.println(dialogStart());
        out.println(dialogContentStart(getParamTitle()));
        out.println(dialogContentEnd());
        out.println(dialogEnd());
        out.println(bodyEnd());
        out.println(htmlEnd());
    }

    /**
     * @see org.opencms.workplace.CmsWidgetDialog#defineWidgets()
     */
    protected void defineWidgets() {

        // not used
    }

    /**
     * @see org.opencms.workplace.CmsWidgetDialog#getPageArray()
     */
    protected String[] getPageArray() {

        // not used
        return new String[] {"page1"};
    }

    
    /**
     * @see org.opencms.workplace.CmsWidgetDialog#initMessages()
     */
    protected void initMessages() {

        // add specific dialog resource bundle
        // for project title
        addMessages("org.opencms.workplace.tools.projects.messages");
        // for gallery overview title
        addMessages("org.opencms.workplace.workplace");
        // add default resource bundles
        super.initMessages();
    }
}
