package org.gradle.plugin.maven;

import com.google.common.base.Function;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multimaps;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.execution.DefaultMavenExecutionRequest;
import org.apache.maven.execution.MavenExecutionRequest;
import org.apache.maven.execution.MavenExecutionRequestPopulationException;
import org.apache.maven.execution.MavenExecutionRequestPopulator;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.Exclusion;
import org.apache.maven.model.Repository;
import org.apache.maven.model.building.ModelBuildingRequest;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.ProjectBuilder;
import org.apache.maven.project.ProjectBuildingException;
import org.apache.maven.project.ProjectBuildingRequest;
import org.apache.maven.project.artifact.ProjectArtifact;
import org.apache.maven.settings.Settings;
import org.apache.maven.settings.building.DefaultSettingsBuildingRequest;
import org.apache.maven.settings.building.SettingsBuilder;
import org.apache.maven.settings.building.SettingsBuildingException;
import org.apache.maven.settings.building.SettingsBuildingRequest;
import org.codehaus.plexus.ContainerConfiguration;
import org.codehaus.plexus.DefaultContainerConfiguration;
import org.codehaus.plexus.DefaultPlexusContainer;
import org.codehaus.plexus.PlexusContainerException;
import org.codehaus.plexus.classworlds.ClassWorld;
import org.codehaus.plexus.component.repository.exception.ComponentLookupException;
import org.codehaus.plexus.util.cli.CommandLineUtils;
import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.gradle.api.GradleException;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.artifacts.dsl.RepositoryHandler;
import org.gradle.api.internal.artifacts.dependencies.DefaultExternalModuleDependency;
import org.gradle.api.internal.project.AbstractProject;
import org.gradle.api.plugins.JavaPluginConvention;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * Created by IntelliJ IDEA.
 *
 * @author JBaruch
 * @since 03-Aug-2010
 */
public class MavenEmbedderPlugin implements Plugin<Project> {

    private static final String MAVEN_COMPILER_PLUGIN_KEY = "org.apache.maven.plugins:maven-compiler-plugin";
    private File defaultUserSettingsFile;
    private File defaultGlobalSettingsFile;

    private MavenProject mavenProject;
    private Project project;
    private Settings settings;
    private DefaultPlexusContainer container;

    public enum Packaging {
        JAR("jar", "java"), WAR("war", "war");
        private String mavenPackaging;
        private String gradlePlugin;

        Packaging(String mavenPackaging, String gradlePlugin) {

            this.mavenPackaging = mavenPackaging;
            this.gradlePlugin = gradlePlugin;
        }

        public String getGradlePlugin() {
            return gradlePlugin;
        }

        public String getMavenPackaging() {
            return mavenPackaging;
        }

        public static Packaging parseMavenPackaging(String mavenPackaging) {
            for (Packaging packaging : Packaging.values()) {
                if (packaging.getMavenPackaging().equalsIgnoreCase(mavenPackaging)) {
                    return packaging;
                }
            }
            throw new GradleException("Failed to find packaging " + mavenPackaging);
        }
    }

    public enum ConfigurationMapping {
        COMPILE("compile", "compile"), TEST_COMPILE("test", "testCompile"), PROVIDED("provided", "compile");
        //TODO map PROVIDED to "providedCompile" for war plugin only 
        private String mavenScope;
        private String gradleConfiguration;

        ConfigurationMapping(String mavenScope, String gradleConfiguration) {
            this.mavenScope = mavenScope;
            this.gradleConfiguration = gradleConfiguration;
        }

        public String getGradleConfiguration() {
            return gradleConfiguration;
        }

        public String getMavenScope() {
            return mavenScope;
        }

        public static ConfigurationMapping parseScope(String mavenScope) {
            for (ConfigurationMapping configurationMapping : ConfigurationMapping.values()) {
                if (configurationMapping.getMavenScope().equalsIgnoreCase(mavenScope)) {
                    return configurationMapping;
                }
            }
            throw new GradleException("Failed to find scope " + mavenScope);
        }
    }

    public void apply(Project project) {
        this.project = project;
        defaultUserSettingsFile = new File(new File(System.getProperty("user.home"), ".m2"), "settings.xml");
        defaultGlobalSettingsFile = new File(System.getProperty("maven.home", System.getProperty("user.dir", "")), "conf/settings.xml");
        try {
            project.getLogger().lifecycle("Reading maven project...");
            buildContainer();
            readSettings();
            readMavenProject();
            project.getLogger().lifecycle("Configuring general settings...");
            configureSettings();
            project.getLogger().lifecycle("Applying plugins according to packaging type...");
            applyPlugins();
            project.getLogger().lifecycle("Applying Maven repositories...");
            addRepositorties();
            project.getLogger().lifecycle("Adding project dependencies...");
            addDependencies();
        } catch (Exception e) {
            throw new GradleException("failed to read Maven project", e);
        }
    }

    private void configureSettings() throws ComponentLookupException {
        AbstractProject abstractProject = (AbstractProject) project;
        abstractProject.setVersion(mavenProject.getVersion());
        abstractProject.setGroup(mavenProject.getGroupId());
        Artifact projectArtifact = new ProjectArtifact(mavenProject);
        abstractProject.setStatus(projectArtifact.isSnapshot() ? Artifact.SNAPSHOT_VERSION : Project.DEFAULT_STATUS);
    }

    private void readSettings() throws IOException, ComponentLookupException, SettingsBuildingException {
        Properties props = new Properties();
        props.putAll(System.getProperties());
        Properties envVars = CommandLineUtils.getSystemEnvVars();
        for (Map.Entry<Object, Object> objectObjectEntry : envVars.entrySet()) {
            props.setProperty("env." + objectObjectEntry.getKey().toString(), objectObjectEntry.getValue().toString());
        }
        SettingsBuildingRequest request = new DefaultSettingsBuildingRequest();
        request.setGlobalSettingsFile(defaultGlobalSettingsFile);
        request.setUserSettingsFile(defaultUserSettingsFile);
        request.setSystemProperties(props);
        this.settings = container.lookup(SettingsBuilder.class).build(request).getEffectiveSettings();
    }

    private void addRepositorties() {
        List<Repository> mavenRepositories = mavenProject.getRepositories();
        RepositoryHandler repositoryHandler = project.getRepositories();
        for (Repository mavenRepository : mavenRepositories) {
            repositoryHandler.mavenRepo(ImmutableMap.<String, String>of("name", mavenRepository.getId(), "urls", mavenRepository.getUrl()));
        }
    }

    private void applyPlugins() {
//    TODO    project.apply(ImmutableMap.<String, String>of("plugin", "maven")); - can't do it because Maven2 dependencies in gradle classloader
        Packaging packaging = Packaging.parseMavenPackaging(mavenProject.getPackaging());
        project.apply(ImmutableMap.<String, String>of("plugin", packaging.getGradlePlugin()));
        JavaPluginConvention javaConvention = (JavaPluginConvention) project.getConvention().getPlugins().get("java");
        if (javaConvention != null) {
            configureJavaCompiler(javaConvention);
        }
    }

    private void configureJavaCompiler(JavaPluginConvention javaConvention) {
        org.apache.maven.model.Plugin mavenCompilerPlugin = mavenProject.getPlugin(MAVEN_COMPILER_PLUGIN_KEY);
        Xpp3Dom configuration = (Xpp3Dom) mavenCompilerPlugin.getConfiguration();
        Xpp3Dom source = configuration.getChild("source");
        if (source != null) {
            javaConvention.setSourceCompatibility(source.getValue());

        }
        Xpp3Dom target = configuration.getChild("target");
        if (target != null) {
            javaConvention.setTargetCompatibility(target.getValue());
        }
    }

    private void addDependencies() {
        project.getConfigurations().getByName("compile").setTransitive(true);
        List<Dependency> dependencies = mavenProject.getDependencies();
        Multimap<String, Dependency> dependenciesByScope = Multimaps.index(dependencies, new Function<Dependency, String>() {
            public String apply(Dependency from) {
                return from.getScope();
            }
        });
        ConfigurationContainer configurations = project.getConfigurations();
        for (String scope : dependenciesByScope.keySet()) {
            org.gradle.api.artifacts.Configuration configuration = configurations.getByName(ConfigurationMapping.parseScope(scope).getGradleConfiguration());
            Collection<Dependency> scopeDependencies = dependenciesByScope.get(scope);
            for (Dependency mavenDependency : scopeDependencies) {
                DefaultExternalModuleDependency dependency = new DefaultExternalModuleDependency(mavenDependency.getGroupId(), mavenDependency.getArtifactId(), mavenDependency.getVersion());
                List<Exclusion> exclusions = mavenDependency.getExclusions();
                for (Exclusion exclusion : exclusions) {
                    dependency.exclude(ImmutableMap.<String, String>of("group", exclusion.getGroupId(), "module", exclusion.getArtifactId()));
                }
                configuration.addDependency(dependency);
            }
        }
    }


    private void readMavenProject() throws PlexusContainerException, ComponentLookupException, MavenExecutionRequestPopulationException, ProjectBuildingException, IOException, SettingsBuildingException {
        ProjectBuilder builder = container.lookup(ProjectBuilder.class);
        MavenExecutionRequest executionRequest = new DefaultMavenExecutionRequest();
        MavenExecutionRequestPopulator populator = container.lookup(MavenExecutionRequestPopulator.class);
        populator.populateFromSettings(executionRequest, settings);
        ProjectBuildingRequest buildingRequest = executionRequest.getProjectBuildingRequest();
        buildingRequest.setValidationLevel(ModelBuildingRequest.VALIDATION_LEVEL_MINIMAL);
        mavenProject = builder.build(new File("pom.xml"), buildingRequest).getProject();
    }

    private void buildContainer() throws PlexusContainerException {
        ContainerConfiguration dpcreq = new DefaultContainerConfiguration()
                .setClassWorld(new ClassWorld("plexus.core", this.getClass().getClassLoader()))
                .setName("mavenCore");
        container = new DefaultPlexusContainer(dpcreq);
    }
}
