package org.crosswire.jsword.book.install.sword;

import org.crosswire.jsword.JSOtherMsg;
import org.crosswire.jsword.book.install.Installer;

/** 
 * Allow AB specific installer to cope with AB specific index download
 * 
 * All the methods below are required just to create an AndBibleHttpInstaller with a custom downloadSearchIndex() rather than an HttpInstaller
 */
public class AndBibleHttpSwordInstallerFactory extends HttpSwordInstallerFactory {
    /*
     * (non-Javadoc)
     * 
     * @see org.crosswire.jsword.book.install.InstallerFactory#createInstaller()
     */
    public Installer createInstaller() {
        return new AndBibleHttpSwordInstaller();
    }
    
    /*
     * (non-Javadoc)
     * 
     * @see
     * org.crosswire.jsword.book.install.InstallerFactory#createInstaller(java
     * .lang.String)
     */
    public Installer createInstaller(String installerDefinition) {
        String[] parts = installerDefinition.split(",", 6);
        if (parts.length==6) {
            return createInstaller(parts);
        } else {
            throw new IllegalArgumentException(JSOtherMsg.lookupText("Not enough / symbols in url: {0}", installerDefinition));
        }

    }

    private Installer createInstaller(String[] parts) {
        AbstractSwordInstaller reply = new AndBibleHttpSwordInstaller();

        reply.setHost(parts[0]);
        reply.setPackageDirectory(parts[1]);
        reply.setCatalogDirectory(parts[2]);
        if (parts[3].length() > 0) {
            reply.setProxyHost(parts[3]);
            if (parts[4].length() > 0) {
                reply.setProxyPort(Integer.valueOf(parts[4]));
            }
        }

        return reply;
    }

}
