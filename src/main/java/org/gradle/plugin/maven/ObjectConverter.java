package org.gradle.plugin.maven;

import static com.google.common.collect.ImmutableMap.of;

import java.util.Map;

class ObjectConverter {

    private static final String JAVA_PLUGIN_NAME = "java";
    private static final String WAR_PLUGIN_NAME = "war";
    private static final String EAR_PLUGIN_NAME = "ear";

    private static final Map<String, String> packagingToPlugin = of(
            "jar", JAVA_PLUGIN_NAME,
            "war", WAR_PLUGIN_NAME,
            "ear", EAR_PLUGIN_NAME,
            "ejb", JAVA_PLUGIN_NAME);   // TODO: use an actual EJB plugin

    private static final Map<String, ? extends Map<String, String>> scopeToConfigurationAccordingToPackaging = of(
            "compile", of("jar", "compile", "war", "compile", "ear", "deploy", "ejb", "compile"),
            "test", of("jar", "testCompile", "war", "testCompile", "ear", "testCompile", "ejb", "testCompile"),
            "runtime", of("jar", "runtime", "war", "runtime", "ear", "earlib", "ejb", "runtime"),
            "provided", of("jar", "provided", "war", "providedCompile", "ear", "provided", "ejb", "provided"));

    public static String packaging2Plugin(String packaging) {
        return packagingToPlugin.get(packaging);
    }

    public static String scope2Configuration(String scope, String packaging) {
        return scopeToConfigurationAccordingToPackaging.get(scope).get(packaging);
    }
}
