package org.andbible.util.readingplan;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;

import org.apache.commons.io.IOUtils;

/**
 * convert simple text to Sword Gen Book
 * @author denha1m
 *
 */
public class Start {
	
	public static void main(String[] args) {
		try {
			File[] files = new File("in").listFiles();
			for (File file : files) {
				String in = IOUtils.toString(new FileInputStream(file));
				in = new RemoveLineStart().filter(in);
				in = new RemoveEmptyLines().filter(in);
				in = new AddDayNumbers().filter(in);
				in = new CompressBookNames().filter(in);
				in = in.replace(" - ", ", ").replace(" -- ", ", ");
				IOUtils.write(in, new FileOutputStream("out/"+file.getName().replace(".txt", ".properties")));
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}
