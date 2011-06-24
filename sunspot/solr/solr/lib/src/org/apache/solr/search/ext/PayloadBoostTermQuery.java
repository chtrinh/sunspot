/**
 * NOT IN USE! Keep for reference sake.
 * An extension of the PayloadTermQuery, with an ability to add a term boost at query time. 
 * Reference: http://lucene.apache.org/java/2_4_0/scoring.html
 */
package org.apache.solr.search.ext;

import org.apache.lucene.index.Term;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.TermPositions;
import org.apache.lucene.search.Searcher;
import org.apache.lucene.search.Scorer;
import org.apache.lucene.search.Weight;
import org.apache.lucene.search.Similarity;
import org.apache.lucene.search.Explanation;
import org.apache.lucene.search.ComplexExplanation;
import org.apache.lucene.search.payloads.PayloadFunction;
import org.apache.lucene.search.spans.TermSpans;
import org.apache.lucene.search.spans.SpanTermQuery;
import org.apache.lucene.search.spans.SpanWeight;
import org.apache.lucene.search.spans.SpanScorer;

import java.io.IOException;

/**
 * This class is very similar to
 * {@link org.apache.lucene.search.spans.SpanTermQuery} except that it factors
 * in the value of the payload located at each of the positions where the
 * {@link org.apache.lucene.index.Term} occurs.
 * <p>
 * In order to take advantage of this, you must override
 * {@link org.apache.lucene.search.Similarity#scorePayload(int, String, int, int, byte[],int,int)}
 * which returns 1 by default.
 * <p>
 * Payload scores are aggregated using a pluggable {@link PayloadFunction}.
 **/
public class PayloadBoostTermQuery extends SpanTermQuery {

	private static final long serialVersionUID = 4030831244663256838L;
	protected PayloadFunction function;
    private boolean includeSpanScore;
    private float queryBoost = 1.0F;

    public PayloadBoostTermQuery(Term term, PayloadFunction function) {
        this (term, function, true);
    }
    
    public PayloadBoostTermQuery(Term term, PayloadFunction function,
            boolean includeSpanScore, float queryBoost) {    
        super (term);
        this .function = function;
        this .includeSpanScore = includeSpanScore;
    	this .queryBoost = queryBoost;
    }

    public PayloadBoostTermQuery(Term term, PayloadFunction function,
            boolean includeSpanScore) {            	
        super (term);
        this .function = function;
        this .includeSpanScore = includeSpanScore;
    }
    
    
    public Weight createWeight(Searcher searcher) throws IOException {
    	this.setBoost(queryBoost); // boost the query's weight here.
        return new PayloadTermWeight(this , searcher);
    }

    protected class PayloadTermWeight extends SpanWeight {

		private static final long serialVersionUID = 549501202294606677L;

		public PayloadTermWeight(PayloadBoostTermQuery query,
                Searcher searcher) throws IOException {
            super (query, searcher);
        }
		
        @Override
        public Scorer scorer(IndexReader reader,
                boolean scoreDocsInOrder, boolean topScorer)
                throws IOException {
            return new PayloadTermSpanScorer((TermSpans) query.getSpans(reader), this , 
            		similarity, reader.norms(query.getField()));
        }

        protected class PayloadTermSpanScorer extends SpanScorer {
            // TODO: is this the best way to allocate this?
            protected byte[] payload = new byte[256];
            protected TermPositions positions;
            protected float payloadScore;
            protected int payloadsSeen;
            protected float value;

            public PayloadTermSpanScorer(TermSpans spans,
                    Weight weight, Similarity similarity, byte[] norms)
                    throws IOException {
                super (spans, weight, similarity, norms);
//                        super (spans, weight, similarity, null);
                positions = spans.getPositions();
                value = weight.getValue();
            }

            @Override
            protected boolean setFreqCurrentDoc() throws IOException {
                if (!more) {
                    return false;
                }
                doc = spans.doc();
                freq = 0.0f;
                payloadScore = 0;
                payloadsSeen = 0;
                Similarity similarity1 = getSimilarity();
                while (more && doc == spans.doc()) {
                    int matchLength = spans.end() - spans.start();

                    freq += similarity1.sloppyFreq(matchLength);
                    processPayload(similarity1);

                    more = spans.next();// this moves positions to the next match in this document
                }
                return more || (freq != 0);
            }

            protected void processPayload(Similarity similarity)
                    throws IOException {
                if (positions.isPayloadAvailable()) {
                	payload = positions.getPayload(payload, 0);
                    payloadScore = function.currentScore(doc, term
                            .field(), spans.start(), spans.end(),
                            payloadsSeen, payloadScore,
                            similarity.scorePayload(doc, term.field(), spans.start(), spans.end(), payload, 0, positions.getPayloadLength()));
                    payloadsSeen++;

                } else {
                    // zero out the payload?
                }
            }

            /**
             * 
             * @return {@link #getSpanScore()} * {@link #getPayloadScore()} * query's boost value for that term
             * @throws IOException
             */
            @Override
            public float score() throws IOException {

                return (includeSpanScore ? getSpanScore() * getPayloadScore() : getPayloadScore()) * value;
            }

            /**
             * Returns the SpanScorer score only.
             * <p/>
             * Should not be overridden without good cause!
             * 
             * @return the score for just the Span part w/o the payload
             * @throws IOException
             * 
             * @see #score()
             */
            protected float getSpanScore() throws IOException {
                return super.score();
            }

            /**
             * The score for the payload
             * 
             * @return The score, as calculated by
             *         {@link PayloadFunction#docScore(int, String, int, float)}
             */
            protected float getPayloadScore() {
                return function.docScore(doc, term.field(),
                        payloadsSeen, payloadScore);
            }

            @Override
            protected Explanation explain(final int doc)
                    throws IOException {
                ComplexExplanation result = new ComplexExplanation();
                Explanation nonPayloadExpl = super .explain(doc);
                result.addDetail(nonPayloadExpl);
                // QUESTION: Is there a way to avoid this skipTo call? We need to know
                // whether to load the payload or not
                Explanation payloadBoost = new Explanation();
                result.addDetail(payloadBoost);

                float payloadScore = getPayloadScore();
                payloadBoost.setValue(payloadScore);
                // GSI: I suppose we could toString the payload, but I don't think that
                // would be a good idea
                payloadBoost.setDescription("scorePayload(...)");
                result.setValue(nonPayloadExpl.getValue()
                        * payloadScore);
                result.setDescription("btq, product of:");
                result.setMatch(nonPayloadExpl.getValue() == 0 ? Boolean.FALSE : Boolean.TRUE); // LUCENE-1303
                return result;
            }

        }
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = super .hashCode();
        result = prime * result
                + ((function == null) ? 0 : function.hashCode());
        result = prime * result + (includeSpanScore ? 1231 : 1237);
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this  == obj)
            return true;
        if (!super .equals(obj))
            return false;
        if (getClass() != obj.getClass())
            return false;
        PayloadBoostTermQuery other = (PayloadBoostTermQuery) obj;
        if (function == null) {
            if (other.function != null)
                return false;
        } else if (!function.equals(other.function))
            return false;
        if (includeSpanScore != other.includeSpanScore)
            return false;
        return true;
    }

}