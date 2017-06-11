import java.io.FileInputStream;
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

public class HelloLucene {
	public static void main(String[] args) throws IOException, XMLStreamException {
		// 0. Specify the analyzer for tokenizing text.
		// The same analyzer should be used for indexing and searching
		SimpleAnalyzer analyzer = new SimpleAnalyzer();
		// 1. create the index
		Directory index = new RAMDirectory();

		IndexWriterConfig config = new IndexWriterConfig(analyzer);
		IndexWriter w = new IndexWriter(index, config);

		XMLInputFactory xmlInputFactory = XMLInputFactory.newInstance();
		String filePath = args.length > 0 ? args[0] : "E:\\will_play_text.xml";
		// Load the XML from the file system.
		InputStream inputStream = new FileInputStream(filePath);
		// We are using SAX parsing hence we need to parse events and
		// XMLEventReader is the top level interface for parsing XML events
		XMLEventReader xmlEventReader = xmlInputFactory.createXMLEventReader(inputStream);
		// Iterate
		Index idx = new Index();
		while (xmlEventReader.hasNext()) {
			XMLEvent xmlEvent = xmlEventReader.nextEvent();

			if (xmlEvent.isStartElement()) {

				if (xmlEvent.asStartElement().getName().getLocalPart().equals("column")) {
					Iterator<?> iterator = xmlEvent.asStartElement().getAttributes();

					while (iterator.hasNext()) {
						Attribute attribute = (Attribute) iterator.next();
						String name = attribute.getValue();
						xmlEvent = xmlEventReader.nextEvent();
						if (xmlEvent.isCharacters()) {
							String value = xmlEvent.asCharacters().getData() != null ? xmlEvent.asCharacters().getData()
									: "";
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

				}
			} else if (xmlEvent.isEndElement() && xmlEvent.asEndElement().getName().getLocalPart().equals("table")
					&& idx.getPlayName() != null) {
				addDoc(w, idx);
				idx = new Index();
			}
		}
		w.close();

		IndexReader reader = DirectoryReader.open(index);
		TermStats[] result;
		List<String> terms = new ArrayList<String>();
		try {
			result = HighFreqTerms.getHighFreqTerms(reader, 1000, "text_entry", new HighFreqTerms.DocFreqComparator());
			int counter = 1;
			System.out.format("%10s%10s%10s\n", "No", "Text", "Frequency");
			for (TermStats stats : result) {
				System.out.format("%10d%10s%10d\n", counter, stats.termtext.utf8ToString(), stats.totalTermFreq);
				terms.add(stats.termtext.utf8ToString());
				counter++;
			}
			int raiseToPower = 0;
			System.out.println(
					"\n\n------------------------------------OR Queries------------------------------------\n\n");
			System.out.format("%10s%20s%20s%15s%20s%20s\n", "No", "Term1", "Term2", "N", "Time Taken", "Result Count");
			Occur occur = BooleanClause.Occur.SHOULD;
			List<Double> times = new ArrayList<Double>();
			for (int i = 0; i < 200; i++) {
				if (i == 100) {
					System.out.format("%10s%20s%20s%15s%20s%20s\n", "", "", "", "", "---------", "");
					System.out.format("%10s%20s%20s%15s%20f%20s\n", "", "", "", "",
							times.stream().mapToDouble(Double::doubleValue).sum() / 100, "");
					times = new ArrayList<Double>();
					occur = BooleanClause.Occur.MUST;
					System.out.println(
							"\n\n------------------------------------AND Queries------------------------------------\n\n");
					System.out.format("%10s%20s%20s%15s%20s%20s\n", "No", "Term1", "Term2", "N", "Time Taken",
							"Result Count");
					raiseToPower = 0;
				}
				BooleanQuery.Builder categoryQuery = new BooleanQuery.Builder();
				String term1 = terms.get(getRandomIndex(terms.size()));
				String term2 = terms.get(getRandomIndex(terms.size()));
				TermQuery catQuery1 = new TermQuery(new Term("text_entry", term1));
				TermQuery catQuery2 = new TermQuery(new Term("text_entry", term2));
				categoryQuery.add(new BooleanClause(catQuery1, occur));
				categoryQuery.add(new BooleanClause(catQuery2, occur));
				int hitsPerPage = (int) Math.pow(2, raiseToPower);
				IndexSearcher searcher = new IndexSearcher(reader);
				double startTime = System.nanoTime();
				TopDocs docs = searcher.search(categoryQuery.build(), hitsPerPage);
				double currentTime = System.nanoTime();
				// Get the elapsed time in milliseconds
				double timeTaken = (currentTime - startTime) / 1000000;
				times.add(timeTaken);
				ScoreDoc[] hits = docs.scoreDocs;
				System.out.format("%10d%20s%20s%15d%20f%20d\n", i + 1, term1, term2, hitsPerPage, timeTaken,
						hits.length);
				if (raiseToPower == 7) {
					raiseToPower = 0;
				} else {
					raiseToPower++;
				}
				if (raiseToPower == 4 || raiseToPower == 6) {
					raiseToPower++;
				}
				if (i == 199) {
					System.out.format("%10s%20s%20s%15s%20s%20s\n", "", "", "", "", "---------", "");
					System.out.format("%10s%20s%20s%15s%20f%20s\n", "", "", "", "",
							times.stream().mapToDouble(Double::doubleValue).sum() / 100, "");
				}
			}
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		reader.close();
	}

	private static void addDoc(IndexWriter w, Index idx) throws IOException {
		Document doc = new Document();
		doc.add(new TextField("line_id", idx.getLineId(), Field.Store.YES));
		doc.add(new TextField("play_name", idx.getPlayName(), Field.Store.YES));
		doc.add(new TextField("speech_number", idx.getSpeechNumber(), Field.Store.YES));
		doc.add(new TextField("line_number", idx.geLineNumber(), Field.Store.YES));
		doc.add(new TextField("speaker", idx.getSpeaker(), Field.Store.YES));
		doc.add(new TextField("text_entry", idx.getTextEntry(), Field.Store.YES));
		w.addDocument(doc);
	}

	private static int getRandomIndex(int length) {
		Random Dice = new Random();
		return Dice.nextInt(length);
	}
}