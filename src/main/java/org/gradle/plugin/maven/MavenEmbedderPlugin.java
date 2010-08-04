package org.gradle.plugin.maven;

import com.google.common.base.Function;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multimaps;
import org.apache.maven.execution.DefaultMavenExecutionRequest;
import org.apache.maven.execution.MavenExecutionRequest;
import org.apache.maven.execution.MavenExecutionRequestPopulationException;
import org.apache.maven.execution.MavenExecutionRequestPopulator;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.Repository;
import org.apache.maven.model.building.ModelBuildingRequest;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.ProjectBuilder;
import org.apache.maven.project.ProjectBuildingException;
import org.apache.maven.project.ProjectBuildingRequest;
import org.codehaus.plexus.ContainerConfiguration;
import org.codehaus.plexus.DefaultContainerConfiguration;
import org.codehaus.plexus.DefaultPlexusContainer;
import org.codehaus.plexus.PlexusContainerException;
import org.codehaus.plexus.classworlds.ClassWorld;
import org.codehaus.plexus.component.repository.exception.ComponentLookupException;
import org.gradle.api.GradleException;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.artifacts.dsl.RepositoryHandler;
import org.gradle.api.internal.artifacts.dependencies.DefaultExternalModuleDependency;

import java.io.File;
import java.util.Collection;
import java.util.List;

/**
 * Created by IntelliJ IDEA.
 *
 * @author JBaruch
 * @since 03-Aug-2010
 */
public class MavenEmbedderPlugin implements Plugin<Project> {

    private MavenProject mavenProject;
    private Project project;

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
        try {
            project.getLogger().lifecycle("Reading maven project...");
            readMavenProject();
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
    }

    private void addDependencies() {
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
            for (Dependency dependency : scopeDependencies) {
                configuration.addDependency(new DefaultExternalModuleDependency(dependency.getGroupId(), dependency.getArtifactId(), dependency.getVersion()));

            }
        }
    }

    private void readMavenProject() throws PlexusContainerException, ComponentLookupException, MavenExecutionRequestPopulationException, ProjectBuildingException {
        ContainerConfiguration dpcreq = new DefaultContainerConfiguration()
                .setClassWorld(new ClassWorld("plexus.core", this.getClass().getClassLoader()))
                .setName("mavenCore");
        DefaultPlexusContainer container = new DefaultPlexusContainer(dpcreq);
        ProjectBuilder builder = container.lookup(ProjectBuilder.class);
        MavenExecutionRequest executionRequest = new DefaultMavenExecutionRequest();
        MavenExecutionRequestPopulator populator = container.lookup(MavenExecutionRequestPopulator.class);
        populator.populateDefaults(executionRequest);
        ProjectBuildingRequest buildingRequest = executionRequest.getProjectBuildingRequest();
        buildingRequest.setValidationLevel(ModelBuildingRequest.VALIDATION_LEVEL_MINIMAL);
        mavenProject = builder.build(new File("pom.xml"), buildingRequest).getProject();
    }
}
