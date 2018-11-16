package net.bible.android.control.versification;

import net.bible.android.control.page.ChapterVerse;

import org.crosswire.jsword.passage.Verse;
import org.crosswire.jsword.versification.BibleBook;
import org.crosswire.jsword.versification.Versification;
import org.crosswire.jsword.versification.system.Versifications;
import org.json.JSONException;
import org.json.JSONObject;

/** Store a main verse and return it in requested versification after mapping (if map available)
 * 
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br>
 *      The copyright to this program is held by it's author.
 */
public class ConvertibleVerse {
    
    private Verse mainVerse;

    static private VersificationConverter versificationConverter = new VersificationConverter();

    public ConvertibleVerse(Verse verse) {
        this(verse.getVersification(), verse.getBook(), verse.getChapter(), verse.getVerse());
    }

    public ConvertibleVerse(BibleBook book, int chapter, int verseNo) {
        this(Versifications.instance().getVersification(Versifications.DEFAULT_V11N), book, chapter, verseNo);
    }

    public ConvertibleVerse(Versification versification, BibleBook book, int chapter, int verseNo) {
        mainVerse = new Verse(versification, book, chapter, verseNo);
    }

    public boolean isConvertibleTo(Versification v11n) {
        return versificationConverter.isConvertibleTo(mainVerse, v11n);
    }

    public void setChapterVerse(ChapterVerse chapterVerse) {
        mainVerse = new Verse(mainVerse.getVersification(), mainVerse.getBook(), chapterVerse.getChapter(), chapterVerse.getVerse());
    }
    public ChapterVerse getChapterVerse() {
        return new ChapterVerse(mainVerse.getChapter(), mainVerse.getVerse());
    }

    /** Set the verse, mapping to the required versification if necessary
     */
    public void setVerse(Versification requiredVersification, Verse verse) {
        mainVerse = versificationConverter.convert(verse, requiredVersification);
    }

    public Verse getVerse() {
        return mainVerse;
    }

    public Verse getVerse(Versification versification) {
        return versificationConverter.convert(mainVerse, versification);
    }


    /** books should be the same as they are enums
     */
    public BibleBook getBook() {
        return mainVerse.getBook();
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result
                + ((mainVerse == null) ? 0 : mainVerse.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        ConvertibleVerse other = (ConvertibleVerse) obj;
        if (mainVerse == null) {
            if (other.mainVerse != null)
                return false;
        } else if (!mainVerse.equals(other.mainVerse))
            return false;
        return true;
    }
    
    public JSONObject getStateJson() throws JSONException {
        JSONObject object = new JSONObject();
        object.put("versification", mainVerse.getVersification().getName());
        object.put("bibleBook", mainVerse.getBook().ordinal());
        object.put("chapter", mainVerse.getChapter());
        object.put("verseNo", mainVerse.getVerse());
        return object;
    }
    
    public void restoreState(JSONObject jsonObject) throws JSONException {
        if (jsonObject!=null) {
            if (jsonObject.has("versification") && jsonObject.has("bibleBook")) {
                Versification v11n = Versifications.instance().getVersification(jsonObject.getString("versification"));
                int bibleBookNo =  jsonObject.getInt("bibleBook");
                int chapterNo = jsonObject.getInt("chapter");
                int verseNo = jsonObject.getInt("verseNo");
    
                mainVerse = new Verse(v11n, BibleBook.values()[bibleBookNo], chapterNo, verseNo, true);
            }
        }
    }
}
