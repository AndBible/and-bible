package org.andbible.util.readingplan;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;

import org.apache.commons.io.IOUtils;

/**
 * convert simple text to Sword Gen Book
 * @author denha1m
 *
 */
public class Start {
	
	public static void main(String[] args) {
		try {
			oneFile();
//			allFiles();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	protected static void oneFile() throws IOException, FileNotFoundException {
		File file = new File("in", "y2ot1ntps2.txt");
		String in = IOUtils.toString(new FileInputStream(file));
		in = new RemoveLineStart().filter(in);
		in = new RemoveEmptyLines().filter(in);
		in = new ConvertToOSISRefs().filter(in);
		in = new AddDayNumbers().filter(in);
//		in = new CompressBookNames().filter(in);
//		in = in.replace(" - ", ", ").replace(" -- ", ", ");
		IOUtils.write(in, new FileOutputStream("out/"+file.getName().replace(".txt", ".properties")));
	}

	protected static void allFiles() throws IOException, FileNotFoundException {
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
	}
}
