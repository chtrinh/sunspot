/**
 * Parser plugin to parse payload queries.
 * Requires commons-lang-2.6.jar to be within the the same /lib due to dependencies for StringUtils.
 * Implementation details from: http://sujitpal.blogspot.com/2011_01_01_archive.html
 */
package org.apache.solr.search.ext;

import org.apache.commons.lang.StringUtils;
import org.apache.lucene.index.Term;
import org.apache.lucene.queryParser.ParseException;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.payloads.*;
import org.apache.solr.common.params.SolrParams;
import org.apache.solr.common.util.NamedList;
import org.apache.solr.request.SolrQueryRequest;
import org.apache.solr.search.QParser;
import org.apache.solr.search.QParserPlugin;

/**
 * 
 */
public class PayloadQParserPlugin extends QParserPlugin {

  @Override
  public QParser createParser(String qstr, SolrParams localParams,
      SolrParams params, SolrQueryRequest req) {
    return new PayloadQParser(qstr, localParams, params, req);
  }

  public void init(NamedList args) {
    // do nothing
  }
}

class PayloadQParser extends QParser {

  public PayloadQParser(String qstr, SolrParams localParams, 
      SolrParams params, SolrQueryRequest req) {
    super(qstr, localParams, params, req);
  }

  @Override
  public Query parse() throws ParseException {	  
	BooleanQuery q = new BooleanQuery(true); // disables coord scoring
    String[] nvps = StringUtils.split(qstr, " ");
    for (int i = 0; i < nvps.length; i++) {
      /** 
       * index 0: should be the playload fieldname
       * index 1: a term for the query 
       * index 2: float value to apply boost to the term
       * ie. 
       *  keywords:car|34.0
       */
      String[] nv = StringUtils.split(nvps[i], "/[:^]/");
      float boostValue = 1.0F;  
      if(nv.length == 3){
    	  boostValue = Float.parseFloat(nv[2]); 
      }      
      PayloadTermQuery query = null;
      if (nv[0].startsWith("+")) {
    	  query = new PayloadTermQuery(new Term(nv[0].substring(1), nv[1]), new MaxPayloadFunction(), true);
    	  query.setBoost(boostValue);
    	  q.add(query, Occur.MUST);
      } else {
    	  query = new PayloadTermQuery(new Term(nv[0], nv[1]), new MaxPayloadFunction(), true);
    	  query.setBoost(boostValue);
    	  q.add(query, Occur.SHOULD);
      }
    }
    return q;
  }
}