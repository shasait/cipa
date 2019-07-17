# cipa - Continuous Integration Pipeline Activities

## What is cipa?
Cipa is a shared library for Jenkins Pipelines.
It is useful for massive parallel pipelines to reduce cyclomatic complexity and boilerplate code.

Currently it is focused to Java and Maven projects, but can be easily extended.


## Concept
* You define nodes.
* Then you define resources (e.g. directories, db schemas) in different states (e.g. checked out, compiled for directories).
* Then you will add activities which require some resources and produce others using steps

Cipa uses this information to build a dependencies graph and execute the activities in correct order and parallel if they are independent.

## BeanContainer
Cipa is also a bean container: Beans can be registered and then requested by (super-)type or interface. This allows concepts like **IoC** for pipeline development (see JobParameterContribution).
CipaAroundActivity adds **AOP**.
Applying these mature concepts to pipeline development enable you to write more maintainable code by splitting complex logic into smaller classes.

## Testability
More complex pipelines require better testing. Therefore some Test-classes are provided for unit testing without Jenkins.

## Example
Please have a look into the test package.

## License
Cipa is licensed under the [Apache License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0).
