This is Maven2 metadata (embedder) plugin for Gradle. It retrieves project information from pom.xml during the Gradle build and configures Gradle (at runtime) accordingly.

See the [project's wiki](https://github.com/jbaruch/Gradle-M2Metadata-Plugin/wiki) for full details.


Features:
============
* Project (POM)
    * Add project version, groupId and status (SNAPSHOT/release)
    * Runtime parsing of Maven pom.xml files
* Plugins, Goals
    * Applying plugins for packagings: jar, war
    * Add source packaging if source-plugin present
    * Executes maven-clean-plugin after Gradle's Java plugin clean task
* Dependencies
    * Runtime configuration of Gradle dependencies via the `<dependency>` tags in the pom.xml
    * Dependencies in compile, provided and test scopes
    * Exclusions for dependencies
    * Turn on transitivity for compile scope
* Repositories
    * Maven repositories (both from pom.xml and profiles in settings.xml)
* Compile, Source
    * Java compiler source and target levels
    * Add source packaging if source-plugin present

Limitations (To Dos):
============
* Does not map all Maven plugins to the Gradle cousins (only a small subset).
* Requires slightly special configuration to apply to multi-modules Gradle projects.
* Requires definition of `repositories` and `dependencies` for the plugin to function. These should possibly be defaulted by the `apply plugin: 'maven-metadata'` statement.

Recognized Contributors:
============
* [Baruch Sadogursky](http://github.com/jbaruch)
* [Matthew McCullough](http://github.com/matthewmccullough)
* [David Gileadi](http://github.com/dgileadi)
