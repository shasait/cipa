package de.hasait.cipa.activity

import com.cloudbees.groovy.cps.NonCPS
import de.hasait.cipa.Cipa
import de.hasait.cipa.internal.CipaActivityWrapper
import de.hasait.cipa.resource.CipaFileResource

/**
 * Activity which cleans up all the resources.
 */
class CipaCleanupNodeActivity extends AbstractCipaAroundActivity implements CipaAfterActivities, Serializable {

    public static final int AROUND_ACTIVITY_ORDER = 10000
    private Set<CipaFileResource> fileResources


    CipaCleanupNodeActivity(Cipa cipa) {
        super(cipa)
        fileResources = new HashSet<>()
    }

    @NonCPS
    @Override
    void runAroundActivity(CipaActivityWrapper wrapper, Closure<?> next) {
        wrapper.getActivity().getRunProvides().each {
            def resource = it.getResource()
            if (resource instanceof CipaFileResource) {
                fileResources.add(resource)
            }
        }
        super.runAroundActivity(wrapper, next)
    }

    @NonCPS
    @Override
    void afterCipaActivities() {
        fileResources.each {
            rawScript.echo("Node: ${it.node}, Path: ${it.path}\n")
        }
        String cleanupDirectory = rawScript.echo(rawScript.env.WORKSPACE)
        rawScript.dir(cleanupDirectory){
            rawScript.echo("In the directory now!!!")
            rawScript.pwd()
        }
    }

    @NonCPS
    @Override
    int getRunAroundActivityOrder() {
        return AROUND_ACTIVITY_ORDER
    }
}
