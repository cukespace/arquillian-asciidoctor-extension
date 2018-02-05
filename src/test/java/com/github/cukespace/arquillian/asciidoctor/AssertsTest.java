package com.github.cukespace.arquillian.asciidoctor;

import org.junit.Test;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.contentOf;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class AssertsTest {


    @Test
    public void shouldRenderTestAdoc() throws IOException {
        final File out = new File("target/adoc/test.adoc");
        assertTrue(out.isFile());

        final File rendered = new File("target/adoc-rendered");
        assertTrue(rendered.isDirectory());

        final File renderedTestHtml = new File("target/adoc-rendered/test.html");
        assertTrue(renderedTestHtml.isFile());

        assertThat(contentOf(renderedTestHtml)).
                isNotEmpty().
                startsWith("<div class=\"paragraph\">").
                hasLineCount(3);
    }

    @Test
    public void shouldRenderPdf() {
        final File renderedTestPdf = new File("target/adoc-rendered/test.pdf");
        assertTrue(renderedTestPdf.isFile());
        assertTrue(renderedTestPdf.length() > 5 * 1024 /*we have some content */);
    }

    @Test
    public void shouldRenderDiagram(){
        final File renderedDiagram = new File("target/adoc-diagram-target/test.html");
        assertTrue(renderedDiagram.isFile());
        assertEquals(1, new File(renderedDiagram.getParentFile(), "images").listFiles(new FilenameFilter() {
            @Override
            public boolean accept(final File dir, final String name) {
                return name.startsWith("diag-") && name.endsWith(".png");
            }
        }).length);
        assertThat(contentOf(renderedDiagram)).contains("<img src=\"images/diag-");
    }

    @Test
    public void shouldRenderArticle(){
        final File renderedArticle = new File("target/adoc-rendered/article.html");
        assertThat(renderedArticle).exists();
        assertThat(contentOf(renderedArticle)).
        //toc right
        contains("<body class=\"article toc2 toc-right\">\n" +
                "<div id=\"header\">\n" +
                "<h1>Document Title</h1>").
        contains("<div id=\"toc\" class=\"toc2\">").
        //section
        contains("<a href=\"#_section_a\">Section A</a>").
        //subsection
        contains("<a href=\"#_section_a_subsection\">Section A Subsection</a>");
    }


    @Test
    public void shouldRenderArticleWithHighlights(){
        final File renderedArticle = new File("target/adoc-rendered-highlights/article.html");
        assertThat(renderedArticle).exists();
        assertThat(contentOf(renderedArticle)).
         //toc right
         contains("<body class=\"article toc2 toc-left\">").
         //source-highlighter
         contains("<pre class=\"highlightjs highlight\"><code class=\"language-java hljs\" data-lang=\"java\">");
    }
}
