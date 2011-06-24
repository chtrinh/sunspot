/**
 * Payload Similarity implementation, use for scoring payload relevance from
 * payload fields. Payloads are fields that contain terms and 'boost' or 'weighted' values 
 * typically as a float value, separated  by a specified delimiter under /solr/conf/schema.xml
 * <typefield/> tag 
 * ie. 
 *   "car|4.0 red|55.0 white|32.0"
 */
package org.apache.solr.search.ext;

import org.apache.lucene.analysis.payloads.PayloadHelper;
import org.apache.lucene.search.DefaultSimilarity;

public class PayloadSimilarity extends DefaultSimilarity {

  private static final long serialVersionUID = -2402909220013794848L;

  @Override
  public float scorePayload(int docId, String fieldName,
      int start, int end, byte[] payload, int offset, int length) {
    if (payload != null) {
      float weight = PayloadHelper.decodeFloat(payload, offset); 
      return weight;  
    } else {
      return 1.0F;
    }
  }
  
  /** 
   * Set to 1.0F to ignore idf affects on payloads 
   * The "rare" terms should NOT give higher contributions to the total score, since relevance
   * should be given via 'user' query in a form of a term boost.
   * Previously implemented as <code>log(numDocs/(docFreq+1)) + 1</code>.
   * Reference: http://lucene.apache.org/java/2_4_0/api/org/apache/lucene/search/Similarity.html
   */
  @Override
  public float idf(int docFreq, int numDocs) {
      return 1.0F;
  }
  
  /** 
   * Removes the score factor based on the fraction of all query terms that a document contains. 
   * This value is multiplied into scores.
   * Previously implemented as <code>overlap / maxOverlap</code>. 
   */
  @Override
  public float coord(int overlap, int maxOverlap) {
      return 1.0F;
  }
}