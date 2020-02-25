package de.hasait.cipa.resource

import de.hasait.cipa.CipaNode

/**
 * An interface to for performing clean up of resources.
 */
interface CipaResourceCleanup {

    void performCleanup(CipaNode node)

}