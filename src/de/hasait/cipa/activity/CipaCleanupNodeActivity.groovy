package de.hasait.cipa.activity

import com.cloudbees.groovy.cps.NonCPS
import de.hasait.cipa.Cipa
import de.hasait.cipa.PScript
import de.hasait.cipa.internal.CipaActivityWrapper
import de.hasait.cipa.resource.CipaFileResource

/**
 * Activity which cleans up all the resources from jenkins
 * workspace for every node.
 */
class CipaCleanupNodeActivity extends AbstractCipaAroundActivity implements CipaAfterActivities, Serializable {

    private Set<CipaFileResource> cleanupResources
    public static final int AROUND_ACTIVITY_ORDER = 10000

    CipaCleanupNodeActivity(Cipa cipa) {
        super(cipa)
        this.cleanupResources = new HashSet<>()
    }

    @NonCPS
    @Override
    int getRunAroundActivityOrder() {
        return AROUND_ACTIVITY_ORDER
    }

    @NonCPS
    @Override
    void afterActivityFinished(CipaActivityWrapper wrapper) {
        super.afterActivityFinished(wrapper)
        wrapper.activity.runProvides.each {
            if (it.resource instanceof CipaFileResource){
                cleanupResources.add(it.resource)
            }
        }
    }

    @NonCPS
    @Override
    void afterCipaActivities() {
        cleanupResources.each {
            script.dir(it.path){
                script.echo("Working Directory: ${script.pwd()}")
            }
        }
    }

}
