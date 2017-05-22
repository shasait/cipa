# cipa - Continuous Integration Pipeline Activities

## What is cipa?
Cipa is a shared library for Jenkins Pipelines.
It is useful for massive parallel pipelines to reduce cyclomatic complexity and remove boilerplate code.

Currently it is limited to Java and Maven.


## Example Jenkinsfile


    @Library('cipa@1.1') _
    
    def cipa = new de.hasait.cipa.Cipa(this)
    cipa.setJdkVersion('JDK8')
    cipa.setMvnVersion('M3')
    cipa.setMvnSettingsFileId('ciserver-settings.xml')
    cipa.setMvnToolchainsFileId('ciserver-toolchains.xml')
    cipa.setMvnOptions('-Xms1g -Xmx4g')
    
    // Declare two nodes by label...
    def node1a = cipa.newNode('node1')
    def node2a = cipa.newNode('node2')
    
    // Declare some activities and their dependencies
    
    def cleanUpActivity = cipa.newActivity(node1a, 'CleanUp') {
        echo 'Cleaning up...'
        sleep 10
    }
    
    
    
    def checkOutActivity = cipa.newActivity(node1a, 'Check out') {
        echo 'Checking out...'
        sleep 10
    }
    checkOutActivity.addDependency(cleanUpActivity)
    
    
    
    def compileActivity = cipa.newActivity(node2a, 'Compile') {
        echo 'Compiling...'
        sleep 10
    }
    compileActivity.addDependency(checkOutActivity)
    
    
    
    def createDbSchema1Activity = cipa.newActivity(node2a, 'Create DB schema 1') {
        echo 'Creating DB schema 1...'
        sleep 10
    }
    createDbSchema1Activity.addDependency(cleanUpActivity)
    
    
    // Execute all activities
    cipa.runActivities()

This will first execute cleanUpActivity.
As soon as it is finished checkOutActivity and createDbSchema1Activity can start.
After checkOutActivity is finished compileActivity can start and is the last activity.


## Ideas / Plans

* Make tool preparation on nodes more flexible
    * Allow global tools and per node tools
* Implicit dependencies by declaring produced resources and used ones.
    * E.g. folder and automatic stash/unstash


## License
Genesis is licensed under the [Apache License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0).
