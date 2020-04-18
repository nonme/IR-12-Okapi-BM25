package okapi;

import smile.nlp.relevance.BM25;

import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Scanner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public class Okapi {
    /**
     * Read all files in the dictionary and add them in the inverted index
     * @param path - path to dictionary
     */
    public void readDirectory(String path) {
        try (Stream<Path> paths = Files.walk(Paths.get(path))) {
            ArrayList<String> absolutePaths = new ArrayList<>();
            paths
                    .filter(Files::isRegularFile)
                    .map(Path::toString)
                    .forEach(absolutePaths::add);

            // Add all documents one by one to the index
            int docID = 0;
            for (String filePath : absolutePaths) {
                Scanner scanner = new Scanner(new FileReader(filePath));
                readDocument(scanner, docID);
                docID++;

                // Now get only document title and add to index
                documentTitles.add(filePath.substring(filePath.lastIndexOf('\\') + 1, filePath.lastIndexOf('.')));
            }

            // Calculate average document size
            avgDocSize = 0;
            for (int i = 0; i < docSizes.size(); ++i) {
                avgDocSize += docSizes.get(i);
            }
            avgDocSize /= docSizes.size();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Read single document
     * @param scanner - scanner with file opened
     * @param docID - current docID among all files
     */
    public void readDocument(Scanner scanner, int docID) {
        docSizes.add(0);
        while (scanner.hasNextLine()) {
            // Get only regex-suited words
            Matcher matcher = regexPattern.matcher(scanner.nextLine());
            while (matcher.find()) {
                addTermToDictionary(matcher.group().toLowerCase(), docID);
            }
        }
    }

    /**
     * Add word to the dictionary if absent or update it's frequency in the document
     * @param word
     * @param docID
     */
    private void addTermToDictionary(String word, int docID) {
        dictionary.computeIfAbsent(word, k -> new Term(word));
        dictionary.get(word).add(docID);

        // Increase this docSize by one
        docSizes.set(docSizes.size() - 1, docSizes.get(docSizes.size() - 1) + 1);
    }

    /**
     * Query inverted index with query
     * @param query
     * @return
     */
    public ArrayList<String> query(String query) {
        // Separate query to single tokens
        String[] tokens = query.replaceAll("[^a-zA-Z9\\-\\s]", "").toLowerCase().split("\\s+");
        // Get merged posting list with calculated Okapi BM25 scores
        ArrayList<WeightedPosting> postingList = OR(tokens);
        // Sort them in score decreasing order
        Collections.sort(postingList);

//        Uncomment to see scores
//        for (WeightedPosting posting : postingList) {
//            System.out.println(documentTitles.get(posting.posting) + " " + posting.score);
//        }

        // Put in the separate array to see only titles
        ArrayList<String> result = new ArrayList<>();
        for (int i = 0; i < postingList.size(); ++i) {
            result.add(documentTitles.get(postingList.get(i).posting));
        }
        return result;
    }

    /**
     * Inner method to retrieve tokens from inverted index and to calculate their Okapi BM25 scores
     * @param tokens - array of tokens
     * @return arraylist with document indexes with their score
     */
    private ArrayList<WeightedPosting> OR(String[] tokens) {
        HashMap<Integer, Double> scoreMap = new HashMap<>();

        // For all tokens search them in dictionary and if present calculate their document's score
        for (String token : tokens) {
            if (dictionary.containsKey(token)) {
                ArrayList<Integer> postingList = dictionary.get(token).postingList;
                ArrayList<Integer> frequency = dictionary.get(token).frequency;

                // Calculate Okapi BM25 score (scorer is an instance of OkapiBM25 from smile lib)
                for (int i = 0; i < postingList.size(); ++i) {
                    int index = postingList.get(i);
                    scoreMap.putIfAbsent(index, 0.0);
                    scoreMap.put(index, scoreMap.get(index) + scorer.score(
                            frequency.get(i),
                            docSizes.get(index),
                            avgDocSize,
                            docSizes.size(),
                            postingList.size()));
                }
            }
        }
        //Merge from hashmap to result
        ArrayList<WeightedPosting> result = new ArrayList<>(scoreMap.size());
        for (HashMap.Entry<Integer, Double> entry : scoreMap.entrySet()) {
            result.add(new WeightedPosting(entry.getKey(), entry.getValue()));
        }

        return result;
    }

    private HashMap<String, Term> dictionary = new HashMap<>();
    private ArrayList<Integer> docSizes = new ArrayList<>();
    private ArrayList<String> documentTitles = new ArrayList<>();
    private Pattern regexPattern = Pattern.compile("[a-zA-Z]+-?'?`?[a-zA-Z]+");
    /*
        Scorer from Smile Java NLP library that provides Okapi BM25 scorer
     */
    private BM25 scorer = new BM25();
    private double avgDocSize = 0;

    private class WeightedPosting implements Comparable<WeightedPosting> {
        public WeightedPosting(int posting, double score) {
            this.posting = posting;
            this.score = score;
        }
        public int posting;
        public double score;

        @Override
        public int compareTo(WeightedPosting o) {
            double value = o.score - this.score;
            return Double.compare(value, 0);
        }
    }

    private class Term {
        public Term(String term) {
            this.term = term;
            this.postingList = new ArrayList<>();
            this.frequency = new ArrayList<>();
        }

        public void add(int docID) {
            if (postingList.isEmpty() || postingList.get(postingList.size() - 1) != docID) {
                postingList.add(docID);
                frequency.add(0);
            }
            else {
                frequency.set(frequency.size() - 1, frequency.get(frequency.size() - 1) + 1);
            }
        }

        final public String term;
        private ArrayList<Integer> postingList;
        private ArrayList<Integer> frequency;
    }

    public static void main(String[] args) {
        Okapi okapi = new Okapi();
        okapi.readDirectory("shakespeare");
        ArrayList<String> results = okapi.query("Hi Romeo I am Henry");
        for (String result : results)
            System.out.println(result);
    }
}