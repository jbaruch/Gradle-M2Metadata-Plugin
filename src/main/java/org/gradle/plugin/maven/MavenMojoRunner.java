package org.gradle.plugin.maven;

import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.PluginExecution;
import org.apache.maven.plugin.BuildPluginManager;
import org.apache.maven.plugin.Mojo;
import org.apache.maven.plugin.MojoExecution;
import org.apache.maven.plugin.descriptor.MojoDescriptor;
import org.apache.maven.plugin.descriptor.PluginDescriptor;
import org.apache.maven.plugin.descriptor.PluginDescriptorBuilder;
import org.codehaus.plexus.DefaultPlexusContainer;
import org.codehaus.plexus.classworlds.ClassWorld;
import org.codehaus.plexus.classworlds.realm.ClassRealm;
import org.codehaus.plexus.component.configurator.ComponentConfigurator;
import org.codehaus.plexus.component.repository.exception.ComponentLookupException;
import org.codehaus.plexus.configuration.PlexusConfiguration;
import org.codehaus.plexus.util.ReaderFactory;
import org.codehaus.plexus.util.StringUtils;
import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.gradle.api.Action;
import org.gradle.api.GradleException;
import org.gradle.api.Task;

import java.io.File;
import java.io.InputStream;
import java.io.Reader;
import java.net.URL;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;

/**
 * Created by IntelliJ IDEA.
 *
 * @author JBaruch
 * @since 06-Aug-2010
 */
public class MavenMojoRunner implements Action<Task> {

    private DefaultPlexusContainer container;
    private Class<? extends Mojo> mojoClass;
    private Plugin plugin;
    private PluginExecution execution;
    private String goal;
    private MavenSession session;

    public MavenMojoRunner(DefaultPlexusContainer container, Class<? extends Mojo> mojoClass, Plugin plugin, PluginExecution execution, String goal, MavenSession session) throws ComponentLookupException {
        this.container = container;
        this.mojoClass = mojoClass;
        this.plugin = plugin;
        this.execution = execution;
        this.goal = goal;
        this.session = session;
    }


    public void execute(Task task) {
        String pluginClassName = mojoClass.getName();
        String relativePluginClassFilePath = pluginClassName.replace('.', '/') + ".class";
        ClassLoader pluginClassLoader = mojoClass.getClassLoader();
        URL pluginClassFileUrl = pluginClassLoader.getResource(relativePluginClassFilePath);
        if (pluginClassFileUrl.getProtocol().equals("jar")) {
            try {
                String pluginClassFilePath = pluginClassFileUrl.getPath();
                String pluginFilePath = pluginClassFilePath.substring(pluginClassFilePath.indexOf('/') + 1, pluginClassFilePath.indexOf('!'));
                File pluginFile = new File(pluginFilePath);
                JarFile pluginJar = new JarFile(pluginFile, false);
                try {
                    ZipEntry pluginDescriptorEntry = pluginJar.getEntry("META-INF/maven/plugin.xml");
                    InputStream is = pluginJar.getInputStream(pluginDescriptorEntry);
                    Reader reader = ReaderFactory.newXmlReader(is);
                    PluginDescriptor pluginDescriptor = new PluginDescriptorBuilder().build(reader, pluginFile.getAbsolutePath());
                    pluginDescriptor.setClassRealm(new ClassRealm(new ClassWorld("maven.plugin", pluginClassLoader), "maven.plugin", pluginClassLoader));
                    MojoExecution mojoExecution = new MojoExecution(plugin, goal, execution.getId());
                    MojoDescriptor mojoDescriptor = pluginDescriptor.getMojo(goal);
                    mojoExecution.setMojoDescriptor(mojoDescriptor);
                    mojoExecution.setConfiguration(convert(mojoDescriptor));
                    Mojo mojo = mojoClass.newInstance();
                    container.addComponent(mojo, mojoDescriptor.getRole(), mojoDescriptor.getRoleHint());
                    String configuratorId = mojoDescriptor.getComponentConfigurator();
                    if (StringUtils.isEmpty(configuratorId)) {
                        configuratorId = "basic";
                    }
                    container.lookup(ComponentConfigurator.class, configuratorId);
                    container.lookup(BuildPluginManager.class).executeMojo(session, mojoExecution);
                } finally {
                    pluginJar.close();
                }
            } catch (Exception e) {
                throw new GradleException("Failed to execute Maven Mojo ", e);
            }
        }
    }

    private Xpp3Dom convert(MojoDescriptor mojoDescriptor) {
        Xpp3Dom dom = new Xpp3Dom("configuration");

        PlexusConfiguration c = mojoDescriptor.getMojoConfiguration();

        PlexusConfiguration[] ces = c.getChildren();

        if (ces != null) {
            for (PlexusConfiguration ce : ces) {
                String value = ce.getValue(null);
                String defaultValue = ce.getAttribute("default-value", null);
                if (value != null || defaultValue != null) {
                    Xpp3Dom e = new Xpp3Dom(ce.getName());
                    e.setValue(value);
                    if (defaultValue != null) {
                        e.setAttribute("default-value", defaultValue);
                    }
                    dom.addChild(e);
                }
            }
        }

        return dom;
    }

}
