package org.xwiki.rest.resources.classes;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.UriInfo;
import javax.ws.rs.core.Response.Status;

import org.xwiki.rest.DomainObjectFactory;
import org.xwiki.rest.XWikiResource;
import org.xwiki.rest.model.jaxb.Class;

import com.xpn.xwiki.XWikiException;

/**
 * @version $Id$
 */
@Path("/wikis/{wikiName}/classes/{className}")
public class ClassResource extends XWikiResource
{

    public ClassResource(@Context UriInfo uriInfo)
    {
        super(uriInfo);
    }

    @GET
    public Class getClass(@PathParam("wikiName") String wikiName, @PathParam("className") String className)
        throws XWikiException
    {

        String database = xwikiContext.getDatabase();

        try {
            xwikiContext.setDatabase(wikiName);

            com.xpn.xwiki.api.Class xwikiClass = xwikiApi.getClass(className);
            if(xwikiClass == null) {
                throw new WebApplicationException(Status.NOT_FOUND);
            }
                        
            return DomainObjectFactory.createClass(objectFactory, uriInfo.getBaseUri(), wikiName, xwikiClass);
        } finally {
            xwiki.setDatabase(database);
        }
    }
}
