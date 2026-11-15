package nl.loc.backend.user.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class CommentTest {
    private static final Instant NOW = Instant.parse("2030-06-07T16:00:00Z");
    private static final UUID EVENT_ID = UUID.randomUUID();
    private static final UUID ALICE = UUID.randomUUID();
    private static final UUID BOB = UUID.randomUUID();

    @Test
    void topLevelCommentIsTrimmedAndNotAReply() {
        Comment comment = Comment.topLevel(EVENT_ID, ALICE, "  Is there parking?  ", NOW);

        assertThat(comment.getBody()).isEqualTo("Is there parking?");
        assertThat(comment.isReply()).isFalse();
        assertThat(comment.isOfficial()).isFalse();
        assertThat(comment.getEditedAt()).isNull();
    }

    @Test
    void replyBelongsToTheSameEventAsItsParent() {
        Comment reply = saved(Comment.topLevel(EVENT_ID, ALICE, "Is there parking?", NOW)).reply(BOB, "Yes", NOW);

        assertThat(reply.getEventId()).isEqualTo(EVENT_ID);
        assertThat(reply.getUserId()).isEqualTo(BOB);
        assertThat(reply.isReply()).isTrue();
    }

    @Test
    void refusesToReplyToAReply() {
        Comment reply = saved(Comment.topLevel(EVENT_ID, ALICE, "Is there parking?", NOW)).reply(BOB, "Yes", NOW);

        assertThatThrownBy(() -> reply.reply(ALICE, "Thanks", NOW)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void editReplacesTheTextAndMarksItEdited() {
        Comment comment = Comment.topLevel(EVENT_ID, ALICE, "Is there parking?", NOW);

        comment.edit(" Is there bike parking? ", NOW.plusSeconds(60));

        assertThat(comment.getBody()).isEqualTo("Is there bike parking?");
        assertThat(comment.getEditedAt()).isEqualTo(NOW.plusSeconds(60));
    }

    @Test
    void refusesToEditADeletedComment() {
        Comment comment = Comment.topLevel(EVENT_ID, ALICE, "Is there parking?", NOW);
        comment.delete(ALICE, null, NOW);

        assertThatThrownBy(() -> comment.edit("New text", NOW)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void deletingTwiceKeepsTheFirstDeletion() {
        Comment comment = Comment.topLevel(EVENT_ID, ALICE, "Is there parking?", NOW);

        comment.delete(ALICE, null, NOW);
        comment.delete(BOB, "spam", NOW.plusSeconds(60));

        assertThat(comment.isDeleted()).isTrue();
        assertThat(comment.getDeletedAt()).isEqualTo(NOW);
        assertThat(comment.getDeletedBy()).isEqualTo(ALICE);
        assertThat(comment.getDeleteReason()).isNull();
    }

    // replies are only made from comments loaded from the database, which always have an id
    private static Comment saved(Comment comment) {
        ReflectionTestUtils.setField(comment, "id", UUID.randomUUID());
        return comment;
    }
}
