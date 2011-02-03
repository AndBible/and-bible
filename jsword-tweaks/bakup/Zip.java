/**
 * Distribution License:
 * JSword is free software; you can redistribute it and/or modify it under
 * the terms of the GNU Lesser General Public License, version 2.1 as published by
 * the Free Software Foundation. This program is distributed in the hope
 * that it will be useful, but WITHOUT ANY WARRANTY; without even the
 * implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU Lesser General Public License for more details.
 *
 * The License is available on the internet at:
 *       http://www.gnu.org/copyleft/lgpl.html
 * or by writing to:
 *      Free Software Foundation, Inc.
 *      59 Temple Place - Suite 330
 *      Boston, MA 02111-1307, USA
 *
 * Copyright: 2007
 *     The copyright to this program is held by it's authors.
 *
 * ID: $Id: Zip.java 1966 2009-10-30 01:15:14Z dmsmith $
 */

package org.crosswire.common.compress;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.Inflater;
import java.util.zip.InflaterInputStream;

/**
 * A class can be Activatable if it needs a significant amount of memory on an
 * irregular basis, and so would benefit from being told when to wake-up and
 * when to conserver memory.
 * 
 * @see gnu.lgpl.License for license details.<br>
 *      The copyright to this program is held by it's authors.
 * @author DM Smith [dmsmith555 at yahoo dot com]
 */
public class Zip extends AbstractCompressor {
    /**
     * Create a Zip that is capable of transforming the input.
     * 
     * @param input
     *            to compress or uncompress.
     */
    public Zip(InputStream input) {
        super(input);
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.crosswire.common.compress.Compressor#compress()
     */
    public ByteArrayOutputStream compress() throws IOException {
        BufferedInputStream in = new BufferedInputStream(input);
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        DeflaterOutputStream out = new DeflaterOutputStream(bos, new Deflater(), BUF_SIZE);
        byte[] buf = new byte[BUF_SIZE];

        for (int count = in.read(buf); count != -1; count = in.read(buf)) {
            out.write(buf, 0, count);
        }
        in.close();
        out.flush();
        out.close();
        return bos;
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.crosswire.common.compress.Compressor#uncompress()
     */
    public ByteArrayOutputStream uncompress() throws IOException {
        return uncompress(BUF_SIZE);
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.crosswire.common.compress.Compressor#uncompress(int)
     */
    public ByteArrayOutputStream uncompress(int expectedLength) throws IOException {
    	//MJD START remove redundant BufferedOutputStream to avoid OutOfMemory errors
        ByteArrayOutputStream out = new ByteArrayOutputStream(expectedLength);
        InflaterInputStream in = new InflaterInputStream(input, new Inflater(), expectedLength);
        byte[] buf = new byte[expectedLength];

        for (int count = in.read(buf); count != -1; count = in.read(buf)) {
            out.write(buf, 0, count);
        }
        in.close();
        out.flush();
        out.close();
        return out;
    }
}
