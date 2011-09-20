package org.crosswire.jsword.index.lucene.analysis;

import java.io.IOException;
import java.io.Reader;

import org.apache.lucene.analysis.TokenStream;

import com.chenlb.mmseg4j.analysis.ComplexAnalyzer;

public class Mmseg4jChineseLuceneAnalyzer extends AbstractBookAnalyzer {
    public Mmseg4jChineseLuceneAnalyzer() {
        myAnalyzer = new ComplexAnalyzer();
    }

    /* (non-Javadoc)
     * @see org.apache.lucene.analysis.Analyzer#tokenStream(java.lang.String, java.io.Reader)
     */
    @Override
    public final TokenStream tokenStream(String fieldName, Reader reader) {
        return myAnalyzer.tokenStream(fieldName, reader);
    }

    /* (non-Javadoc)
     * @see org.apache.lucene.analysis.Analyzer#reusableTokenStream(java.lang.String, java.io.Reader)
     */
    @Override
    public final TokenStream reusableTokenStream(String fieldName, Reader reader) throws IOException {
        return myAnalyzer.reusableTokenStream(fieldName, reader);
    }

    private ComplexAnalyzer myAnalyzer;
}
