This is Maven2 metadata (embedder) plugin for Gradle. It retrieves project information from pom.xml during the Gradle build and configures Gradle (at runtime) accordingly.

See the [project's wiki](https://github.com/jbaruch/Gradle-M2Metadata-Plugin/wiki) for full details.


Features:
============
* Runtime parsing of Maven pom.xml files
* Runtime configuration of Gradle dependencies via the `<dependency>` tags in the pom.xml

Limitations (To Dos):
============
* Does not map Maven plugins to the Gradle cousins.
* Requires slightly special configuration to apply to multi-modules Gradle projects.
* Requires definition of `repositories` and `dependencies`. These should possibly be defaulted by the `apply plugin: 'maven-metadata'` statement.

Recognized Contributors:
============
* [Baruch Sadogursky](http://github.com/jbaruch)
* [Matthew McCullough](http://github.com/matthewmccullough)
