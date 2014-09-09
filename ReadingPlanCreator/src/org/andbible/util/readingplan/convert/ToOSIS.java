package org.andbible.util.readingplan.convert;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;

import org.crosswire.jsword.passage.PassageKeyFactory;

/**
 * convert simple text to Sword Gen Book
 * @author denha1m
 *
 */
public class ToOSIS {
	
	public static void main(String[] args) {
		try {
			File[] files = new File("convert/in").listFiles();
			for (File file : files) {
				System.out.println("File:"+file);
				Properties prop = new Properties();
	 
		        InputStream inputStream = new FileInputStream(file);
		        prop.load(inputStream);
		        
		        List<String> strkeys = new ArrayList(prop.keySet());
		        List<Integer> intkeys = new ArrayList();
		        for (String strkey : strkeys) {
		        	try {
		        		intkeys.add(Integer.parseInt(strkey));
		        	} catch (Exception e) {
			        	System.out.println("Unused:"+strkey);
		        	}
		        }
		        Collections.sort(intkeys);

		        for (Integer key : intkeys) {
		        	String val = (String)prop.get(key.toString());
		        	String[] refs = val.split(",");
		        	String osisref = "";
		        	boolean isFirst = true;
		        	for (String ref : refs) {
		        		if (!isFirst) osisref += ", ";
		        		try {
		        			osisref += PassageKeyFactory.instance().getKey(ref).getOsisRef();
		        		} catch (Exception e) {
				        	System.out.println("ERROR:key="+key+","+val+":"+ref);
		        		}
		        		isFirst = false;
		        	}
		        	System.out.println(key+"="+osisref);
		        }
		        
//				Properties props = new Prope
//				String in = IOUtils.toString(new FileInputStream(file));
//				in = new RemoveLineStart().filter(in);
//				in = new RemoveEmptyLines().filter(in);
//				in = new AddDayNumbers().filter(in);
//				in = new CompressBookNames().filter(in);
//				in = in.replace(" - ", ", ").replace(" -- ", ", ");
//				IOUtils.write(in, new FileOutputStream("convert/out/"+file.getName()));
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}
