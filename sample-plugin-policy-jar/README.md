# Sample plugin policy project

This a sample project to demonstrate how to create a custom policy for Caupain.

It shows the steps needed to create a custom policy plugin:
- Create a class that implements the `Policy` interface (see [`ExamplePolicy`](src/main/java/com/example/plugin/policy/ExamplePolicy.kt))
- Create a `META-INF/services/com.deezer.caupain.model.Policy` file containing the fully qualified name of your class (see [here](src/main/resources/META-INF/services/com.deezer.caupain.model.Policy))
- Package it as a JAR file