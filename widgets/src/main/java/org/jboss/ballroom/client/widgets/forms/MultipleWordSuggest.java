package org.jboss.ballroom.client.widgets.forms;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import com.google.gwt.user.client.ui.MultiWordSuggestOracle;
import com.google.gwt.user.client.ui.SuggestOracle;

/**
 * Very similar to com.google.gwt.user.client.ui.MultiWordSuggestOracle, except this class doesn't format the typed letters in bold.
 * This SuggestOracle implementation, searchs the typed query in any part of the string itens, also 
 * uses the Suggestion item class to show a formatted string and when user selects a item, it uses
 * the replacementValue of Suggestion bean.
 * 
 * @author Claudio Miranda
 */
public class MultipleWordSuggest extends SuggestOracle {

    private List<Suggestion> items = new ArrayList<>();
    private Response defaultResponse;

    /**
     * Sets the default suggestion collection, when the user doesn't type letters in the textbox.
     *
     * @param suggestionList the default list of suggestions
     */
    public void setDefaultSuggestions(Collection<Suggestion> suggestionList) {
        this.defaultResponse = new Response(suggestionList);
    }

    /**
     * This method is called when the user clicks in the textbox, and doesn't type anything, this way
     * a list of pre-made suggestions is presented to the user.
     * 
     */
    @Override
    public void requestDefaultSuggestions(Request request, Callback callback) {
        if (defaultResponse != null) {
            callback.onSuggestionsReady(request, defaultResponse);
        } else {
            requestDefaultSuggestions(request, callback);
        }
    }
    
    /**
     * The case when the user types letters in the textbox and this method 
     * is called to search (case insensitive) the list of sugestions previously added.
     * This list is build and passed to the callback.
     * 
     */
    @Override
    public void requestSuggestions(Request request, Callback callback) {
        final List<Suggestion> suggestions = searchSuggestions(request.getQuery().toLowerCase(), request.getLimit());
        Response response = new Response(suggestions);
        callback.onSuggestionsReady(request, response);
    }

    /**
     * Searches the suggestion for the user typed query. It is case insensitive and search for the 
     * query in any part of the suggestion string.
     */
    private List searchSuggestions(String query, int limit) {
        List<Suggestion> matches = new ArrayList<>();
        for (int i = 0; i < items.size() && matches.size() < limit; i++) {
            Suggestion suggestion = items.get(i);
            String display = suggestion.getDisplayString();
            String value = suggestion.getReplacementString();
            if (display.toLowerCase().contains(query)) {
                matches.add(new MultiWordSuggestOracle.MultiWordSuggestion(value, display));
            }
        }
        return matches;
    }

    public void add(Suggestion suggestion) {
        items.add(suggestion);
    }

    public void addAll(Collection<Suggestion> collection) {
        items.addAll(collection);
    }

    public void clear() {
        items.clear();
    }
}
