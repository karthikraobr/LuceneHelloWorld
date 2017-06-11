import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

import javax.xml.stream.*;
import javax.xml.stream.events.XMLEvent;
import javax.xml.stream.events.Attribute;

import org.apache.lucene.analysis.core.SimpleAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.misc.HighFreqTerms;
import org.apache.lucene.misc.TermStats;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.RAMDirectory;

/**
 * @author karth
 *
 */
class Index {
	private String lineId = "";
	private String playName = "";
	private String speechNumber = "";
	private String speaker = "";
	private String lineNumber = "";
	private String textEntry = "";

	public void setLineId(String id) {
		lineId = id;
	}

	public String getLineId() {
		return lineId;
	}

	public void setPlayName(String str) {
		playName = str;
	}

	public String getPlayName() {
		return playName;
	}

	public void setSpeechNumber(String id) {
		speechNumber = id;
	}

	public String getSpeechNumber() {
		return speechNumber;
	}

	public void setSpeaker(String str) {
		speaker = str;
	}

	public String getSpeaker() {
		return speaker;
	}

	public void setLineNumber(String str) {
		lineNumber = str;
	}

	public String geLineNumber() {
		return lineNumber;
	}

	public void setTextEntry(String str) {
		textEntry = str;
	}

	public String getTextEntry() {
		return textEntry;
	}
}

/**
 * @author karth
 *
 */
public class HelloLucene {
	public static void main(String[] args) throws IOException, XMLStreamException {
		// Specify the analyzer for tokenizing the text.
		// The same analyzer should be used for indexing and searching
		SimpleAnalyzer analyzer = new SimpleAnalyzer();
		// Use the ram directory for creating index
		Directory index = new RAMDirectory();
		IndexWriterConfig config = new IndexWriterConfig(analyzer);
		// Create the index writer used to write the index.
		IndexWriter w = new IndexWriter(index, config);
		// Take filepath from the command line.
		String filePath = args.length > 0 ? args[0] : "E:\\will_play_text.xml";
		// Parse the XML and index it.
		parseAndExtract(w, filePath);

		// Get handle of the reader to read from the indexes.
		IndexReader reader = DirectoryReader.open(index);
		// Array to store the top 1000 frequently used terms in the XML.
		TermStats[] result;
		List<String> terms = new ArrayList<String>();
		try {
			// Get the top 1000 frequently used terms from the "text_entry"
			// field. If null is passed then it queries
			result = HighFreqTerms.getHighFreqTerms(reader, 1000, "text_entry",
					new HighFreqTerms.TotalTermFreqComparator());
			int counter = 1;
			// Log it to the console.
			System.out.println("The 1000 most frequent terms in the XML\n\n");
			System.out.format("%10s%10s%10s\n", "No", "Text", "Frequency");
			System.out.println("--------------------------------------------");
			for (TermStats stats : result) {
				System.out.format("%10d%10s%10d\n", counter, stats.termtext.utf8ToString(), stats.totalTermFreq);
				// Store the termtext as text to generate OR and AND queries
				// later.
				terms.add(stats.termtext.utf8ToString());
				counter++;
			}
			// Boilerplate for pretty printing results.
			System.out.println(
					"\n\n------------------------------------OR Queries------------------------------------\n\n");
			System.out.format("%10s%20s%20s%15s%20s%20s\n", "No", "Term1", "Term2", "N", "Time Taken", "Result Count");
			System.out.println(
					"----------------------------------------------------------------------------------------------------------");
			// For a BooleanQuery with no MUST clauses one or more SHOULD
			// clauses must match a document for the BooleanQuery to match. (OR
			// queries)
			Occur occur = BooleanClause.Occur.SHOULD;

			// Iterating 200 times the first 100 to generate OR queries and the
			// next 100 to generate AND queries.
			for (int i = 0; i < 200; i++) {
				// List to hold the times to calculate the average.
				List<Double> times = new ArrayList<Double>();
				// Once we encounter index 100 it is time to switch to AND
				// queries since we have already generated 100 OR queries.
				if (i == 100) {
					occur = BooleanClause.Occur.MUST;
					System.out.println(
							"\n\n------------------------------------AND Queries------------------------------------\n\n");
					System.out.format("%10s%20s%20s%15s%20s%20s\n", "No", "Term1", "Term2", "N", "Time Taken",
							"Result Count");
					System.out.println(
							"----------------------------------------------------------------------------------------------------------");
				}

				// Get 2 random terms from a list of top 1000 frequently used
				// terms.
				String term1 = terms.get(getRandomIndex(terms.size()));
				String term2 = terms.get(getRandomIndex(terms.size()));
				// Initialize the searcher.
				IndexSearcher searcher = new IndexSearcher(reader);
				// Create a boolean query.
				BooleanQuery.Builder categoryQuery = getQuery(occur, term1, term2);
				// For n=1,2,4,8,32,128
				for (int raiseToPower = 0; raiseToPower <= 7; raiseToPower++) {
					// We don't need pow(2,4) and pow(2,6)
					if (raiseToPower == 4 || raiseToPower == 6) {
						raiseToPower++;
					}
					//Number of results.
					int hitsPerPage = (int) Math.pow(2, raiseToPower);
					double startTime = System.nanoTime();
					// Query the index for the query generated.
					TopDocs docs = searcher.search(categoryQuery.build(), hitsPerPage);
					double currentTime = System.nanoTime();
					// Get the elapsed time in milliseconds
					double timeTaken = (currentTime - startTime) / 1000000;
					times.add(timeTaken);
					ScoreDoc[] hits = docs.scoreDocs;
					// Log the results to the console.
					System.out.format("%10d%20s%20s%15d%20f%20d\n", i + 1, term1, term2, hitsPerPage, timeTaken,
							hits.length);
					//Print the average
					if (raiseToPower == 7) {
						System.out.format("%10s%20s%20s%15s%20s%20s\n", "", "", "", "", "---------", "");
						System.out.format("%10s%20s%20s%15s%20f%20s\n\n\n", "", "", "", "",
								times.stream().mapToDouble(Double::doubleValue).sum() / 6, "");
						// List to hold the times to calculate the average.
						times = new ArrayList<Double>();
					}

				}
			}
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		// Close the reader.
		reader.close();
	}

	/**
	 * @param w
	 *            The index writer being used to write to the index.
	 * @param filePath
	 *            The path of the xml file.
	 * @throws FactoryConfigurationError
	 * @throws FileNotFoundException
	 * @throws XMLStreamException
	 * @throws IOException
	 */
	private static void parseAndExtract(IndexWriter w, String filePath)
			throws FactoryConfigurationError, FileNotFoundException, XMLStreamException, IOException {
		XMLInputFactory xmlInputFactory = XMLInputFactory.newInstance();
		// Load the XML from the file system.
		InputStream inputStream = new FileInputStream(filePath);
		// We are using SAX parsing hence we need to parse events and
		// XMLEventReader is the top level interface for parsing XML events
		XMLEventReader xmlEventReader = xmlInputFactory.createXMLEventReader(inputStream);
		// Iterate
		Index idx = new Index();
		while (xmlEventReader.hasNext()) {
			XMLEvent xmlEvent = xmlEventReader.nextEvent();
			// Check if the tag is a start element and check if it it a column
			// tag
			if (xmlEvent.isStartElement() && xmlEvent.asStartElement().getName().getLocalPart().equals("column")) {
				Iterator<?> iterator = xmlEvent.asStartElement().getAttributes();

				while (iterator.hasNext()) {
					Attribute attribute = (Attribute) iterator.next();
					String name = attribute.getValue();
					xmlEvent = xmlEventReader.nextEvent();
					if (xmlEvent.isCharacters()) {
						// Get the value inside a tag.
						String value = xmlEvent.asCharacters().getData() != null ? xmlEvent.asCharacters().getData()
								: "";
						// Switch based on the attribute value and set it to the
						// model we created.
						switch (name) {
						case "line_id":
							idx.setLineId(value);
							break;
						case "play_name":
							idx.setPlayName(value);
							break;
						case "speech_number":
							idx.setSpeechNumber(value);
							break;
						case "line_number":
							idx.setLineNumber(value);
							break;
						case "speaker":
							idx.setSpeaker(value);
							break;
						case "text_entry":
							idx.setTextEntry(value);
							break;
						default:
							break;
						}
					}

				}
				// If an end element of type table is encountered we have parsed
				// one document and it is now safe to write it to the index.
			} else if (xmlEvent.isEndElement() && xmlEvent.asEndElement().getName().getLocalPart().equals("table")
					&& idx.getPlayName() != null) {
				addToIndex(w, idx);
				idx = new Index();
			}
		}
		// Close the writer.
		w.close();
	}

	/**
	 * @param occur
	 *            Specify whether you need an AND or OR query.
	 * @param term1
	 *            The first term to query.
	 * @param term2
	 *            The second term to query.
	 * @return The generated query.
	 */
	private static BooleanQuery.Builder getQuery(Occur occur, String term1, String term2) {
		BooleanQuery.Builder categoryQuery = new BooleanQuery.Builder();
		TermQuery catQuery1 = new TermQuery(new Term("text_entry", term1));
		TermQuery catQuery2 = new TermQuery(new Term("text_entry", term2));
		categoryQuery.add(new BooleanClause(catQuery1, occur));
		categoryQuery.add(new BooleanClause(catQuery2, occur));
		return categoryQuery;
	}

	/**
	 * @param w Index writer
	 * @param idx The object which contains all the data to be indexed
	 * @throws IOException
	 */
	private static void addToIndex(IndexWriter w, Index idx) throws IOException {
		Document doc = new Document();
		//Index and tokenize all the fields in the XML.
		doc.add(new TextField("line_id", idx.getLineId(), Field.Store.YES));
		doc.add(new TextField("play_name", idx.getPlayName(), Field.Store.YES));
		doc.add(new TextField("speech_number", idx.getSpeechNumber(), Field.Store.YES));
		doc.add(new TextField("line_number", idx.geLineNumber(), Field.Store.YES));
		doc.add(new TextField("speaker", idx.getSpeaker(), Field.Store.YES));
		doc.add(new TextField("text_entry", idx.getTextEntry(), Field.Store.YES));
		w.addDocument(doc);
	}

	private static int getRandomIndex(int length) {
		Random dice = new Random();
		return dice.nextInt(length);
	}
}