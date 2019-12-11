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
    private String resourcePath
    private String pwd


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
        rawScript.echo("ws: ${rawScript.env.WORKSPACE}")
        rawScript.echo("pwd: ${rawScript.pwd()}")
    }

    @NonCPS
    @Override
    int getRunAroundActivityOrder() {
        return AROUND_ACTIVITY_ORDER
    }
}
