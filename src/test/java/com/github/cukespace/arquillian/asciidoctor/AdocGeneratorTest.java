package com.github.cukespace.arquillian.asciidoctor;

import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.shrinkwrap.api.Archive;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.EmptyAsset;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;

@RunWith(Arquillian.class)
public class AdocGeneratorTest {
    @Deployment(testable = false)
    public static Archive<?> app() {
        return ShrinkWrap.create(WebArchive.class, "app.war")
                .addAsWebInfResource(EmptyAsset.INSTANCE, "beans.xml");
    }

    @Test
    public void createAdoc() throws IOException {
        final File out = new File("target/adoc/test.adoc");
        out.getParentFile().mkdirs();
        try (final Writer writer = new FileWriter(out)) {
            writer.write("= test\n\nSome adoc");
        }
    }

    @Test
    public void createAdocDiagram() throws IOException {
        final File out = new File("target/adoc-diagram-source/test.adoc");
        out.getParentFile().mkdirs();
        try (final Writer writer = new FileWriter(out)) {
            writer.write(
                    "= test\n\n" +
                    "[ditaa]\n" +
                    "....\n" +
                    "                   +-------------+\n" +
                    "                   | Asciidoctor |-------+\n" +
                    "                   |   diagram   |       |\n" +
                    "                   +-------------+       | PNG out\n" +
                    "                       ^                 |\n" +
                    "                       | ditaa in        |\n" +
                    "                       |                 v\n" +
                    " +--------+   +--------+----+    /---------------\\\n" +
                    " |        | --+ Asciidoctor +--> |               |\n" +
                    " |  Text  |   +-------------+    |   Beautiful   |\n" +
                    " |Document|   |   !magic!   |    |    Output     |\n" +
                    " |     {d}|   |             |    |               |\n" +
                    " +---+----+   +-------------+    \\---------------/\n" +
                    "     :                                   ^\n" +
                    "     |          Lots of work             |\n" +
                    "     +-----------------------------------+\n" +
                    "....");
        }
    }
}
