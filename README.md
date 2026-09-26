# kzork

This project uses [Gradle](https://gradle.org/).
To build and run the application, use the *Gradle* tool window by clicking the
Gradle icon in the right-hand toolbar, or run it directly from the terminal:

* Run `./gradlew run` to build and run the application.
* Run `./gradlew build` to only build the application.
* Run `./gradlew check` to run all checks, including tests.
* Run `./gradlew clean` to clean all build outputs.

Note the usage of the Gradle Wrapper (`./gradlew`).
This is the suggested way to use Gradle in production projects.

[Learn more about the Gradle Wrapper][wrapper].

[Learn more about Gradle tasks][tasks].

This project follows the suggested multi-module setup and consists of the `app`
and `utils` subprojects.
The shared build logic was extracted to a convention plugin located in
`buildSrc`.

This project uses a version catalog (see `gradle/libs.versions.toml`) to declare
and version dependencies and both a build cache and a configuration cache (see
`gradle.properties`).

[wrapper]: https://docs.gradle.org/current/userguide/gradle_wrapper.html
[tasks]: https://docs.gradle.org/current/userguide/command_line_interface.html
