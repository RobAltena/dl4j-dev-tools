package org.nd4j.codegen.impl.java;

import org.nd4j.codegen.api.Language;
import org.nd4j.codegen.api.Namespace;
import org.nd4j.codegen.api.NamespaceOps;
import org.nd4j.codegen.api.Op;
import org.nd4j.codegen.api.generator.Generator;
import org.nd4j.codegen.api.generator.GeneratorConfig;

import java.io.File;
import java.io.IOException;

public class JavaPoetGenerator implements Generator {


    @Override
    public Language language() {
        return Language.JAVA;
    }

    @Override
    public void generateNamespaceNd4j(NamespaceOps namespace, GeneratorConfig config, File directory, String className) throws IOException {
        Nd4jNamespaceGenerator.generate(namespace, config, directory, className);
    }

    @Override
    public void generateNamespaceSameDiff(NamespaceOps namespace, GeneratorConfig config, File directory, String classname) throws IOException {
        throw new UnsupportedOperationException("Not yet implemented");
    }
}
