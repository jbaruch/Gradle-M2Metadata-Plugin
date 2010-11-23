package org.gradle.plugin.maven;

import java.util.Map;

import static com.google.common.collect.ImmutableMap.of;

/**
 * Created by IntelliJ IDEA.
 *
 * @author JBaruch
 * @since 05-Aug-2010
 */
class ObjectConverter {

    private static final String JAVA_PLUGIN_NAME = "java";

    private static final String WAR_PLUGIN_NAME = "war";

    private static final Map<String, String> packagingToPlugin = of(
            "jar", JAVA_PLUGIN_NAME,
            "war", WAR_PLUGIN_NAME);

    private static final Map<String, ? extends Map<String, String>> scopeToConfigurationAccordingToPackaging = of(
            "compile", of("jar", "compile", "war", "compile"),
            "test", of("jar", "testCompile", "war", "testCompile"),
            "runtime", of("jar", "runtime", "war", "runtime"),
            "provided", of("jar", "compile", "war", "providedCompile"));


    public static String packaging2Plugin(String packaging) {
        return packagingToPlugin.get(packaging);
    }

    public static String scope2Configuration(String scope, String packaging) {
        return scopeToConfigurationAccordingToPackaging.get(scope).get(packaging);
    }
}
