package net.bible.service.common;

import android.content.res.AssetManager;
import android.content.res.Resources;

import net.bible.android.BibleApplication;

import org.apache.commons.lang3.StringUtils;
import org.crosswire.common.util.IOUtil;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.FileChannel;
import java.util.Properties;

/**
 * File related utility methods
 * 
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br>
 *      The copyright to this program is held by it's author.
 */
public class FileManager {

	private static final String DOT_PROPERTIES = ".properties";

	private static final Logger log = new Logger(FileManager.class.getName());

	public static boolean copyFile(String filename, File fromDir, File toDir) {
		log.debug("Copying:"+filename);
		boolean ok;

        File fromFile = new File(fromDir, filename);
        File targetFile = new File(toDir, filename);

        ok = copyFile(fromFile, targetFile);

        return ok;
	}
	
	public static boolean copyFile(File fromFile, File toFile) {
		boolean ok = false;
		try {
	        // don't worry if tofile exists, allow overwrite
	        if (fromFile.exists()) {
	        	//ensure the target dir exists or FileNotFoundException is thrown creating dst FileChannel
	        	File toDir = toFile.getParentFile();
	        	toDir.mkdir();

	        	long fromFileSize = fromFile.length();
	        	log.debug("Source file length:"+fromFileSize);
	        	if (fromFileSize > CommonUtils.getFreeSpace(toDir.getPath())) {
	        		// not enough room on SDcard
	        		ok = false;            		
	        	} else {
	            	// move the file
	        		FileInputStream srcStream = new FileInputStream(fromFile);
	                FileChannel src = srcStream.getChannel();
	                FileOutputStream dstStream = new FileOutputStream(toFile);
	                FileChannel dst = dstStream.getChannel();
	            	try {
	            		dst.transferFrom(src, 0, src.size());
		                ok = true;
	            	} finally {
		                src.close();
		                dst.close();
		                srcStream.close();
		                dstStream.close();
	            	}
	        	}
	        } else {
	        	// fromfile does not exist
	        	ok = false;
	        }
	    } catch (Exception e) {
	    	log.error("Error moving file to sd card", e);
	    }
		return ok;
	}
	
	/* Open a properties file from the assets folder
	 */
	public static Properties readPropertiesFile(String folder, String filename) {
	    Properties returnProperties = new Properties();

		Resources resources = BibleApplication.getApplication().getResources();
		AssetManager assetManager = resources.getAssets();
		if (!filename.endsWith(DOT_PROPERTIES)) {
			filename = filename+DOT_PROPERTIES;
		}
		if (StringUtils.isNotEmpty(folder)) {
			filename = folder+File.separator+filename;
		}

		// Read from the /assets directory
	    InputStream inputStream = null;
		try {
			// check to see if a user has created his own reading plan with this name
	    	inputStream = assetManager.open(filename);

	    	returnProperties.load(inputStream);
		    log.debug("The properties are now loaded from: " + filename);
		} catch (IOException e) {
		    System.err.println("Failed to open property file:"+filename);
		    e.printStackTrace();
		} finally {
			IOUtil.close(inputStream);
		}
		return returnProperties;
	}
}
