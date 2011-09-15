/*
 * See the NOTICE file distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package com.xpn.xwiki.internal.display.scripting;

import java.lang.reflect.Field;

import javax.inject.Inject;
import javax.inject.Named;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xwiki.bridge.DocumentModelBridge;
import org.xwiki.component.annotation.Component;
import org.xwiki.component.manager.ComponentManager;
import org.xwiki.context.Execution;
import org.xwiki.display.internal.DocumentDisplayer;
import org.xwiki.display.internal.DocumentDisplayerParameters;
import org.xwiki.rendering.block.XDOM;
import org.xwiki.rendering.renderer.BlockRenderer;
import org.xwiki.rendering.renderer.printer.DefaultWikiPrinter;
import org.xwiki.rendering.renderer.printer.WikiPrinter;
import org.xwiki.rendering.syntax.Syntax;
import org.xwiki.script.service.ScriptService;

import com.xpn.xwiki.XWikiContext;
import com.xpn.xwiki.XWikiException;
import com.xpn.xwiki.api.Document;
import com.xpn.xwiki.internal.cache.rendering.RenderingCache;

/**
 * Exposes {@link org.xwiki.display.internal.Displayer}s to scripts.
 * 
 * @version $Id$
 * @since 3.2M3
 */
@Component
@Named("display")
public class DisplayScriptService implements ScriptService
{
    /** Logging helper object. */
    private static final Logger LOGGER = LoggerFactory.getLogger(DisplayScriptService.class);

    /**
     * The component used to display documents.
     */
    @Inject
    @Named("configured")
    private DocumentDisplayer documentDisplayer;

    /**
     * The component manager.
     */
    @Inject
    private ComponentManager componentManager;

    /**
     * The rendering cache.
     */
    @Inject
    private RenderingCache renderingCache;

    /**
     * Execution context handler, needed for accessing the XWikiContext.
     */
    @Inject
    private Execution execution;

    /**
     * Displays a document.
     * 
     * @param document the document to display
     * @param parameters the display parameters
     * @param outputSyntax the output syntax
     * @return the result of displaying the given document
     */
    public String document(Document document, DocumentDisplayerParameters parameters, Syntax outputSyntax)
    {
        try {
            return renderXDOM(documentDisplayer.display(getDocument(document), parameters), outputSyntax);
        } catch (Exception e) {
            LOGGER.warn(String.format("Failed to display document [%s]", document.getPrefixedFullName()), e);
            return null;
        }
    }

    /**
     * @return a new instance of {@link DocumentDisplayerParameters}
     */
    public DocumentDisplayerParameters createDocumentDisplayerParameters()
    {
        return new DocumentDisplayerParameters();
    }

    /**
     * @param document the document whose content is displayed
     * @return the result of rendering the content of the given document as XHTML in the context of the current document
     * @see #content(Document, Syntax)
     */
    public String content(Document document)
    {
        return content(document, Syntax.XHTML_1_0);
    }

    /**
     * Displays the content of the given document in the context of the current document.
     * 
     * @param document the document whose content is displayed
     * @param outputSyntax the output syntax
     * @return the result of rendering the content of the given document in the context of the current document
     */
    public String content(Document document, Syntax outputSyntax)
    {
        XWikiContext context = getXWikiContext();
        String content = null;
        try {
            content = document.getTranslatedContent();
        } catch (XWikiException e) {
            String format = "Failed to get the translated content of document [%s]";
            LOGGER.warn(String.format(format, document.getPrefixedFullName()), e);
            return null;
        }
        String renderedContent = renderingCache.getRenderedContent(document.getDocumentReference(), content, context);
        if (renderedContent == null) {
            DocumentDisplayerParameters parameters = new DocumentDisplayerParameters();
            parameters.setTransformationContextIsolated(true);
            parameters.setContentTranslated(true);
            renderedContent = document(document, parameters, outputSyntax);
            if (renderedContent != null) {
                renderingCache.setRenderedContent(document.getDocumentReference(), content, renderedContent, context);
            }
        }
        return renderedContent;
    }

    /**
     * Displays the document title. If a title has not been provided through the title field, it looks for a section
     * title in the document's content and if not found return the page name. The returned title is also interpreted
     * which means it's allowed to use Velocity, Groovy, etc. syntax within a title.
     * 
     * @param document the document whose title is displayed
     * @param outputSyntax the output syntax
     * @return the result of rendering the title of the given document in the specified syntax
     */
    public String title(Document document, Syntax outputSyntax)
    {
        DocumentDisplayerParameters parameters = new DocumentDisplayerParameters();
        parameters.setTitleDisplayed(true);
        parameters.setExecutionContextIsolated(true);
        return document(document, parameters, outputSyntax);
    }

    /**
     * @param document the document whose title is displayed
     * @return the result of rendering the title of the given document as XHTML
     * @see #title(Document, Syntax)
     */
    public String title(Document document)
    {
        return title(document, Syntax.XHTML_1_0);
    }

    /**
     * @param document the document whose title is displayed
     * @return the result of rendering the title of the given document as plain text (all mark-up removed)
     * @see #title(Document, Syntax)
     */
    public String plainTitle(Document document)
    {
        return title(document, Syntax.PLAIN_1_0);
    }

    /**
     * Note: This method accesses the low level XWiki document through reflection in order to bypass programming rights.
     * 
     * @param document an instance of {@link Document} received from a script
     * @return an instance of {@link DocumentModelBridge} that wraps the low level document object exposed by the given
     *         document API
     */
    private DocumentModelBridge getDocument(Document document)
    {
        try {
            // HACK: We try to access the XWikiDocument instance wrapped by the document API using reflection because we
            // want to bypass the programming rights requirements.
            Field docField = Document.class.getDeclaredField("doc");
            docField.setAccessible(true);
            return (DocumentModelBridge) docField.get(document);
        } catch (Exception e) {
            throw new RuntimeException("Failed to access the XWikiDocument instance wrapped by the document API.", e);
        }
    }

    /**
     * Renders the provided XDOM.
     * 
     * @param content the XDOM content to render
     * @param targetSyntax the syntax of the rendering result
     * @return the result of rendering the given XDOM
     * @throws XWikiException if an exception occurred during the rendering process
     */
    private String renderXDOM(XDOM content, Syntax targetSyntax) throws XWikiException
    {
        try {
            BlockRenderer renderer = componentManager.lookup(BlockRenderer.class, targetSyntax.toIdString());
            WikiPrinter printer = new DefaultWikiPrinter();
            renderer.render(content, printer);
            return printer.toString();
        } catch (Exception e) {
            throw new XWikiException(XWikiException.MODULE_XWIKI_RENDERING, XWikiException.ERROR_XWIKI_UNKNOWN,
                "Failed to render XDOM to syntax [" + targetSyntax + "]", e);
        }
    }

    /**
     * @return the XWiki context
     * @deprecated avoid using this method; try using the document access bridge instead
     */
    private XWikiContext getXWikiContext()
    {
        return (XWikiContext) execution.getContext().getProperty("xwikicontext");
    }
}
