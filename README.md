# cipa - Continuous Integration Pipeline Activities

## What is cipa?
Cipa is a shared library for Jenkins Pipelines.
It is useful for massive parallel pipelines to reduce cyclomatic complexity and remove boilerplate code.

Currently it is limited to Java and Maven.


## Concept
* You define nodes.
* Then you define resources (e.g. directories, db schemas) in different states (e.g. checked out, compiled for directories).
* Then you will add activities which require some resources and produce others (the logic)

Cipa uses this information to build a dependencies graph and execute the activities in correct order and parallel if they are independent.

## BeanContainer
Cipa is also a bean container, where you add your beans which implement interfaces to influence pipeline behaviour (e.g. JobParameterContribution).
This way a big pipeline can be easily split into various classes making the code more maintainable.


## Example
Please have a look into the test package.

## License
Cipa is licensed under the [Apache License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0).
