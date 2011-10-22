package net.bible.service.sword;

/*This program is free software: you can redistribute it and/or modify 
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program.  If not, see <http://www.gnu.org/licenses/>.

Author: Asterios Katsifodimos (http://www.asteriosk.gr)

http://blog.asteriosk.gr/2009/03/31/merging-multiple-lucene-indexes/
*/
import java.io.File;
import java.io.IOException;
import java.util.Date;

import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;

public class IndexMerger {

	/** Index all text files under a directory. */
	public static void main(String[] args) {

		if(args.length != 2){
			System.out.println("Usage: java -jar IndexMerger.jar " +
					           "existing_indexes_dir merged_index_dir");
			System.out.println(" existing_indexes_dir: A directory where the " +
					             "indexes that have to merged exist");
			System.out.println("   e.g. indexes/");
			System.out.println("   e.g.         index1");
			System.out.println("   e.g.         index2");
			System.out.println("   e.g.         index3");
			System.out.println(" merged_index_dir: A directory where the merged " +
					                               "index will be stored");
			System.out.println("   e.g. merged_indexes");
			System.exit(1);
		}

		File INDEXES_DIR  = new File(args[0]);
		File INDEX_DIR    = new File(args[1]);

		INDEX_DIR.mkdir();

		Date start = new Date();

		try {
			IndexWriter writer = new IndexWriter(INDEX_DIR,
												new StandardAnalyzer(),
												true);
			writer.setMergeFactor(1000);
			writer.setRAMBufferSizeMB(50);

			Directory indexes[] = new Directory[INDEXES_DIR.list().length];

			for (int i = 0; i < INDEXES_DIR.list().length; i++) {
				System.out.println("Adding: " + INDEXES_DIR.list()[i]);
				indexes[i] = FSDirectory.getDirectory(INDEXES_DIR.getAbsolutePath()
													+ "/" + INDEXES_DIR.list()[i]);
			}

			System.out.print("Merging added indexes...");
			writer.addIndexes(indexes);
			System.out.println("done");

			System.out.print("Optimizing index...");
			writer.optimize();
			writer.close();
			System.out.println("done");

			Date end = new Date();
			System.out.println("It took: "+((end.getTime() - start.getTime()) / 1000)
											+ "\"");

		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}