package com.github.cukespace.arquillian.asciidoctor;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.asciidoctor.Asciidoctor;
import org.asciidoctor.AttributesBuilder;
import org.asciidoctor.OptionsBuilder;
import org.asciidoctor.SafeMode;
import org.asciidoctor.extension.BlockMacroProcessor;
import org.asciidoctor.extension.BlockProcessor;
import org.asciidoctor.extension.DocinfoProcessor;
import org.asciidoctor.extension.IncludeProcessor;
import org.asciidoctor.extension.InlineMacroProcessor;
import org.asciidoctor.extension.JavaExtensionRegistry;
import org.asciidoctor.extension.Postprocessor;
import org.asciidoctor.extension.Preprocessor;
import org.asciidoctor.extension.Processor;
import org.asciidoctor.extension.Treeprocessor;
import org.asciidoctor.extension.spi.ExtensionRegistry;
import org.asciidoctor.internal.JRubyRuntimeContext;
import org.jboss.arquillian.config.descriptor.api.ArquillianDescriptor;
import org.jboss.arquillian.config.descriptor.api.ExtensionDef;
import org.jboss.arquillian.core.api.Instance;
import org.jboss.arquillian.core.api.annotation.Inject;
import org.jboss.arquillian.core.api.annotation.Observes;
import org.jboss.arquillian.core.api.event.ManagerStarted;
import org.jboss.arquillian.core.api.event.ManagerStopping;
import org.jboss.arquillian.core.spi.EventContext;
import org.jruby.Ruby;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.logging.Logger;
import java.util.regex.Pattern;

import static java.util.Arrays.asList;

// fork of asciidoctor maven plugin
public class AsciidoctorObserver {
    
    private static final Pattern ASCIIDOC_EXTENSION_PATTERN = Pattern.compile("^[^_.].*\\.a((sc(iidoc)?)|d(oc)?)$");
    
    private static Asciidoctor asciidoctor;

    @Inject
    private Instance<ArquillianDescriptor> descriptorInstance;

    public void start(@Observes final EventContext<ManagerStarted> starting) {
        starting.proceed();
        final ArquillianDescriptor descriptor = descriptorInstance.get();
        Thread adocThread = new Thread(new Runnable() {

            @Override
            public void run() {
                initAsciidoctor(descriptor);
            }
        }, "asciidoctor-thread");
        adocThread.setDaemon(true);
        adocThread.start();
    }
    
    private void initAsciidoctor(ArquillianDescriptor arquillianDescriptor) {

        String gemPath = null;
        for (final ExtensionDef extensionDef : arquillianDescriptor.getExtensions()) {
            if (extensionDef.getExtensionName().startsWith("asciidoctor")) {
                gemPath = get(extensionDef.getExtensionProperties(), "gemPath", "");
                if (!gemPath.equals("")) {
                    break;
                }
            }
        }
        if (gemPath == null || "".equals(gemPath)) {
            asciidoctor = Asciidoctor.Factory.create();
        } else {
            asciidoctor = Asciidoctor.Factory.create((File.separatorChar == '\\') ? gemPath.replaceAll("\\\\", "/") : gemPath);
        }

    }

    public void stop(@Observes final EventContext<ManagerStopping> ending) {
        final ArquillianDescriptor descriptor = descriptorInstance.get();
        try {
            ending.proceed();
        } finally {
            renderAll(descriptor);
        }
    }

    private void renderAll(final ArquillianDescriptor descriptor) {
        for (final ExtensionDef extensionDef : descriptor.getExtensions()) {
            if (extensionDef.getExtensionName().startsWith("asciidoctor")) {
                render(extensionDef.getExtensionName(), extensionDef.getExtensionProperties());
            }
        }
    }

    private void render(final String name, final Map<String, String> extensionDef) {
        final String skip = extensionDef.get("skipSystemProperty");
        if (Boolean.getBoolean(skip == null ? "arquillian.asciidoctor" : skip)) { // for ide usage
            return;
        }

        // read the config
        final String sourceDirectory = extensionDef.get("sourceDirectory");
        final String sourceDocumentName = extensionDef.get("sourceDocumentName");
        final String outputDirectory = extensionDef.get("outputDirectory");
        if (sourceDirectory == null || outputDirectory == null) {
            throw new IllegalArgumentException("You need to specify sourceDirectory and outputDirectory");
        }

        final boolean preserveDirectories = Boolean.parseBoolean(extensionDef.get("preserveDirectories"));
        final boolean relativeBaseDir = Boolean.parseBoolean(extensionDef.get("relativeBaseDir"));

        final Map<String, String> attributes = new HashMap<>();
        for (final Map.Entry<String, String> entry : extensionDef.entrySet()) {
            if (entry.getKey().startsWith("attribute.")) {
                attributes.put(entry.getKey().substring("attribute.".length()), entry.getValue() == null ? "" : entry.getValue());
            }
        }

        final Collection<String> requires = new ArrayList<>();
        if (extensionDef.containsKey("requires")) {
            requires.addAll(asList(extensionDef.get("requires").split(",")));
        }

        final Collection<String> sourceDocumentExtensions = new ArrayList<>();
        if (extensionDef.containsKey("sourceDocumentExtensions")) {
            sourceDocumentExtensions.addAll(asList(extensionDef.get("sourceDocumentExtensions").split(",")));
        }

        final String baseDir = extensionDef.get("baseDir");
        final String gemPath = get(extensionDef, "gemPath", "");
        final String backend = get(extensionDef, "backend", "docbook");
        final String doctype = extensionDef.get("doctype");
        final String eruby = get(extensionDef, "eruby", "");
        final boolean headerFooter = Boolean.parseBoolean(extensionDef.get("headerFooter"));
        final boolean embedAssets = Boolean.parseBoolean(extensionDef.get("embedAssets"));
        final String templateDir = extensionDef.get("templateDir");
        final String templateEngine = extensionDef.get("templateEngine");
        final String imagesDir = get(extensionDef, "imagesDir", "images");
        final String sourceHighlighter = extensionDef.get("sourceHighlighter");
        final String attributeMissing = get(extensionDef, "attributeMissing", "skip");
        final String attributeUndefined = get(extensionDef, "attributeUndefined", "drop-line");

        // extension.<name>.class and extension.<name>.block properties
        final Collection<String> extensions = new HashSet<>();
        for (final Map.Entry<String, String> entry : extensionDef.entrySet()) {
            final String key = entry.getKey();
            if (key.startsWith("extension.")) {
                extensions.add(key.substring("extension.".length()));
            }
        }

        final Collection<String> resources = new HashSet<>();
        for (final Map.Entry<String, String> entry : extensionDef.entrySet()) {
            final String key = entry.getKey();
            if (key.startsWith("resource.")) {
                resources.add(key.substring("resource.".length()));
            }
        }

        // now start the rendering
        final File sourceDir = new File(sourceDirectory);
        if (!sourceDir.exists()) {
            getLogger().warning("No adoc to render, skipping");
            return;
        }

        final File outputDir = new File(outputDirectory);
        if (!outputDir.exists() && !outputDir.mkdirs()) {
            getLogger().severe("Can't create " + outputDirectory);
        }

         if(asciidoctor == null){
             throw new RuntimeException("Asciidoctor not initilizable properly.");
         }

        final Ruby rubyInstance = JRubyRuntimeContext.get();
        final String gemHome = rubyInstance.evalScriptlet("ENV['GEM_HOME']").toString();
        final String gemHomeExpected = (gemPath == null || "".equals(gemPath)) ? "" : gemPath.split(java.io.File.pathSeparator)[0];
        if (!"".equals(gemHome) && !gemHomeExpected.equals(gemHome)) {
            getLogger().warning("Using inherited external environment to resolve gems (" + gemHome + "), i.e. build is platform dependent!");
        }

        asciidoctor.requireLibraries(requires);

        final OptionsBuilder optionsBuilder = OptionsBuilder.options()
                .backend(backend)
                .safe(SafeMode.UNSAFE)
                .headerFooter(headerFooter)
                .eruby(eruby)
                .mkDirs(true);
        if (doctype != null) {
            optionsBuilder.docType(doctype);
        }
        if (templateEngine != null) {
            optionsBuilder.templateEngine(templateEngine);
        }
        if (templateDir != null) {
            optionsBuilder.templateDir(new File(templateDir));
        }

        final AttributesBuilder attributesBuilder = AttributesBuilder.attributes();
        if (sourceHighlighter != null) {
            attributesBuilder.sourceHighlighter(sourceHighlighter);
        }

        if (embedAssets) {
            attributesBuilder.linkCss(false);
            attributesBuilder.dataUri(true);
        }

        if (imagesDir != null) {
            attributesBuilder.imagesDir(imagesDir);
        }

        if ("skip".equals(attributeMissing) || "drop".equals(attributeMissing) || "drop-line".equals(attributeMissing)) {
            attributesBuilder.attributeMissing(attributeMissing);
        } else {
            throw new IllegalStateException(attributeMissing + " is not valid. Must be one of 'skip', 'drop' or 'drop-line'");
        }

        if ("drop".equals(attributeUndefined) || "drop-line".equals(attributeUndefined)) {
            attributesBuilder.attributeUndefined(attributeUndefined);
        } else {
            throw new IllegalStateException(attributeUndefined + " is not valid. Must be one of 'drop' or 'drop-line'");
        }

        for (final Map.Entry<String, String> attributeEntry : attributes.entrySet()) {
            String val = attributeEntry.getValue();
            if (val == null || "true".equals(val)) {
                attributesBuilder.attribute(attributeEntry.getKey(), "");
            } else if ("false".equals(val)) {
                attributesBuilder.attribute(attributeEntry.getKey(), null);
            } else {
                attributesBuilder.attribute(attributeEntry.getKey(), val);
            }
        }
        optionsBuilder.attributes(attributesBuilder);

        new AsciidoctorJExtensionRegistry(extensions, extensionDef).register(asciidoctor);

        for (final String resource : resources) {
            final File from = new File(resource);
            if (!from.exists()) {
                getLogger().warning(from + " doesn't exist");
                continue;
            }

            final File out = new File(outputDirectory, sourceDir.toPath().relativize(from.toPath()).toString());
            if (from.isFile()) {
                out.getParentFile().mkdirs();
                try (final InputStream is = new FileInputStream(from);
                     final OutputStream os = new FileOutputStream(out)) {
                    IOUtils.copy(is, os);
                } catch (final IOException e) {
                    throw new IllegalStateException(e);
                }
            } else {
                out.mkdirs();
                try {
                    FileUtils.copyDirectory(from, outputDir);
                } catch (final IOException e) {
                    throw new IllegalStateException(e);
                }
            }
        }

        if (sourceDocumentName == null) {
            try {
                Files.walkFileTree(sourceDir.toPath(), new SimpleFileVisitor<Path>() {
                    @Override
                    public FileVisitResult visitFile(final Path file, final BasicFileAttributes attrs) throws IOException {
                        final boolean match;
                        if (extensions.isEmpty()) {
                            match = ASCIIDOC_EXTENSION_PATTERN.matcher(file.getFileName().toString()).matches();
                        } else {
                            String ext = file.getFileName().toString();
                            final int dot = ext.lastIndexOf('.');
                            if (dot > 0) {
                                ext = ext.substring(dot);
                                match = ext.contains(ext);
                            } else {
                                match = false;
                            }
                        }
                        if (match) {
                            final File toFile = file.toFile();
                            setDestinationPaths(optionsBuilder, toFile, preserveDirectories, outputDir, preserveDirectories, outputDirectory, sourceDir);
                            renderFile(name, asciidoctor, optionsBuilder.asMap(), toFile);
                        }
                        return FileVisitResult.CONTINUE;
                    }

                    @Override
                    public FileVisitResult postVisitDirectory(final Path dir, final IOException exc) throws IOException {
                        if (dir.getFileName().toString().startsWith(".")) {
                            return FileVisitResult.SKIP_SUBTREE;
                        }
                        return FileVisitResult.CONTINUE;
                    }
                });
            } catch (final IOException e) {
                throw new IllegalStateException(e);
            }
        } else {
            final File sourceFile = new File(sourceDirectory, sourceDocumentName);
            setDestinationPaths(optionsBuilder, sourceFile, preserveDirectories, outputDir, relativeBaseDir, baseDir, sourceDir);
            renderFile(name, asciidoctor, optionsBuilder.asMap(), sourceFile);
        }
    }


    private void renderFile(final String name, final Asciidoctor asciidoctor, final Map<String, Object> options, final File f) {
        asciidoctor.renderFile(f, options);
        getLogger().info("Rendered " + f + " @ " + name);
    }

    private void setDestinationPaths(final OptionsBuilder optionsBuilder, final File sourceFile,
                                     final boolean preserveDirectories, final File outputDir,
                                     final boolean relativeBaseDir, final String baseDir, final File sourceDir) {
        try {
            if (baseDir != null) {
                optionsBuilder.baseDir(new File(baseDir));
            } else {
                if (relativeBaseDir) {
                    optionsBuilder.baseDir(sourceFile.getParentFile());
                } else {
                    optionsBuilder.baseDir(sourceDir);
                }
            }
            if (preserveDirectories) {
                String propostalPath = sourceFile.getParentFile().getCanonicalPath().substring(sourceDir.getCanonicalPath().length());
                File relativePath = new File(outputDir.getCanonicalPath() + propostalPath);
                optionsBuilder.toDir(relativePath).destinationDir(relativePath);
            } else {
                optionsBuilder.toDir(outputDir).destinationDir(outputDir);
            }
        } catch (final IOException e) {
            throw new IllegalStateException("Unable to locate output directory", e);
        }
    }

    private String get(final Map<String, String> extensionDef, final String key, final String val) {
        final String s = extensionDef.get(key);
        return s == null ? val : s;
    }

    private Logger getLogger() {
        return Logger.getLogger(AsciidoctorObserver.class.getName());
    }

    private static final class AsciidoctorJExtensionRegistry implements ExtensionRegistry {
        private final Collection<String> extensionNames;
        private final Map<String, String> configuration;

        private AsciidoctorJExtensionRegistry(final Collection<String> extensionNames, final Map<String, String> configuration) {
            this.extensionNames = extensionNames;
            this.configuration = configuration;
        }

        @Override
        public void register(final Asciidoctor asciidoctor) {
            final JavaExtensionRegistry registry = asciidoctor.javaExtensionRegistry();
            for (final String name : extensionNames) {
                register(registry, configuration.get("extension." + name + ".class"), configuration.get("extension." + name + ".block"));
            }
        }

        private void register(final JavaExtensionRegistry javaExtensionRegistry, final String extensionClassName, final String blockName) {
            final Class<? extends Processor> clazz;
            try {
                clazz = (Class<Processor>) Class.forName(extensionClassName);
            } catch (final ClassCastException cce) {
                throw new RuntimeException("'" + extensionClassName + "' is not a valid AsciidoctorJ processor class");
            } catch (final ClassNotFoundException e) {
                throw new RuntimeException("'" + extensionClassName + "' not found in classpath");
            }

            if (DocinfoProcessor.class.isAssignableFrom(clazz)) {
                javaExtensionRegistry.docinfoProcessor((Class<? extends DocinfoProcessor>) clazz);
            } else if (Preprocessor.class.isAssignableFrom(clazz)) {
                javaExtensionRegistry.preprocessor((Class<? extends Preprocessor>) clazz);
            } else if (Postprocessor.class.isAssignableFrom(clazz)) {
                javaExtensionRegistry.postprocessor((Class<? extends Postprocessor>) clazz);
            } else if (Treeprocessor.class.isAssignableFrom(clazz)) {
                javaExtensionRegistry.treeprocessor((Class<? extends Treeprocessor>) clazz);
            } else if (BlockProcessor.class.isAssignableFrom(clazz)) {
                javaExtensionRegistry.block(blockName, (Class<? extends BlockProcessor>) clazz);
            } else if (IncludeProcessor.class.isAssignableFrom(clazz)) {
                javaExtensionRegistry.includeProcessor((Class<? extends IncludeProcessor>) clazz);
            } else if (BlockMacroProcessor.class.isAssignableFrom(clazz)) {
                javaExtensionRegistry.blockMacro(blockName, (Class<? extends BlockMacroProcessor>) clazz);
            } else if (InlineMacroProcessor.class.isAssignableFrom(clazz)) {
                javaExtensionRegistry.inlineMacro(blockName, (Class<? extends InlineMacroProcessor>) clazz);
            }
        }
    }
}
