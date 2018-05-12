package net.bible.android.view.activity.page;

/**
 * Support infinite scroll by inserting text into web page.
 *
 * Created by mjden on 30/08/2017.
 */
public interface BibleViewTextInserter {

	void insertTextAtTop(String textId, String text);

	void insertTextAtEnd(String textId, String text);
}
