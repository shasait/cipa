/*
 * Copyright (C) 2017 by Sebastian Hasait (sebastian at hasait dot de)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package de.hasait.cipa.activity

import de.hasait.cipa.Cipa
import de.hasait.cipa.CipaNode
import de.hasait.cipa.resource.CipaFileResource
import de.hasait.cipa.NodeCleanup

/**
 * Performs clean up of all file resources for parallel activities with respect to each node.
 */
class CipaFileResourceCleanup extends AbstractCipaBean implements NodeCleanup {

    CipaFileResourceCleanup(Cipa cipa) {
        super(cipa)
    }

    @Override
    void cleanupNode(CipaNode node) {
        script.echo("Initializing clean up of ${node.getLabel()} for host ${node.runtimeHostname}")
        Set<CipaFileResource> cleanupFileResources = cipa.findBeans(CipaFileResource.class)
        script.echo("Cleaning up file resources on node ${node.runtimeHostname}...")
        cleanupFileResources.findAll { it.node == node && it.cleanupEnabled }.forEach({ resource ->
            String path = resource.path
            script.echo("Perform clean up of ${resource} running on host ${node.runtimeHostname}...")
            script.dir(path) {
                script.deleteDir()
            }
        })
    }
}