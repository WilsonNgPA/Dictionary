package ca.ubc.cs317.dict.net;

import ca.ubc.cs317.dict.net.DictStringParser;
import ca.ubc.cs317.dict.model.Database;
import ca.ubc.cs317.dict.model.Definition;
import ca.ubc.cs317.dict.model.MatchingStrategy;

import javax.xml.crypto.Data;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.*;

/**
 * Created by Jonatan on 2017-09-09.
 */
public class DictionaryConnection {

    private static final int DEFAULT_PORT = 2628;
    private Socket dictSocket;
    private PrintWriter out;
    private BufferedReader in;

    /** Establishes a new connection with a DICT server using an explicit host and port number, and handles initial
     * welcome messages.
     *
     * @param host Name of the host where the DICT server is running
     * @param port Port number used by the DICT server
     * @throws DictConnectionException If the host does not exist, the connection can't be established, or the messages
     * don't match their expected value.
     */
    public DictionaryConnection(String host, int port) throws DictConnectionException {

        // TODO Add your code here
        try {
            // Create socket, input stream, and output stream.
            dictSocket = new Socket();
            dictSocket.connect(new InetSocketAddress(host, port), 10000);
            dictSocket.setSoTimeout(10000);
            out = new PrintWriter(dictSocket.getOutputStream(), true);
            in = new BufferedReader(new InputStreamReader(dictSocket.getInputStream()));

            // Read status code and if status code is negative reply, throw exception
            Status s = Status.readStatus(in);
            if(s.isNegativeReply()) {
                throw new DictConnectionException();
            }
        }
        catch(IOException e) {
            // Throw DictConnectionException for any exception caught when creating socket, input stream,
            // output stream, or when reading status.
            throw new DictConnectionException();
        }
    }

    /** Establishes a new connection with a DICT server using an explicit host, with the default DICT port number, and
     * handles initial welcome messages.
     *
     * @param host Name of the host where the DICT server is running
     * @throws DictConnectionException If the host does not exist, the connection can't be established, or the messages
     * don't match their expected value.
     */
    public DictionaryConnection(String host) throws DictConnectionException {
        this(host, DEFAULT_PORT);
    }

    /** Sends the final QUIT message and closes the connection with the server. This function ignores any exception that
     * may happen while sending the message, receiving its reply, or closing the connection.
     *
     */
    public synchronized void close() {

        // TODO Add your code here
        try {
            // Send QUIT command to server, wait for reply, and then close socket.
            out.println("QUIT");
            System.out.println(in.readLine());
            dictSocket.close();
        }
        catch (IOException e) {
            // Do nothing
        }
    }

    /** Requests and retrieves all definitions for a specific word.
     *
     * @param word The word whose definition is to be retrieved.
     * @param database The database to be used to retrieve the definition. A special database may be specified,
     *                 indicating either that all regular databases should be used (database name '*'), or that only
     *                 definitions in the first database that has a definition for the word should be used
     *                 (database '!').
     * @return A collection of Definition objects containing all definitions returned by the server.
     * @throws DictConnectionException If the connection was interrupted or the messages don't match their expected value.
     */
    public synchronized Collection<Definition> getDefinitions(String word, Database database) throws DictConnectionException {
        Collection<Definition> set = new ArrayList<>();

        // TODO Add your code here
        try {
            // Send DEFINE command with specified database name and word
            out.println("DEFINE " + database.getName() + " \"" + word + "\"");

            // Read status, if negative reply return empty set
            Status s = Status.readStatus(in);
            if(s.isNegativeReply()) {
                return set;
            }

            // Get number of definition found for specified word from specified database from status response
            int defintionCount = Integer.parseInt(s.getDetails().substring(0, s.getDetails().indexOf(" ")));

            // For loop to add all definition found into the set
            for(int i = 0; i < defintionCount; i++) {
                boolean reachedNewDefinition = false;
                String databaseName = "", fullDefinition = "";
                String readDefinition = in.readLine();

                // If line contains 250, all definition has been added to the set, return set
                if(readDefinition.contains("250")) {
                    return set;
                }

                // If line contains 151, the line contains details of the definition. Extract the database name where
                // definition has been found
                if(readDefinition.contains("151")) {
                    String[] detailsArray = readDefinition.split(" ", 4);
                    databaseName = detailsArray[2];
                }

                // Do while loop to read the definition line by line until end of definition
                do {
                    readDefinition = in.readLine();
                    if(readDefinition.equals(".")) {
                        reachedNewDefinition = true;
                    }
                    else {
                        fullDefinition += readDefinition + "\n";
                    }
                } while(!reachedNewDefinition);

                // Add full definition into set
                Definition defined = new Definition(word, databaseName);
                defined.setDefinition(fullDefinition);
                set.add(defined);
            }
        }
        catch (IOException e) {
            throw new DictConnectionException();
        }
        return set;
    }

    /** Requests and retrieves a list of matches for a specific word pattern.
     *
     * @param word     The word whose definition is to be retrieved.
     * @param strategy The strategy to be used to retrieve the list of matches (e.g., prefix, exact).
     * @param database The database to be used to retrieve the definition. A special database may be specified,
     *                 indicating either that all regular databases should be used (database name '*'), or that only
     *                 matches in the first database that has a match for the word should be used (database '!').
     * @return A set of word matches returned by the server.
     * @throws DictConnectionException If the connection was interrupted or the messages don't match their expected value.
     */
    public synchronized Set<String> getMatchList(String word, MatchingStrategy strategy, Database database) throws DictConnectionException {
        Set<String> set = new LinkedHashSet<>();

        // TODO Add your code here
        try {
            // Send MATCH command with specified database name, strategy, and word
            out.println("MATCH " + database.getName() + " " + strategy.getName() + " \"" + word + "\"");

            // Read status, if negative reply return empty set
            Status s = Status.readStatus(in);
            if (s.isNegativeReply()) {
                return set;
            }

            // For loop to read each match line by line and add it into the set
            for(String match = in.readLine(); !match.contains("250"); match = in.readLine()) {
                if(!match.equals(".")) {
                    word = match.substring(match.indexOf(" ") + 2, match.length() - 1);
                    if(!set.contains(word)) {
                        set.add(word);
                    }
                }
            }
        }
        catch (IOException e) {
            throw new DictConnectionException();
        }
        return set;
    }

    /** Requests and retrieves a map of database name to an equivalent database object for all valid databases used in the server.
     *
     * @return A map of Database objects supported by the server.
     * @throws DictConnectionException If the connection was interrupted or the messages don't match their expected value.
     */
    public synchronized Map<String, Database> getDatabaseList() throws DictConnectionException {
        Map<String, Database> databaseMap = new HashMap<>();

        // TODO Add your code here
        try {
            // Send SHOW DB command
            out.println("SHOW DB");

            // Read status, if negative reply return empty databaseMap
            Status s = Status.readStatus(in);
            if(s.isNegativeReply()) {
                return databaseMap;
            }
            for(String readDatabase = in.readLine(); !readDatabase.contains("250 ok"); readDatabase = in.readLine()) {
                String dbName = "", dbDescription = "";
                String[] detailsArray = readDatabase.split(" ", 2);
                if(detailsArray.length >= 2) {
                    dbName = detailsArray[0];
                    dbDescription = detailsArray[1].substring(1, detailsArray[1].length() - 1);
                    databaseMap.put(dbName, new Database(dbName, dbDescription));
                }
            }
        }
        catch (IOException e) {
            throw new DictConnectionException();
        }
        return databaseMap;
    }

    /** Requests and retrieves a list of all valid matching strategies supported by the server.
     *
     * @return A set of MatchingStrategy objects supported by the server.
     * @throws DictConnectionException If the connection was interrupted or the messages don't match their expected value.
     */
    public synchronized Set<MatchingStrategy> getStrategyList() throws DictConnectionException {
        Set<MatchingStrategy> set = new LinkedHashSet<>();

        // TODO Add your code here
        try {
            out.println("SHOW STRAT");
            Status s = Status.readStatus(in);
            if(s.isNegativeReply()) {
                return set;
            }
            for(String readStrat = in.readLine(); !readStrat.contains("250 ok"); readStrat = in.readLine()) {
                String stratName = "", stratDescription = "";
                String[] detailsArray = readStrat.split(" ", 2);
                if(detailsArray.length >= 2) {
                    stratName = detailsArray[0];
                    stratDescription = detailsArray[1].substring(1, detailsArray[1].length() - 1);
                    set.add(new MatchingStrategy(stratName, stratDescription));
                }
            }
        }
        catch (IOException e) {
            throw new DictConnectionException();
        }

        return set;
    }
}
