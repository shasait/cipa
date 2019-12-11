package de.hasait.cipa.activity

import com.cloudbees.groovy.cps.NonCPS
import de.hasait.cipa.Cipa

/**
 * Activity which cleans up all the resources.
 */
class CipaCleanupNodeActivity extends AbstractCipaAroundActivity implements CipaAfterActivities, Serializable {

    public static final int AROUND_ACTIVITY_ORDER = 10000

    CipaCleanupNodeActivity(Cipa cipa) {
        super(cipa)
    }

    @NonCPS
    @Override
    void afterCipaActivities() {
        script.echo("In CleanupNodeActivity...")
        script.echo("Estimating working directory")
        script.echo(script.pwd())
        script.echo("Printed working directory")
    }

    @NonCPS
    @Override
    int getRunAroundActivityOrder() {
        return AROUND_ACTIVITY_ORDER
    }
}
