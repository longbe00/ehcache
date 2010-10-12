/**
 *  Copyright 2003-2009 Terracotta, Inc.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package net.sf.ehcache.server.rest.resources;

import net.sf.ehcache.CacheManager;
import net.sf.ehcache.MimeTypeByteArray;
import net.sf.ehcache.server.jaxb.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.HEAD;
import javax.ws.rs.PUT;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.EntityTag;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Request;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;
import java.net.URI;
import java.util.Date;
import java.util.List;


/**
 * Resource representing an ehcache Element
 *
 * @author Greg Luck
 */
public class ElementResource {

    private static final Logger LOG = LoggerFactory.getLogger(ElementResource.class);

    private static final CacheManager MANAGER;

    static {
        MANAGER = CacheManager.getInstance();
    }

    /**
     * The full URI for the resource
     */
    private UriInfo uriInfo;

    /**
     * The HTTP request
     */
    private Request request;

    /**
     * The cache this resource belongs to
     */
    private String cache;

    /**
     * The element attached to this resource
     */
    private String element;

    /**
     * Full constructor
     *
     * @param uriInfo
     * @param request
     * @param cache
     * @param element
     */
    public ElementResource(UriInfo uriInfo, Request request,
                           String cache, String element) {
        this.uriInfo = uriInfo;
        this.request = request;
        this.cache = cache;
        this.element = element;
    }


    /**
     * HEAD HTTP method implementation
     *
     * @return a response which sets the headers only with no body
     */
    @HEAD
    public Response getElementHeader()  {
        LOG.debug("HEAD element {}", element);

        net.sf.ehcache.Cache ehcache = lookupCache();
        net.sf.ehcache.Element ehcacheElement = lookupElement(ehcache);
        Date lastModified = createLastModified(ehcacheElement);
        EntityTag eTag = createETag(ehcacheElement);

        Element localElement = new Element(ehcacheElement, uriInfo.getAbsolutePath().toString());

        //HEAD needs the content-length set. This is not being done by Jersey. See bug https://jersey.dev.java.net/issues/show_bug.cgi?id=91
        //So are are doing it ourselves
        String contentLength = "" + localElement.getValue().length;

        return Response.ok()
                .lastModified(lastModified)
                .tag(eTag)
                .header("Content-Length", contentLength)
                .header("Expires", (new Date(localElement.getExpirationDate())).toString())
                .build();
    }

    /**
     * Implements the GET method.
     *
     */
    @GET
    public Response getElement() {
        LOG.debug("GET element {}", element);
        net.sf.ehcache.Cache ehcache = lookupCache();
        net.sf.ehcache.Element ehcacheElement = lookupElement(ehcache);

        Element localElement = new Element(ehcacheElement, uriInfo.getAbsolutePath().toString());

        Date lastModifiedDate = createLastModified(ehcacheElement);
        EntityTag entityTag = createETag(ehcacheElement);


        Response.ResponseBuilder responseBuilder = request.evaluatePreconditions(lastModifiedDate, entityTag);
        //returns 304 if preconditions are met
        if (responseBuilder != null) {
            return responseBuilder.build();
        } else {
            //return the data?
            return Response.ok(localElement.getValue(), localElement.getMimeType())
                    .lastModified(lastModifiedDate)
                    .tag(entityTag)
                    .header("Expires", (new Date(localElement.getExpirationDate())).toString())
                    .build();
        }
    }


    /**
     * Implements the PUT method
     *
     * @param headers this method parses the mime type header and also ehcacheTimeToLiveSeconds, which is used
     *                to override the cache's default time to live.
     * @param data    the data is stored in a MimeTypeByteArray
     * @return 201 for a successful PUT
     */
    @PUT
    public Response putElement(@Context HttpHeaders headers, byte[] data) {
        LOG.debug("PUT element {}" + element);

        net.sf.ehcache.Cache ehcache = lookupCache();

        URI uri = uriInfo.getAbsolutePath();

        Integer ehcacheTimeToLiveSeconds = extractEhcacheTimeToLiveSeconds(headers);

        MediaType mimeType = headers.getMediaType();
        String mimeTypeString;
        if (mimeType == null) {
            mimeTypeString = "application/octet-stream";
        } else {
            mimeTypeString = mimeType.toString();
        }

        Element localElement = new Element(data, uri.toString(), mimeTypeString);

        Response response;

        if (cache != null) {
            response = Response.created(uri).build();
        } else {
            response = Response.noContent().build();
        }

        MimeTypeByteArray mimeTypeByteArray = new MimeTypeByteArray(localElement.getMimeType(), data);

        net.sf.ehcache.Element newElement = new net.sf.ehcache.Element(this.element, mimeTypeByteArray);
        if (ehcacheTimeToLiveSeconds != null) {
            newElement.setTimeToLive(ehcacheTimeToLiveSeconds);
        }
        ehcache.put(newElement);


        // Create the cache if one has not been created
//            URI cacheUri = uriInfo.getAbsolutePathBuilder().path("..").build().normalize();
        return response;
    }

    private Integer extractEhcacheTimeToLiveSeconds(HttpHeaders headers) {
        Integer ehcacheTimeToLiveSeconds = null;
        List<String> ttlHeaders = headers.getRequestHeader("ehcacheTimeToLiveSeconds");
        if (ttlHeaders != null && !ttlHeaders.isEmpty()) {
            String ehcacheTimeToLiveSecondsString = ttlHeaders.get(0);
            try {
                ehcacheTimeToLiveSeconds = Integer.parseInt(ehcacheTimeToLiveSecondsString);
            } catch (NumberFormatException e) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Cannot parse " + ehcacheTimeToLiveSecondsString);
                }
            }
        }
        return ehcacheTimeToLiveSeconds;
    }

    /**
     * Implements the DELETE RESTful operation
     * <p/>
     * If the element resource is specified as "*", then <code>cache.removeAll()</code> is called.
     *
     * @throws javax.ws.rs.WebApplicationException if element did not exist
     */
    @DELETE
    public void deleteElement() throws WebApplicationException {
        LOG.debug("DELETE element {}", element);
        net.sf.ehcache.Cache ehcache = lookupCache();

        if (element.equals("*")) {
            ehcache.removeAll();
        } else {
            boolean removed = ehcache.remove(element);
            if (!removed) {
                throw new WebApplicationException(Response.Status.NOT_FOUND);
            }
        }
    }

    /**
     * Each time an element is put into ehcache the creation time is set even if it is an update.
     * So, "creation time" means Last-Modified.
     *
     * @param ehcacheElement the underlying Ehcache element
     * @return the last modified date. If this is the first version of the element, the last-modified means the name things as created.
     *         This date is accurate to ms, however the HTTP protocol is not - it only goes down to seconds. Jersey removes the ms.
     */
    private Date createLastModified(net.sf.ehcache.Element ehcacheElement) {
        long lastModified = ehcacheElement.getCreationTime();
        Date lastModifiedDate = new Date(lastModified);
        LOG.debug("lastModified as long: {}", lastModified);
        LOG.debug("lastModified as Date without ms: {}", lastModifiedDate);
        return lastModifiedDate;
    }

    /**
     * A very performant ETag implementation.
     * This will be unique across JVM restarts, or deleting an element and putting one back in.
     *
     * @param ehcacheElement A backing ehcache element
     * @return the ETag for this entry
     * @see <a href="http://www.w3.org/Protocols/rfc2616/rfc2616-sec3.html#sec3.11">HTTP/1.1 section 3.11</a>
     */
    private EntityTag createETag(net.sf.ehcache.Element ehcacheElement) {

        //For a given key and server this is unique, unless two updates for that key happened in the same millisecond.
        long eTagNumber = ehcacheElement.getCreationTime();

        return new EntityTag(new StringBuffer().append(eTagNumber).toString());
    }

    /**
     * Looks up the element
     *
     * @param ehcache A cache to be checked for the key
     * @return An ehcache element. This method will not return null.
     */
    private net.sf.ehcache.Element lookupElement(net.sf.ehcache.Cache ehcache) throws WebApplicationException {
        net.sf.ehcache.Element ehcacheElement = ehcache.get(element);
        if (ehcacheElement == null) {
            throw new WebApplicationException(Response.Status.NOT_FOUND);
        }
        return ehcacheElement;
    }

    /**
     * Looks up the cache in the instance field <code>cache</code>
     *
     * @return An ehcache element. This method will not return null.
     */
    private net.sf.ehcache.Cache lookupCache() throws WebApplicationException {
        net.sf.ehcache.Cache ehcache = MANAGER.getCache(cache);
        if (ehcache == null) {
            throw new WebApplicationException(Response.Status.NOT_FOUND);
        }
        return ehcache;
    }


}
