package com.github.cukespace.arquillian.asciidoctor.api.event;

import org.jboss.arquillian.config.descriptor.api.ArquillianDescriptor;

import java.io.Serializable;

public class RenderDocsEvent implements Serializable {

    private ArquillianDescriptor descriptor;

    public RenderDocsEvent(final ArquillianDescriptor descriptor) {
        this.descriptor = descriptor;
    }

    public ArquillianDescriptor getDescriptor() {
        return descriptor;
    }
}
