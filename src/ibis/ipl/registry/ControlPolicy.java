package ibis.ipl.registry;

import ibis.ipl.Credentials;

import java.security.AccessControlException;

/**
 * @ibis.experimental
 * @author rkemp
 */
public interface ControlPolicy {

    /**
     * @ibis.experimental
     * @param authenticationObject
     * @throws AccessControlException
     */
    public void onJoin(Credentials credentials) throws AccessControlException;

}
