package adapters.display_data.story_data;

/**
 * Returns a string of all the author names separated by commas
 */
public class AuthorNameStringCreatorCommas implements AuthorNameStringCreator {

    @Override
    public String createAuthorString(String[] authors) {
        StringBuilder s = new StringBuilder();

        for (String author: authors) {
            s.append(author).append(", ");
        }
        s.deleteCharAt(s.length());

        return s.toString();
    }
}
