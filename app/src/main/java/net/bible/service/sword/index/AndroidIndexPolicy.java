package net.bible.service.sword.index;

import org.crosswire.jsword.index.IndexPolicyAdapter;

/**
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br>
 *      The copyright to this program is held by it's author.
 */
public class AndroidIndexPolicy extends IndexPolicyAdapter {

    @Override
    public int getRAMBufferSize() {
        return 1;
    }

    @Override
    public boolean isSerial() {
        return true;
    }
}
