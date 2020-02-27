package de.hasait.cipa.activity

import de.hasait.cipa.Cipa
import de.hasait.cipa.CipaNode
import de.hasait.cipa.jobprops.JobParameterContainer
import de.hasait.cipa.jobprops.JobParameterContribution
import de.hasait.cipa.jobprops.JobParameterValues
import de.hasait.cipa.resource.CipaFileResource
import de.hasait.cipa.resource.NodeCleanup

/**
 * Performs clean up of all file resources for parallel activities with respect to each node.
 */
class CipaFileResourceCleanup extends AbstractCipaBean implements NodeCleanup, JobParameterContribution {

    static final String PARAM___CLEANUP_RESOURCES = "CLEANUP_FILE_RESOURCES"

    private boolean cleanUp

    CipaFileResourceCleanup(Cipa cipa) {
        super(cipa)
    }

    @Override
    void cleanupNode(CipaNode node) {
        script.echo("Initializing clean up of ${node.getLabel()} for host ${node.runtimeHostname}")
        if (cleanUp){
            Set<CipaFileResource> cleanupFileResources = cipa.findBeans(CipaFileResource.class)
            script.echo("Cleaning up ${cleanupFileResources.size()} file resources on node ${node.runtimeHostname}...")
            cleanupFileResources.findAll { it.node == node && it.isCleanupEnabled() }.forEach({ resource ->
                String path = resource.path
                script.echo("Perform clean up of ${resource} running on host ${node.runtimeHostname}...")
                script.dir(path) {
                    script.deleteDir()
                }
            })
        }
    }

    @Override
    void contributeParameters(JobParameterContainer container) {
        container.addBooleanParameter(PARAM___CLEANUP_RESOURCES, true, 'clean up file resources')
    }

    @Override
    void processParameters(JobParameterValues values) {
        cleanUp = values.retrieveBooleanParameterValue(PARAM___CLEANUP_RESOURCES)
    }
}