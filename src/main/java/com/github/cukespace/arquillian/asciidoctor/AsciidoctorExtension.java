package com.github.cukespace.arquillian.asciidoctor;

import org.jboss.arquillian.core.spi.LoadableExtension;

public class AsciidoctorExtension implements LoadableExtension {
    @Override
    public void register(final ExtensionBuilder builder) {
        builder.observer(AsciidoctorObserver.class);
    }
}
