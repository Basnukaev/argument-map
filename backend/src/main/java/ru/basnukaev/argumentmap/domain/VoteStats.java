package ru.basnukaev.argumentmap.domain;

/**
 * Агрегированная статистика голосов за тему (community-сигнал популярности,
 * ADR-053). Голоса переехали с узлов на темы - см. {@link TopicVote}.
 *
 * <ul>
 *   <li>upvotes - количество голосов с weight=+1
 *   <li>downvotes - количество голосов с weight=-1
 *   <li>score - upvotes - downvotes (нетто, может быть отрицательным)
 * </ul>
 */
public record VoteStats(int upvotes, int downvotes, int score) {

    public static final VoteStats EMPTY = new VoteStats(0, 0, 0);

    public static VoteStats of(int upvotes, int downvotes) {
        return new VoteStats(upvotes, downvotes, upvotes - downvotes);
    }
}
