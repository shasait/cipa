/*
 * Copyright (C) 2020 by azamafzaal (azamafzaal at gmail dot com)
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
import de.hasait.cipa.NodeCleanup
import de.hasait.cipa.resource.CipaFileResource

/**
 * Perform clean up of all file resources on each node.
 */
class CipaFileResourceCleanup extends AbstractCipaBean implements NodeCleanup {

    CipaFileResourceCleanup(Cipa cipa) {
        super(cipa)
    }

    @Override
    void cleanupNode(CipaNode node) {
        Set<CipaFileResource> allFileResources = cipa.findBeans(CipaFileResource.class)
        script.echo("Cleaning up file resources on host ${node.runtimeHostname} (${node.label})...")
        for (resource in allFileResources) {
            if (resource.node == node && resource.cleanupEnabled) {
                String path = resource.path
                script.echo('- ' + resource)
                script.dir(path) {
                    script.deleteDir()
                }
            }
        }
    }

}
