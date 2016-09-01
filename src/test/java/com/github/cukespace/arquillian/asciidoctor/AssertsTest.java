package com.github.cukespace.arquillian.asciidoctor;

import org.apache.commons.io.IOUtils;
import org.junit.Test;

import java.io.File;
import java.io.FileInputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;

import static java.lang.System.lineSeparator;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class AssertsTest {
    @Test
    public void run() throws IOException {
        final File out = new File("target/adoc/test.adoc");
        assertTrue(out.isFile());

        final File rendered = new File("target/adoc-rendered");
        assertTrue(rendered.isDirectory());

        // html
        final File renderedTestHtml = new File("target/adoc-rendered/test.html");
        assertTrue(renderedTestHtml.isFile());
        try (final InputStream is = new FileInputStream(renderedTestHtml)) {
            assertEquals(
                    "<div class=\"paragraph\">\n" +
                            "<p>Some adoc</p>\n" +
                            "</div>", IOUtils.toString(is, "UTF-8").replace(lineSeparator(), "\n"));
        }

        // pdf
        final File renderedTestPdf = new File("target/adoc-rendered/test.pdf");
        assertTrue(renderedTestPdf.isFile());
        assertTrue(renderedTestPdf.length() > 10 * 1024 /*we have some content */);

        // diagram
        final File renderedDiagram = new File("target/adoc-diagram-target/test.html");
        assertTrue(renderedDiagram.isFile());
        assertEquals(1, new File(renderedDiagram.getParentFile(), "images").listFiles(new FilenameFilter() {
            @Override
            public boolean accept(final File dir, final String name) {
                return name.startsWith("diag-") && name.endsWith(".png");
            }
        }).length);
        try (final InputStream is = new FileInputStream(renderedDiagram)) {
            assertTrue(IOUtils.toString(is, "UTF-8").contains("<img src=\"images/diag-"));
        }
    }
}
