package de.hasait.cipa.activity

import de.hasait.cipa.Cipa
import de.hasait.cipa.internal.CipaActivityWrapper

/**
 * Activity which cleans up all the resources.
 */
class CipaCleanupNodeActivity extends AbstractCipaAroundActivity {

    public static final int AROUND_ACTIVITY_ORDER = 10000

    CipaCleanupNodeActivity(Cipa cipa) {
        super(cipa)
    }

    @Override
    int getRunAroundActivityOrder() {
        return AROUND_ACTIVITY_ORDER
    }

    @Override
    void afterActivityFinished(CipaActivityWrapper wrapper) {
        script.echo("[SALOGINFRA-7136] Clean up started...")
        script.deleteDir()
        script.echo("[SALOGINFRA-7136] Clean up ended...")
    }
}
