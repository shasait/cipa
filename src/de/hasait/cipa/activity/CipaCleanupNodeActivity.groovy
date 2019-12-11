package de.hasait.cipa.activity

import com.cloudbees.groovy.cps.NonCPS
import de.hasait.cipa.Cipa

/**
 * Activity which cleans up all the resources from jenkins
 * workspace for every node.
 */
class CipaCleanupNodeActivity extends AbstractCipaBean implements CipaAfterActivities, Serializable {

    CipaCleanupNodeActivity(Cipa cipa) {
        super(cipa)
    }

    @NonCPS
    @Override
    void afterCipaActivities() {
        rawScript.echo("Jenkins workspace: ${rawScript.env.WORKSPACE}")
        rawScript.echo("Cleaning up ${rawScript.pwd()} base directory...")
        rawScript.deleteDir()
    }
}
