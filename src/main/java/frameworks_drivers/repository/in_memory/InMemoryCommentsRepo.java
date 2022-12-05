package frameworks_drivers.repository.in_memory;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import usecases.CommentRepoData;
import usecases.RepoRes;
import usecases.Response;
import usecases.comment_as_guest.CagGatewayComments;
import usecases.get_story_comments.GscGatewayComments;

import java.util.ArrayList;
import java.util.List;

/**
 * In memory implementation of the database component in charge of storing
 * comments associated with stories
 */
public class InMemoryCommentsRepo implements GscGatewayComments, CagGatewayComments {

    /**
     * DB table row for storing a single comment entry
     */
    private static class CommentsTableRow {

        private static int nextAvailableId = 0;
        private final int commentId;
        private final int storyId;
        private final String displayName;
        private String comment;

        public CommentsTableRow (int storyId, @NotNull String displayName, @NotNull String comment) {
            commentId = nextAvailableId;
            nextAvailableId++;

            this.storyId = storyId;
            this.displayName = displayName;
            this.comment = comment;
        }

        public String getDisplayName() { return displayName; }
        public int getCommentId() { return commentId; }
        public String getComment() { return comment; }
        public int getStoryId() { return storyId; }
    }

    private final List<CommentsTableRow> commentsTable;

    /**
     * Initialize comments table
     */
    public InMemoryCommentsRepo () { commentsTable = new ArrayList<>(); }

    /**
     *
     * @param storyId unique primary key ID of story to which to save a comment
     * @param displayName string guest display name, not null
     * @param comment string comment content, no
     * @return if the comment was added successfully
     */
    @Override
    @NotNull
    public Response commentAsGuest (int storyId, @NotNull String displayName, @NotNull String comment) {
        commentsTable.add(new CommentsTableRow(storyId, displayName, comment));
        return Response.getSuccessful("Comment successfully added to Story ID" + storyId);
    }

    /**
     *
     * @param storyId unique primary key ID of story for which to retrieve all comments
     * @return all comments for the requested story, or null if some failure occurs
     */
    @Override
    @NotNull
    public RepoRes<CommentRepoData> getAllComments (int storyId) {
        RepoRes<CommentRepoData> res = new RepoRes<>();

        // Convert to CommentRepoData objects
        for (CommentsTableRow row : commentsTable) {
            res.addRow(new CommentRepoData(
                    row.getCommentId(), row.getStoryId(),
                    row.getDisplayName(), row.getComment()
            ));
        }

        res.setResponse(Response.getSuccessful("Successfully retrieved comments for story " + storyId));

        return res;
    }
}
