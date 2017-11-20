package io.github.jeqo.demo.infra;

import io.github.jeqo.demo.domain.FindAllTweets;
import io.github.jeqo.demo.domain.Tweet;
import io.github.jeqo.demo.domain.TweetsRepository;
import io.github.jeqo.demo.domain.User;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.Result;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;

import java.math.BigInteger;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static io.github.jeqo.demo.infra.jooq.Tables.HASHTAGS;
import static io.github.jeqo.demo.infra.jooq.tables.Tweets.TWEETS;
import static io.github.jeqo.demo.infra.jooq.tables.Users.USERS;

/**
 *
 */
public class JooqPostgresTweetsRepository implements TweetsRepository {

  private final String jdbcUrl;
  private final String jdbcUsername;
  private final String jdbcPassword;

  public JooqPostgresTweetsRepository(String jdbcUrl, String jdbcUsername, String jdbcPassword) {
    this.jdbcUrl = jdbcUrl;
    this.jdbcUsername = jdbcUsername;
    this.jdbcPassword = jdbcPassword;
  }


  @Override
  public void put(Tweet tweet) {
    try (Connection connection = DriverManager.getConnection(jdbcUrl, jdbcUsername, jdbcPassword)) {
      final User user = tweet.user();
      final DSLContext dslContext = DSL.using(connection, SQLDialect.POSTGRES);
      final BigInteger userId = BigInteger.valueOf(user.id());
      dslContext
          .insertInto(USERS)
          .columns(USERS.ID, USERS.NAME, USERS.SCREEN_NAME, USERS.LOCATION, USERS.VERIFIED)
          .values(userId, user.name(), user.screenName(), user.location(), user.verified())
          .onDuplicateKeyUpdate()
          .set(USERS.NAME, user.name())
          .set(USERS.LOCATION, user.location())
          .execute();

      final BigInteger tweetId = BigInteger.valueOf(tweet.id());
      dslContext
          .insertInto(TWEETS)
          .columns(TWEETS.ID, TWEETS.CREATED_AT, TWEETS.USER_ID, TWEETS.TEXT, TWEETS.IS_RETWEET)
          .values(tweetId, tweet.createdAt(), userId, tweet.text(), tweet.isRetweet())
          .execute();

      tweet.hashtagSet()
          .forEach(hashtag ->
              dslContext
                  .insertInto(HASHTAGS)
                  .columns(HASHTAGS.TEXT, HASHTAGS.TWEET_ID)
                  .values(hashtag.text(), tweetId)
                  .execute());
    } catch (SQLException e) {
      e.printStackTrace();
    }
  }

  @Override
  public List<Tweet> find(FindAllTweets query) {
    try (Connection connection = DriverManager.getConnection(jdbcUrl, jdbcUsername, jdbcPassword)) {
      final DSLContext dslContext = DSL.using(connection, SQLDialect.POSTGRES);
      final Result<Record> records =
          dslContext.select()
              .from(TWEETS)
              .join(USERS)
              .on(TWEETS.USER_ID.eq(USERS.ID))
              .fetch();
      return
          records.stream()
              .map(Tweet::buildFromRecord)
              .peek(tweet -> {
                final BigInteger tweetId = BigInteger.valueOf(tweet.id());
                dslContext.select()
                    .from(HASHTAGS)
                    .where(HASHTAGS.TWEET_ID.eq(tweetId))
                    .fetch()
                    .stream()
                    .map(Tweet.Hashtag::buildFromRecord)
                    .forEach(tweet::addHashtag);
              })
              .collect(Collectors.toList());
    } catch (SQLException e) {
      e.printStackTrace();
      return new ArrayList<>();
    }
  }

}