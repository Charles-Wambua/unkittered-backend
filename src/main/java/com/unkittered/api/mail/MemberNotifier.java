package com.unkittered.api.mail;

import com.unkittered.api.mail.MailService.Action;
import com.unkittered.api.push.PushService;
import com.unkittered.api.user.User;
import com.unkittered.api.user.UserRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Tells members about decisions made about their account: suspensions,
 * verification reviews and reports we acted on. Push for everything; email too
 * where the member may not be able to open the app (suspension) or may want a
 * record. Sends are asynchronous and never fail the admin action.
 */
@Service
public class MemberNotifier {

    /** FCM data {@code type}: account notices (not covered by notification preferences). */
    static final String ACCOUNT = "account";
    /** FCM data {@code type}: verification results; the app opens the verification screen. */
    static final String VERIFICATION = "verification";

    private final PushService push;
    private final MailService mail;
    private final UserRepository users;
    private final JdbcTemplate jdbc;

    public MemberNotifier(PushService push, MailService mail, UserRepository users, JdbcTemplate jdbc) {
        this.push = push;
        this.mail = mail;
        this.users = users;
        this.jdbc = jdbc;
    }

    public void suspended(UUID userId) {
        push.sendToUser(userId, "Your account has been suspended",
                "After a review we've suspended your account for breaking our Community Guidelines. "
                        + "Email us if you think this is a mistake.",
                Map.of("type", ACCOUNT));
        email(userId, "Your Unkittered account has been suspended", "Your account is suspended", List.of(
                "After reviewing reports about your account, we've suspended it because it broke our "
                        + "Community Guidelines or Terms of Service. While it's suspended you can't sign in, "
                        + "and other members can't see your profile.",
                "If you think we got this wrong, reply to this email and a person on our team will look at it "
                        + "again. Please include anything that helps us understand what happened."),
                new Action("Read the guidelines", mail.siteUrl() + "/guidelines"));
    }

    public void reinstated(UUID userId) {
        push.sendToUser(userId, "Welcome back to Unkittered",
                "We've reviewed your account and lifted the suspension. You can sign in again.",
                Map.of("type", ACCOUNT));
        email(userId, "Your Unkittered account is active again", "Welcome back", List.of(
                "We've reviewed your account and lifted the suspension. You can sign in and pick up where you "
                        + "left off.",
                "Thanks for your patience, and for helping keep Unkittered kind and safe."), null);
    }

    public void selfieReviewed(UUID userId, boolean approved) {
        if (approved) {
            push.sendToUser(userId, "You're verified ✓",
                    "Your selfie matched your photos. The Verified badge is now on your profile.",
                    Map.of("type", VERIFICATION));
        } else {
            push.sendToUser(userId, "We couldn't verify your selfie",
                    "It didn't clearly match your profile photos. Try again in good light, facing the camera.",
                    Map.of("type", VERIFICATION));
        }
    }

    public void sterilisationReviewed(UUID userId, boolean approved) {
        if (approved) {
            push.sendToUser(userId, "Sterilisation verified ✓",
                    "We checked your document. The \"Sterilised · verified\" badge is now on your profile, "
                            + "and the document has been deleted.",
                    Map.of("type", VERIFICATION));
        } else {
            push.sendToUser(userId, "We couldn't verify your document",
                    "It wasn't clear enough to confirm the procedure. You can upload a clearer one from "
                            + "Settings → Verification. We've deleted the one you sent.",
                    Map.of("type", VERIFICATION));
        }
    }

    /** Thanks everyone who reported {@code reportedId}, without naming the outcome or the person. */
    public void reportersThanked(UUID reportedId) {
        List<UUID> reporters = jdbc.queryForList(
                "SELECT DISTINCT reporter_id FROM reports WHERE reported_id = ?", UUID.class, reportedId);
        if (reporters.isEmpty()) return;
        push.sendToUsers(reporters, "Thanks for your report",
                "We've reviewed it and taken action. Reports like yours keep Unkittered safe.",
                Map.of("type", ACCOUNT));
    }

    /** Same for a pack: call before deleting it, as its reports go with it. */
    public void packReportersThanked(UUID meetupId) {
        List<UUID> reporters = jdbc.queryForList(
                "SELECT DISTINCT reporter_id FROM meetup_reports WHERE meetup_id = ?", UUID.class, meetupId);
        if (reporters.isEmpty()) return;
        push.sendToUsers(reporters, "Thanks for your report",
                "We've reviewed the pack you reported and removed it.",
                Map.of("type", ACCOUNT));
    }

    private void email(UUID userId, String subject, String heading, List<String> paragraphs, Action action) {
        users.findById(userId).map(User::getEmail).ifPresent(to ->
                mail.sendAsync(to, subject, heading, paragraphs, action));
    }
}
