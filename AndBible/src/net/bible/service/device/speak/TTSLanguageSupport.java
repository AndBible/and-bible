package net.bible.service.device.speak;

import java.util.Locale;

import net.bible.service.common.CommonUtils;

/** maintain a list of languages that are knwn to be supported by the installed TTS engine
 * this list will be updated on success/failure of TTS init
 * 
 * @author denha1m
 *
 */
public class TTSLanguageSupport {

	private static final String TTS_LANG_SUPPORTED_KEY = "TTS_LANG_SUPPORTED";
	private static final String LANG_SEPERATOR = ",";

	public void addSupportedLocale(Locale locale) {
		
		if (!isLangKnownToBeSupported(locale.getLanguage())) {
			String langCode = locale.getLanguage();
			String langList = getSupportedLangList();
			CommonUtils.getSharedPreferences()
						.edit()
						.putString(TTS_LANG_SUPPORTED_KEY, langList+LANG_SEPERATOR+langCode)
						.commit();
		}
	}
	
	public void addUnsupportedLocale(Locale locale) {
		if (isLangKnownToBeSupported(locale.getLanguage())) {
			String langCode = locale.getLanguage();
			String langList = getSupportedLangList();
			CommonUtils.getSharedPreferences()
						.edit()
						.putString(TTS_LANG_SUPPORTED_KEY, langList.replace(LANG_SEPERATOR+langCode, ""))
						.commit();
		}
	}
	
	public boolean isLangKnownToBeSupported(String langCode) {
		boolean isSupported = getSupportedLangList().contains(langCode);
		return isSupported;
	}
	
	private String getSupportedLangList() {
		return CommonUtils.getSharedPreferences().getString(TTS_LANG_SUPPORTED_KEY, "");
	}
}
